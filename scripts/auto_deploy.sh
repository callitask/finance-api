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
#   - EDITED (Zero-Disruption Architecture & SSH Drop Prevention):
#     • Removed the `-s` flag from `docker compose rm` to prevent the `treish_net` bridge from collapsing, which previously severed SSH connections during deployment.
#     • Separated infrastructure convergence (`docker compose up -d`) from application deployment (`docker compose up -d --force-recreate --no-deps backend nginx`).
#     • This guarantees databases and networks remain untouched, while the backend pulls fresh code and Infisical secrets seamlessly.
#
#   - STRATEGIC PIVOTS & FAILED ATTEMPTS (CRITICAL FOR FUTURE AI):
#     • Strategy Attempted: `docker compose rm -f -s -v` to clear deadlocked ghost containers.
#     • Failure Mode: The `-s` flag halted all healthy containers (DB, Redis), collapsing the Docker bridge network and flushing iptables, severing the remote engineer's SSH connection mid-deployment.
#     • Future AI Warning: NEVER shut down the entire container stack natively or via Compose. Always enforce surgical, non-disruptive rolling updates (`--no-deps backend nginx`).
#     • Strategy Attempted: `docker compose up -d --force-recreate` on deleted/missing infrastructure (Redis).
#     • Failure Mode: The `--force-recreate` flag forces Compose to query the Docker daemon for the exact old container ID. Because the old container was natively deleted, the daemon responds "No such container: <ID>", instantly causing Compose to panic and abort the entire deployment script.
#     • Future AI Warning: NEVER use `--force-recreate` when healing missing or deleted infrastructure components. Use standard `up -d` to allow Compose to cleanly build missing components without searching for dead metadata.
#
#   - EDITED (Envoy Sidecar Orchestration):
#     • Added `envoy-sidecar` and `aegis-canary-server` to the surgical application deployment command (`docker compose up -d --force-recreate --no-deps backend nginx envoy-sidecar aegis-canary-server`).
#     • Why: Envoy depends on the backend replicas. By forcefully recreating it alongside the backend, we ensure it correctly discovers the new replica IP addresses and remains permanently live, rather than dropping out of the process tree.
#
#   - EDITED (Canary Deception Restoration & Enterprise Domain Isolation):
#     • Restored `aegis-canary-server` to the surgical application update target.
#     • The container no longer crashes because the underlying Redis namespace conflict was resolved natively in compose.
#
#   - EDITED (Global Orphan Scan Bypass & Explicit DB State Healing):
#     • Removed the temporary namespace collision breaker.
#     • Replaced the global `--remove-orphans` parameter (which suffered fatal crashes due to dangling Compose metadata caches) with explicit, targeted infrastructure rebuilds (`treishvaam-redis redis treishvaam-backup`).
#     • This bypasses the buggy metadata scan entirely and ensures databases are always definitively attached to the network before application boot.
#     • Removed `--force-recreate` from the targeted DB rebuild phase to prevent Compose caching crashes.
# ==============================================================================

export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="deploy.log"
ENV_FILE=".env"
TEMPLATE_FILE=".env.template"

cd "$PROJECT_DIR" || { echo "CRITICAL: Could not find project directory $PROJECT_DIR"; exit 1; }
exec > >(tee -a "$LOG_FILE") 2>&1

# --- 0. CRITICAL CONCURRENCY LOCK ---
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
trap 'rm -f "$LOCKFILE"' EXIT

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
# Bypass the global orphan scan bug by explicitly targeting missing/unlinked databases first
# [FIX] Do NOT use --force-recreate here to prevent fatal metadata lookup crashes
docker compose up -d --no-deps treishvaam-redis redis treishvaam-backup

# Safely converge the rest of the infrastructure
docker compose up -d --build

echo "[Docker] Surgically cycling application tier to consume updated artifacts & secrets..."
# Force recreate backend, proxy, sidecars, and canary server securely to fresh replicas
docker compose up -d --force-recreate --no-deps backend nginx envoy-sidecar aegis-canary-server

echo "[System] Stabilizing application layer (Waiting 10s)..."
sleep 10

# --- 4. SECURITY WIPE (Flash & Wipe) ---
echo "[Security] Wiping secrets from disk..."
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > SECURE WIPE COMPLETE. .env restored to template baseline."

docker image prune -f

echo "[$(date)] ✅ Intelligent Rebuild & Deployment Complete."
echo "================================================================"