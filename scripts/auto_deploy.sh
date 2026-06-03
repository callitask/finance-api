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
#   - EDITED (Boundary Quote Extraction):
#     • Replaced the destructive global quote stripper (`sed "s/['\"]//g"`) with a precise boundary extractor (`sed -E "s/^([^=]+)=['\"](.*)['\"]$/\1=\2/"`).
#   - EDITED (Dependency Deadlock Resolution):
#     • Restored `docker compose down --remove-orphans` immediately prior to the global rebuild step.
#   - EDITED (Force Execution & Strict Quote Extraction):
#     • Added `--force` argument to bypass `[ "$LOCAL" != "$REMOTE" ]` check for manual/CI overrides.
#     • Refactored Infisical quote extraction to use a sequential `sed -E "s/='(.*)'$/=\1/" | sed -E 's/="(.*)"$/=\1/"` pipeline.
#   - EDITED (Network-Preserving Teardown & SIGHUP Prevention):
#     • Replaced 'docker compose down' with 'docker compose stop && docker compose rm -f -s -v'.
#   - EDITED (Surgical Deadlock Breaker & I/O Panic Prevention):
#     • Removed global `docker compose stop` which triggered catastrophic VM I/O spikes.
#     • Injected a precise `docker rm -f` command targeting exclusively `restarting` or `dead` containers.
#   - EDITED (CI/CD Decoupling & CPU Starvation Fix):
#     • Removed `--build` flag from `docker compose up`.
#   - EDITED (Event-Driven Architecture):
#     • Removed broad --force-recreate to prevent DB/Keycloak reboot storms.
#     • Implemented surgical restart for backend and nginx to pick up bind-mounted artifact changes.
#   - EDITED (Deadlock Mitigation Upgrade):
#     • Replaced the `docker run alpine chown` container hook with a zero-dependency host OS level `sudo chown` check. 
#   - EDITED (Smart Auxiliary Compilation):
#     • Re-introduced `--build` flag to `docker compose up -d`.
#   - EDITED (Surgical Deadlock Breaker Enhancement):
#     • Injected `-f "status=created"` into the CRASHED_CONTAINERS query.
#   - EDITED (Non-Interactive Sudo Safety):
#     • Injected `-n` (non-interactive) flag into all `sudo` calls.
#   - EDITED (Absolute CI/CD Decoupling):
#     • Completely eradicated legacy Git operations (`fetch`, `checkout`, `reset`). 
#     • Reason: GitHub Actions already delivers the artifact payloads natively. Background Git fetch operations were hanging indefinitely waiting for headless authentication, blocking the Infisical secret injection.
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

echo "================================================================"
echo "[$(date)] 🚀 Deployment Triggered via CI/CD Orchestrator."
echo "================================================================"

chmod +x scripts/*.sh backup/*.sh 2>/dev/null || true
chmod +x scripts/auto_deploy.sh 2>/dev/null || true

# --- 1. SECURE RESTART STRATEGY (INFISICAL INJECTION) ---
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

sudo -n chown $(id -u):$(id -g) .env 2>/dev/null || true

echo "[Docker] Cleaning up dead containers..."
docker rm -f $(docker ps -f "status=dead" -q) 2>/dev/null || true

# --- 2. MEMORY RECOVERY, SWAP & DNS HEALING ---
echo "[System] Executing Aggressive OS Memory Recovery..."
sudo -n sync && echo 3 | sudo -n tee /proc/sys/vm/drop_caches > /dev/null

echo "[System] Disabling host IPv6 to prevent Registry Pull Timeouts..."
sudo -n sysctl -w net.ipv6.conf.all.disable_ipv6=1 > /dev/null
sudo -n sysctl -w net.ipv6.conf.default.disable_ipv6=1 > /dev/null

# Ensure 4GB Enterprise Swap Space natively on Host OS
if [ ! -f /swapfile ]; then
    echo "[Swap] Creating 4GB swap file..."
    sudo -n dd if=/dev/zero of=/swapfile bs=1M count=4096 status=none
    sudo -n chmod 600 /swapfile
    sudo -n mkswap /swapfile > /dev/null
    sudo -n swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo -n tee -a /etc/fstab > /dev/null
else
    sudo -n swapon -a || true
fi

docker builder prune --filter until=168h -f

export GOMAXPROCS=1
export DOCKER_BUILDKIT=1

# --- 3. SMART EFFICIENT GLOBAL REBUILD ---
echo "[Docker] Executing Surgical Deadlock Breaker..."
CRASHED_CONTAINERS=$(docker ps -q -f "status=restarting" -f "status=dead" -f "status=exited" -f "status=created")
if [ ! -z "$CRASHED_CONTAINERS" ]; then
    echo "  > Removing stuck containers to clear dependency lock: $CRASHED_CONTAINERS"
    docker rm -f $CRASHED_CONTAINERS
else
    echo "  > No crashed dependencies found. Proceeding cleanly."
fi

echo "[Docker] Applying state-driven idempotency (Infrastructure)..."
docker compose up -d --build --remove-orphans

echo "[Docker] Surgically cycling core application services to consume updated artifacts..."
docker compose restart backend nginx

echo "[System] Stabilizing containers (Waiting 10s)..."
sleep 10

# --- 4. SECURITY WIPE (Flash & Wipe) ---
echo "[Security] Wiping secrets from disk..."
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > SECURE WIPE COMPLETE. .env restored to template."

docker image prune -f

echo "[$(date)] ✅ Global Rebuild & Deployment Complete."
echo "================================================================"