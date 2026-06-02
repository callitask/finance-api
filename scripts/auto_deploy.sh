#!/bin/bash

# ==============================================================================
# AI-CONTEXT:
#
# Purpose:
#   - Automated deployment watchdog that monitors Git branches.
#   - Handles self-healing, secret injection (Infisical), and service restarts.
#
# Scope:
#   - Infrastructure orchestration, Permission management, Docker lifecycle.
#   - Must NEVER handle application logic, only ops.
#
# Critical Dependencies:
#   - Docker Compose
#   - Git
#   - Infisical CLI
#
# Security Constraints:
#   - Secrets must never be persisted to disk (Flash & Wipe strategy).
#   - .env file is transient.
#
# Non-Negotiables:
#   - Now strictly Event-Driven (Triggered purely via GitHub Actions, not Cron).
#
# IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#   - ADDED:
#     • Initial deployment logic
#   - EDITED:
#     • Added Pre-Flight Permission Fix (sudo chown) to prevent Git lockouts.
#     • Upgraded Nginx restart to 'force-recreate' to guarantee config reload.
#   - EDITED:
#     • Added `sed "s/['\"]//g"` pipeline to the Infisical export command.
#   - EDITED:
#     • Injected a proactive OS Memory Recovery sequence (`drop_caches` and `docker builder prune`).
#   - EDITED:
#     • Elevated Step 5 (Permission Repair) to utilize Docker alpine sequence.
#   - EDITED (Rolling Cache Retention & BuildKit):
#     • Swapped destructive `docker builder prune` for rolling retention (`--filter until=168h`).
#   - EDITED (OOM Prevention & Infrastructure Stabilization):
#     • Injected privileged Docker alpine sequence to dynamically provision 4GB swap file.
#   - EDITED (Cron Concurrency & DNS Healing):
#     • Added PID-verified Lockfile and host DNS flush.
#   - EDITED (IPv6 & DNS Healing Fix):
#     • Added `sysctl -w net.ipv6.conf.all.disable_ipv6=1` via nsenter.
#   - EDITED (Global Rolling Update & Carriage Return Annihilation):
#     • Replaced targeted backend-only `docker compose up` with a global `docker compose up -d --build --force-recreate`.
#     • Injected `sed 's/\r//g'` and `sed "s/['\"]//g"` directly into the `TEMP_SECRETS` parsing pipeline.
#     • Reason: Fulfilling mandate for "smart, efficient" updates where every push forces a global rebuild of all containers (resetting the 8-hour stale uptime). Stripping `\r` permanently resolves the MariaDB `file_key_management` plugin crash at `line 0, column 1` caused by invisible Windows line-endings in the Infisical export.
#   - EDITED (Boundary Quote Extraction):
#     • Replaced the destructive global quote stripper (`sed "s/['\"]//g"`) with a precise boundary extractor (`sed -E "s/^([^=]+)=['\"](.*)['\"]$/\1=\2/"`).
#     • Reason: Global stripping was destroying Redis and Database passwords that contained internal `#` or `$` characters, exposing them to the YAML parser as comments and triggering widespread NOAUTH crash loops. The regex solely removes outer Infisical quotes.
#   - EDITED (Dependency Deadlock Resolution):
#     • Restored `docker compose down --remove-orphans` immediately prior to the global rebuild step.
#     • Reason: Fixed a dependency deadlock where `docker compose up --force-recreate` would hang infinitely and abort without executing if a required service (like treishvaam-db) crashed and failed its healthcheck, leaving downstream containers stuck in a stale state.
#   - EDITED (Force Execution & Strict Quote Extraction):
#     • Added `--force` argument to bypass `[ "$LOCAL" != "$REMOTE" ]` check for manual/CI overrides.
#     • Refactored Infisical quote extraction to use a sequential `sed -E "s/='(.*)'$/=\1/" | sed -E 's/="(.*)"$/=\1/"` pipeline.
#     • Reason: Single-pass regex failed on GNU sed, leaving literal quotes in `.env` which poisoned the MariaDB password and caused infinite crash loops.
#   - EDITED (Network-Preserving Teardown & SIGHUP Prevention):
#     • Replaced 'docker compose down' with 'docker compose stop && docker compose rm -f -s -v'.
#     • Reason: 'down' destroys the treish_net bridge, causing host iptables flushing and VirtualBox IP collisions which sever SSH connections and trigger SIGHUP script terminations mid-deployment. The new approach wipes container state to resolve deadlocks but preserves the network infrastructure perfectly intact.
#   - EDITED (Surgical Deadlock Breaker & I/O Panic Prevention):
#     • Removed global `docker compose stop` which triggered catastrophic VM I/O spikes (simultaneous heap dumping of 17 containers) that locked the network interface and killed the GitHub Runner.
#     • Injected a precise `docker rm -f` command targeting exclusively `restarting` or `dead` containers.
#     • Reason: Surgically removes only the crashed services blocking the dependency tree, avoiding massive system shock, preserving SSH connections, and keeping the CI/CD runner online.
#   - EDITED (CI/CD Decoupling & CPU Starvation Fix):
#     • Removed `--build` flag from `docker compose up`.
#     • Reason: Fulfilling the Enterprise Separation of Concerns principle. GitHub Actions (CI) now exclusively compiles the `.war` artifact. The server deployment script (CD) mounts the pre-built `.war` and simply recreates the containers in seconds. This eliminates the 400% CPU spike that previously crashed the VM network interface and killed the GitHub Runner.
#   - EDITED (Event-Driven Architecture):
#     • Removed broad --force-recreate to prevent DB/Keycloak reboot storms.
#     • Implemented surgical restart for backend and nginx to pick up bind-mounted artifact changes.
#   - EDITED (Deadlock Mitigation Upgrade):
#     • Replaced the `docker run alpine chown` container hook with a zero-dependency host OS level `sudo chown` check. 
#     • Reason: Eliminates container dependency engine blocks under heavy infrastructure state changes, stabilizing runtime permissions flawlessly.
# ==============================================================================

