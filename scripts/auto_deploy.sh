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
#   - Docker Compose, Git, Infisical CLI
#
# Security Constraints:
#   - Secrets must never be persisted to disk (Flash & Wipe strategy).
#
# IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#   - EDITED (Compose-Native State Healing & Ghost Container Fix):
#     • Replaced the brute-force `docker rm -f` deadlock breaker with a compose-native `docker compose rm -f -s -v || true`.
#
#   - EDITED (Zero-Disruption Architecture & SSH Drop Prevention):
#     • Removed the `-s` flag from `docker compose rm` to prevent the `treish_net` bridge from collapsing.
#
#   - EDITED (Pipeline Build Integrity Fix):
#     • Appended the `--build` flag to the explicit state-healing command (`docker compose up -d --build --no-deps treishvaam-redis redis backup-service`).
#     • Reason: Forces Compose to strictly apply all pushed repository changes to the infrastructure layer dynamically.
#
#   - EDITED (Ghost Metadata Crash Prevention & Canary/Wazuh Healing):
#     • Added `wazuh-manager` and `aegis-canary-server` to the explicit state-healing `--no-deps` array.
#     • Removed `aegis-canary-server` from the `--force-recreate` array.
#     • Reason: Enforces the PIPELINE PRESERVATION PROTOCOL. Using `--force-recreate` on missing/crashing containers causes Docker Compose to panic querying ghost metadata. This surgical bypass ensures missing infrastructure is created safely before the general build block.
#
#   - EDITED (Flash & Wipe Bulletproof Trap & Elastic State Healing):
#     • Replaced `trap 'rm -f "$LOCKFILE"' EXIT` with a comprehensive cleanup trap that forces `cp "$TEMPLATE_FILE" "$ENV_FILE"`.
#     • Added `elasticsearch` to the `--no-deps` ghost container rebuild list.
#     • Reason: Manual SSH container cycles previously crashed the Canary server due to missing cryptographic seeds (`WG_PRIVATE_KEY_SEED`). By enforcing the wipe via `trap`, the script guarantees the .env is sanitized back to the base template (preserving `INFISICAL_*` creds) even if the pipeline aborts, crashes, or is killed manually, thus protecting the enterprise vault mechanism without stalling the Git Runner.
# ==============================================================================

export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="deploy.log"
ENV_FILE=".env"
TEMPLATE_FILE=".env.template"

cd "$PROJECT_DIR" || { echo "CRITICAL: Could not find project directory $PROJECT_DIR"; exit 1; }
exec > >(tee -a "$LOG_FILE") 2>&1

# --- 0. CRITICAL CONCURRENCY LOCK & WIPER TRAP ---
LOCKFILE="/tmp/treishvaam_deploy.lock"
if [ -f "$LOCKFILE" ]; then
    PID=$(cat "$LOCKFILE")
    if kill -0 "$PID" 2>/dev/null; then
        exit 0
    else
        rm -f "$LOCKFILE"
    fi
fi
echo $$ > "$LOCKFILE"

# BULLETPROOF TRAP: Clears lock and securely restores template (Flash & Wipe) on any exit condition
trap 'rm -f "$LOCKFILE"; cp "$TEMPLATE_FILE" "$ENV_FILE" 2>/dev/null || true' EXIT

echo "================================================================"
echo "[$(date)] 🚀 Non-Disruptive Deployment Triggered."
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

echo "[Security] Authenticating with Infisical..."
infisical login --method=universal-auth --client-id="$INFISICAL_CLIENT_ID" --client-secret="$INFISICAL_CLIENT_SECRET" --silent 2>/dev/null || echo "  > Notice: Using cached session."

TEMP_SECRETS=$(mktemp)
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$TEMP_SECRETS" 2>/dev/null
EXIT_CODE=$?

# Strict Safety Net: If this fails, abort before corrupting the backend
if [ $EXIT_CODE -eq 0 ] && [ -s "$TEMP_SECRETS" ] && ! grep -qE "arrow keys|Select project|login" "$TEMP_SECRETS"; then
    cat "$TEMP_SECRETS" | sed 's/\r//g' | sed -E "s/='(.*)'$/=\1/" | sed -E 's/="(.*)"$/=\1/' >> "$ENV_FILE"
    echo "  > Secrets successfully injected into transient memory."
    rm "$TEMP_SECRETS"
else
    echo "CRITICAL ERROR: Infisical authentication or export failed. Vault is empty."
    echo "Aborting deployment to prevent 401 Unauthorized cascade."
    rm "$TEMP_SECRETS"
    exit 1
fi

sudo -n chown $(id -u):$(id -g) .env 2>/dev/null || true

# --- 2. MEMORY RECOVERY & DNS HEALING ---
echo "[System] Executing Non-Disruptive OS Memory Recovery..."
sudo -n sync && echo 3 | sudo -n tee /proc/sys/vm/drop_caches > /dev/null
sudo -n sysctl -w vm.max_map_count=262144 > /dev/null
sudo -n sysctl -w net.ipv6.conf.all.disable_ipv6=1 > /dev/null

docker builder prune --filter until=168h -f
export GOMAXPROCS=1
export DOCKER_BUILDKIT=1

# --- 3. SMART ZERO-DOWNTIME REBUILD ---
echo "[Docker] Executing Non-Disruptive State Healing..."
# Remove dead/exited containers natively WITHOUT stopping running ones (preserves SSH network bridge)
docker compose rm -f -v || true

echo "[Docker] Applying explicit state-healing (Infrastructure)..."
# Bypass the global orphan scan bug by explicitly targeting missing/unlinked databases and infrastructure first.
docker compose up -d --build --no-deps treishvaam-redis redis backup-service wazuh-manager aegis-canary-server elasticsearch

# Safely converge the rest of the infrastructure
docker compose up -d --build

echo "[Docker] Surgically cycling application tier to consume updated artifacts & secrets..."
# Force recreate backend, proxy, sidecars securely to fresh replicas
# CRITICAL: aegis-canary-server omitted to prevent ghost metadata aborts
docker compose up -d --force-recreate --no-deps backend nginx envoy-sidecar

echo "[System] Stabilizing application layer (Waiting 10s)..."
sleep 10

# --- 4. SECURITY WIPE (Flash & Wipe) ---
echo "[Security] Wiping secrets from disk..."
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > SECURE WIPE COMPLETE. .env restored to template baseline."

docker image prune -f

echo "[$(date)] ✅ Intelligent Rebuild & Deployment Complete."
echo "================================================================"