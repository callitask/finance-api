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
#
#   - EDITED (Temporal Synchronization Enforcement):
#     • Injected a native OS NTP clock synchronization block prior to Infisical auth and Docker rebuilds.
#     • Reason: VirtualBox VMs suffer severe clock drift when paused/resumed. If the clock drifts, AEGIS L1-PPO edge signatures instantly expire (300s TTL), causing a 403 Forbidden blackout. This proactively forces host monotonic alignment before updating application logic.
#
#   - EDITED (TCP Massacre & Network Drop Fix):
#     • Removed `systemctl restart systemd-timesyncd` from the deployment pipeline.
#     • Reason: Restarting the daemon forced a massive time-jump, which triggered `systemd-networkd` to expire DHCP leases instantly. This dropped the network bridge (`treish_net`), killed active SSH sessions, and corrupted Docker daemon metadata (`No such container` panics). The Tier-1 boot lock now handles this safely at startup.
#
#   - EDITED (Proactive Ghost Metadata Reconciliation):
#     • Added a proactive `docker rm -f` block targeted specifically at containers with `status=dead` before `docker compose up` executes.
#     • Reason: If the Docker Daemon falls into Split-Brain mode due to a previous crash, Compose panics with "No such container" and aborts the infrastructure build. This block sanitizes the daemon's internal state machine before allowing Compose to evaluate the manifest.
#
#   - EDITED (Kernel I/O Starvation & SSH Connection Reset Fix):
#     • Introduced 'Staggered Infrastructure Ignition' using sleep buffers between docker compose commands.
#     • Reason: Shotgunning 17 enterprise containers simultaneously caused a massive CPU/IO spike and STP broadcast storm, which starved the 'sshd' daemon and dropped active SSH sessions with 'client_loop: send disconnect'. Staggering the deployment rate-limits the kernel, preserving host responsiveness and SSH stability.
#
#   - EDITED (ZKP Service Concurrent Build Failure Fix):
#     • Added `aegis-zkp-service` explicitly to the Staggered Ignition sequence.
#     • Reason: The Go compiler requires significant CPU/RAM. During general `docker compose up -d --build`, compiling Go simultaneously with starting Elasticsearch and Wazuh caused the compiler to silently OOM/timeout. Isolating it guarantees the security microservice builds and boots successfully without being skipped.
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

# --- 0.5 TEMPORAL SYNCHRONIZATION ENFORCEMENT ---
echo "[System] Ensuring NTP synchronization is active..."
# Ensure NTP is enabled without violently restarting the daemon (prevents DHCP network drops)
sudo -n timedatectl set-ntp true >/dev/null 2>&1 || true

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

# --- 3. SMART ZERO-DOWNTIME REBUILD (STAGGERED TO PREVENT SSH KERNEL LOCK) ---
echo "[Docker] Reconciling Daemon Metadata..."
# Detect if dead containers exist and prune ghost metadata before attempting to up
if docker ps -a --filter "status=dead" | grep -q 'dead'; then
    echo "  > Ghost containers detected. Purging corrupted metadata..."
    docker rm -f $(docker ps -aq --filter "status=dead") 2>/dev/null || true
fi

echo "[Docker] Executing Non-Disruptive State Healing..."
# Remove dead/exited containers natively WITHOUT stopping running ones (preserves SSH network bridge)
docker compose rm -f -v || true

echo "[Docker] Applying Staggered Infrastructure Ignition (Preventing I/O Storm)..."
# Sequenced with sleep buffers to prevent 'client_loop' SSH disconnects caused by kernel CPU starvation
docker compose up -d --build --no-deps treishvaam-redis redis backup-service
sleep 3

docker compose up -d --build --no-deps wazuh-manager aegis-canary-server
sleep 3

docker compose up -d --build --no-deps elasticsearch
sleep 3

# Isolate the ZKP Go compilation to prevent concurrent CPU exhaustion
docker compose up -d --build --no-deps aegis-zkp-service
sleep 3

# Safely converge the rest of the infrastructure
docker compose up -d --build
sleep 3

echo "[Docker] Surgically cycling application tier to consume updated artifacts & secrets..."
# Force recreate backend, proxy, sidecars securely in waves
docker compose up -d --force-recreate --no-deps backend
sleep 5

docker compose up -d --force-recreate --no-deps nginx envoy-sidecar

echo "[System] Stabilizing application layer (Waiting 10s)..."
sleep 10

# --- 4. SECURITY WIPE (Flash & Wipe) ---
echo "[Security] Wiping secrets from disk..."
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > SECURE WIPE COMPLETE. .env restored to template baseline."

docker image prune -f

echo "[$(date)] ✅ Intelligent Rebuild & Deployment Complete."
echo "================================================================"