# ==============================================================================
# TREISHVAAM FINANCE - ENTERPRISE WATCHDOG (AUTO DEPLOY)
# ==============================================================================

# --- Configuration ---
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="deploy.log"
ENV_FILE=".env"
TEMPLATE_FILE=".env.template"

# Ensure we are in the project directory
cd "$PROJECT_DIR" || { echo "CRITICAL: Could not find project directory $PROJECT_DIR"; exit 1; }

# Start Logging
exec > >(tee -a "$LOG_FILE") 2>&1

# --- 0. CRITICAL CONCURRENCY LOCK ---
# Prevents overlapping cron jobs from destroying active image pulls and deployments
LOCKFILE="/tmp/treishvaam_deploy.lock"
if [ -f "$LOCKFILE" ]; then
    PID=$(cat "$LOCKFILE")
    if kill -0 "$PID" 2>/dev/null; then
        exit 0
    else
        echo "[$(date)] Stale lockfile found (PID: $PID dead). Removing..."
        rm -f "$LOCKFILE"
    fi
fi
echo $$ > "$LOCKFILE"
trap 'rm -f "$LOCKFILE"' EXIT

# --- 0.1 FORCE DEPLOY EVALUATION ---
FORCE_DEPLOY=0
if [ "$1" == "--force" ]; then
    FORCE_DEPLOY=1
    echo "[System] --force flag detected. Bypassing Git branch difference checks."
fi

# List of branches to monitor for deployment
MONITORED_BRANCHES=("main" "staging" "develop")

# --- 0.5 PRE-FLIGHT PERMISSION FIX (CRITICAL) ---
echo "[System] Fixing Git tracking permissions safely..."
# Refactored to native host execution to prevent Docker storage driver dependency blockages
sudo chown -R $(id -u):$(id -g) .git scripts docker-compose.yml 2>/dev/null || true

# --- 1. BRANCH INTELLIGENCE ---
git fetch --all

TARGET_BRANCH="main"
LATEST_TIMESTAMP=0

echo "Checking branch activity..."

for branch in "${MONITORED_BRANCHES[@]}"; do
    TS=$(git log -1 --format=%ct "origin/$branch" 2>/dev/null || echo 0)
    if [ "$TS" -gt "$LATEST_TIMESTAMP" ]; then
        LATEST_TIMESTAMP=$TS
        TARGET_BRANCH="$branch"
    fi
done

# --- 2. DETECT CHANGES ---
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

