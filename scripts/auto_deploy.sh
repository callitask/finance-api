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
#   - Must run every minute via cron.
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

# List of branches to monitor for deployment
MONITORED_BRANCHES=("main" "staging" "develop")

# --- 0.5 PRE-FLIGHT PERMISSION FIX (CRITICAL) ---
echo "[System] Fixing Git tracking permissions safely..."
docker run --rm -v "$(pwd):/workspace" alpine sh -c "chown -R $(id -u):$(id -g) /workspace/.git /workspace/scripts /workspace/docker-compose.yml 2>/dev/null || true"

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

if [ "$LOCAL" != "$REMOTE" ] || [ "$CURRENT_BRANCH" != "$TARGET_BRANCH" ]; then
    echo "================================================================"
    echo "[$(date)] 🚀 New activity detected. Winning Branch: [$TARGET_BRANCH]"
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
        # CRITICAL FIX: Eradicate invisible \r line endings and all quotes before injecting into environment
        cat "$TEMP_SECRETS" | sed 's/\r//g' | sed "s/['\"]//g" >> "$ENV_FILE"
        echo "  > Secrets injected and sanitized (\r and quotes removed)."
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

    docker run --rm -v "$(pwd):/workspace" alpine sh -c "chown $(id -u):$(id -g) /workspace/.env 2>/dev/null || true"
    
    echo "[Docker] Cleaning up dead containers..."
    docker rm -f $(docker ps -f "status=dead" -q) 2>/dev/null || true

    # --- 5. MEMORY RECOVERY, SWAP & DNS HEALING ---
    echo "[System] Executing Aggressive OS Memory Recovery..."
    docker run --rm --privileged alpine sh -c "sync && echo 3 > /proc/sys/vm/drop_caches"
    
    echo "[System] Disabling host IPv6 to prevent Registry Pull Timeouts..."
    docker run --rm --privileged --pid=host alpine nsenter -t 1 -m -u -n -i sysctl -w net.ipv6.conf.all.disable_ipv6=1
    docker run --rm --privileged --pid=host alpine nsenter -t 1 -m -u -n -i sysctl -w net.ipv6.conf.default.disable_ipv6=1

    echo "[System] Ensuring 4GB Enterprise Swap Space via host mount..."
    docker run --rm --privileged -v /:/host alpine sh -c "if [ ! -f /host/swapfile ]; then echo '[Swap] Creating 4GB swap file...'; dd if=/dev/zero of=/host/swapfile bs=1M count=4096 status=none && chmod 600 /host/swapfile && mkswap /host/swapfile && chroot /host swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /host/etc/fstab; else echo '[Swap] Active.'; chroot /host swapon -a || true; fi"

    docker builder prune --filter until=168h -f

    export GOMAXPROCS=1
    export DOCKER_BUILDKIT=1

    # --- 6. SMART EFFICIENT GLOBAL REBUILD ---
    echo "[Docker] Executing Global Rebuild & Force-Recreate for ALL containers..."
    docker compose up -d --build --force-recreate
    
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