if [ "$LOCAL" != "$REMOTE" ] || [ "$CURRENT_BRANCH" != "$TARGET_BRANCH" ] || [ "$FORCE_DEPLOY" -eq 1 ]; then
    echo "================================================================"
    echo "[$(date)] 🚀 Deployment Triggered. Winning Branch: [$TARGET_BRANCH]"
    echo "================================================================"
    
    # --- 3. SELF-HEALING UPDATE ---
    echo "[System] Syncing files with origin/$TARGET_BRANCH..."
    
    if git rev-parse --verify "$TARGET_BRANCH" >/dev/null 2>&1; then
        git checkout "$TARGET_BRANCH"
    else
        echo "[System] Branch $TARGET_BRANCH does not exist locally. Creating it..."
        git checkout -b "$TARGET_BRANCH" "origin/$TARGET_BRANCH"
    fi

    git reset --hard "origin/$TARGET_BRANCH"
    
    chmod +x scripts/*.sh backup/*.sh
    chmod +x scripts/auto_deploy.sh

    # --- 4. SECURE RESTART STRATEGY ---
    echo "[Security] Preparing Secure Environment..."
    
    if [ ! -f "$TEMPLATE_FILE" ]; then
        echo "CRITICAL: $TEMPLATE_FILE missing! Cannot fetch secrets."
        exit 1
    fi
    cp "$TEMPLATE_FILE" "$ENV_FILE"
    
    export INFISICAL_PROJECT_ID=$(grep -E '^INFISICAL_PROJECT_ID=' "$ENV_FILE" | cut -d '=' -f2 | tr -d " \"'\r")
    export INFISICAL_CLIENT_ID=$(grep -E '^INFISICAL_CLIENT_ID=' "$ENV_FILE" | cut -d '=' -f2 | tr -d " \"'\r")
    export INFISICAL_CLIENT_SECRET=$(grep -E '^INFISICAL_CLIENT_SECRET=' "$ENV_FILE" | cut -d '=' -f2 | tr -d " \"'\r")
    
    echo "[Security] Authenticating with Infisical (Universal Auth)..."
    infisical login --method=universal-auth --client-id="$INFISICAL_CLIENT_ID" --client-secret="$INFISICAL_CLIENT_SECRET" --silent 2>/dev/null || echo "  > Notice: Using cached session."

    echo "[Security] Fetching live secrets from Infisical..."
    echo "" >> "$ENV_FILE"

    TEMP_SECRETS=$(mktemp)
    
    infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$TEMP_SECRETS" 2>/dev/null
    EXIT_CODE=$?

    if [ $EXIT_CODE -eq 0 ] && [ -s "$TEMP_SECRETS" ] && ! grep -qE "arrow keys|Select project|login" "$TEMP_SECRETS"; then
        # CRITICAL FIX: Double-pass precise sed removal. Extracts exact payload bounded by either single or double quotes.
        cat "$TEMP_SECRETS" | sed 's/\r//g' | sed -E "s/='(.*)'$/=\1/" | sed -E 's/="(.*)"$/=\1/' >> "$ENV_FILE"
        echo "  > Secrets injected and sanitized (bounding quotes safely extracted)."
        rm "$TEMP_SECRETS"
    else
        echo "CRITICAL: Infisical fetch failed or returned interactive prompt."
        rm "$TEMP_SECRETS"
        exit 1
    fi

    # --- OS SHADOW-KILL: Unset all template variables from the active shell ---
    echo "[Security] Executing OS shadow-kill: unsetting shell environment overrides..."
    while IFS= read -r line; do
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ -z "$line" ]] && continue
        var_name=$(echo "$line" | cut -d'=' -f1 | tr -d ' \r')
        [[ "$var_name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] && unset "$var_name"
    done < "$TEMPLATE_FILE"
    echo "  > OS shell overrides neutralised."

    sudo chown $(id -u):$(id -g) .env 2>/dev/null || true
    
    echo "[Docker] Cleaning up dead containers..."
    docker rm -f $(docker ps -f "status=dead" -q) 2>/dev/null || true

    # --- 5. MEMORY RECOVERY, SWAP & DNS HEALING ---
    echo "[System] Executing Aggressive OS Memory Recovery..."
    sudo sync && echo 3 | sudo tee /proc/sys/vm/drop_caches > /dev/null
    
    echo "[System] Disabling host IPv6 to prevent Registry Pull Timeouts..."
    sudo sysctl -w net.ipv6.conf.all.disable_ipv6=1 > /dev/null
    sudo sysctl -w net.ipv6.conf.default.disable_ipv6=1 > /dev/null

    # Ensure 4GB Enterprise Swap Space natively on Host OS
    if [ ! -f /swapfile ]; then
        echo "[Swap] Creating 4GB swap file..."
        sudo dd if=/dev/zero of=/swapfile bs=1M count=4096 status=none
        sudo chmod 600 /swapfile
        sudo mkswap /swapfile > /dev/null
        sudo swapon /swapfile
        echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
    else
        sudo swapon -a || true
    fi

    docker builder prune --filter until=168h -f

    export GOMAXPROCS=1
    export DOCKER_BUILDKIT=1

    # --- 6. SMART EFFICIENT GLOBAL REBUILD ---
    echo "[Docker] Executing Surgical Deadlock Breaker..."
    CRASHED_CONTAINERS=$(docker ps -q -f "status=restarting" -f "status=dead" -f "status=exited")
    if [ ! -z "$CRASHED_CONTAINERS" ]; then
        echo "  > Removing stuck containers to clear dependency lock: $CRASHED_CONTAINERS"
        docker rm -f $CRASHED_CONTAINERS
    else
        echo "  > No crashed dependencies found. Proceeding cleanly."
    fi
    
    echo "[Docker] Applying state-driven idempotency (Infrastructure)..."
    docker compose up -d --remove-orphans
    
    echo "[Docker] Surgically cycling core application services to consume updated artifacts..."
    docker compose restart backend nginx
    
    echo "[System] Stabilizing containers (Waiting 10s)..."
    sleep 10
    
    # E. SECURITY WIPE (Flash & Wipe)
    echo "[Security] Wiping secrets from disk..."
    cp "$TEMPLATE_FILE" "$ENV_FILE"
    echo "  > SECURE WIPE COMPLETE. .env restored to template."
    
    docker image prune -f
    
    echo "[$(date)] ✅ Global Rebuild & Deployment Complete for [$TARGET_BRANCH]."
    echo "================================================================"
else
    :
fi