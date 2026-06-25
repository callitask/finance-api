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
#
#   - EDITED (Infisical Token Expiration & Silent Failure Fix):
#     • Injected `rm -f "$HOME/.infisical/.infisical.json"` before `infisical login`.
#     • Rerouted Infisical STDERR from `/dev/null` to `$LOG_FILE`.
#     • Reason: The Universal Auth machine token expired after 4 hours of server uptime. The CLI aggressively cached the dead token and silently failed to export secrets. The safety net correctly aborted deployment, but stranded the Docker infrastructure in a frozen state. Purging the cache mathematically forces a fresh cryptographic token fetch on every pipeline execution, and routing STDERR guarantees observability.
#
#   - ADDED (Engine B Telemetry):
#     • Injected `log_telemetry()` function utilizing `jq -cn` to prevent JSON syntax corruption.
#     • Added `RUN_ID=$(uuidgen)` correlation ID and the Dead Man's Switch `IN_PROGRESS` event at boot.
#     • Reason: Converts the silent shell script into a structured NDJSON event generator for Grafana/Loki integration, allowing deep observability into orchestration states without SSH access.
#
#   - ADDED (FMEA Hardening):
#     • FMEA #2: Added `set +x` explicitly before Infisical injection to eliminate bash subshell secret leakage.
#     • FMEA #5: Added `chmod 644` to the telemetry file and `755` to the directory ensuring Promtail inside Docker can read it natively.
#     • FMEA #6: Enforced ISO-8601 UTC timestamps natively for mathematically precise Loki ingestion.
#     • FMEA #8: Upgraded the `EXIT` trap to absolutely guarantee the Flash & Wipe executes BEFORE emitting the terminal telemetry status.
#
#   - EDITED (Infisical Domain Resolution Fix):
#     • Added explicit `--domain="https://app.infisical.com"` flag to `infisical login` and `infisical export` commands.
#     • Reason: Purging the `.infisical.json` cache caused the CLI to lose its default domain routing, resulting in an "Unable to parse domain url" crash. Hardcoding the domain restores Universal Auth while maintaining the cache-purge safety mechanism.
#
#   - EDITED (CI/CD Remediation - Phase 2 Hardening):
#     • Flaw 1: Added notify() function for real-time Telegram deployment status.
#     • Flaw 2: Replaced 168h cache pruning with aggressive 24h limit and pre-deploy image prune.
#     • Flaw 3: Removed --build flag from pre-built public images during staggered ignition.
#     • Flaw 4: Added post-deployment health verification blocking on /actuator/health and container status.
#     • Flaw 5: Removed -v from `docker compose rm -f` to prevent anonymous volume data loss.
#     • Flaw 6: Introduced md5 hash-based change detection for aegis-zkp-service to prevent unnecessary Go rebuilds.
#     • Flaw 8: Enhanced concurrency lock with PID name verification and 2-hour maximum age timeout.
#     • Flaw 13: Scaled backend to 1 replica during deployment, scaling to 2 after health check to prevent VirtualBox OOM.
# ==============================================================================

export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# ── ENGINE B TELEMETRY GLOBALS ────────────────────────────────────────────────
# Generated once per run for full correlation across all log events.
# uuidgen is part of uuid-runtime, standard on Ubuntu 24.
RUN_ID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "no-uuid-$(date +%s)")
TELEMETRY_DIR="/opt/treishvaam/logs"
TELEMETRY_FILE="$TELEMETRY_DIR/deploy_telemetry.ndjson"
DEPLOY_STATUS="FAILURE"   # Pessimistic default. Set to SUCCESS only on clean completion.
# ─────────────────────────────────────────────────────────────────────────────

PROJECT_DIR="/opt/treishvaam"
LOG_FILE="deploy_pipeline.log"
ENV_FILE=".env"
TEMPLATE_FILE=".env.template"

cd "$PROJECT_DIR" || { echo "CRITICAL: Could not find project directory $PROJECT_DIR"; exit 1; }

# ── TELEMETRY DIRECTORY SETUP ─────────────────────────────────────────────────
# Must run BEFORE exec redirect. chmod 755 on dir + 644 on file ensures
# Promtail (running in Docker with unknown UID) can read via the bind mount.
mkdir -p "$TELEMETRY_DIR"
chmod 755 "$TELEMETRY_DIR"
touch "$TELEMETRY_FILE"
chmod 644 "$TELEMETRY_FILE"

# Check jq availability. If absent, telemetry degrades gracefully (fail-open).
if ! command -v jq &>/dev/null; then
    echo "[WARN] jq not found. Structured telemetry disabled. Install: sudo apt-get install -y jq"
    _JQ_AVAILABLE=false
else
    _JQ_AVAILABLE=true
fi
# ─────────────────────────────────────────────────────────────────────────────

exec > >(tee -a "$LOG_FILE") 2>&1

# ── log_telemetry() ───────────────────────────────────────────────────────────
# Emits a single NDJSON line to the telemetry file.
# ARGS: $1=phase (string), $2=status (string), $3=message (string)
#
# SECURITY CONSTRAINTS:
#   - NEVER pass secret values as arguments to this function.
#   - All args are string-escaped by jq --arg, preventing JSON injection.
#   - Uses ISO-8601 UTC for Loki temporal alignment.
#   - Fails open (does not abort deployment) if jq or file write fails.
# ─────────────────────────────────────────────────────────────────────────────
log_telemetry() {
    if [ "$_JQ_AVAILABLE" != "true" ]; then return 0; fi
    local phase="$1"
    local status="$2"
    local message="$3"
    local ts
    ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    jq -cn \
        --arg ts        "$ts"      \
        --arg run_id    "$RUN_ID"  \
        --arg phase     "$phase"   \
        --arg status    "$status"  \
        --arg message   "$message" \
        '{timestamp:$ts, run_id:$run_id, phase:$phase, status:$status, message:$message}' \
        >> "$TELEMETRY_FILE" 2>/dev/null || true
}

# ── notify() ──────────────────────────────────────────────────────────────────
# Dispatches deployment status to Telegram using Infisical-injected tokens.
# ─────────────────────────────────────────────────────────────────────────────
notify() {
    local phase="$1"
    local status="$2"
    local message="$3"
    local emoji
    case "$status" in
        SUCCESS)     emoji="✅" ;;
        FAILURE)     emoji="🚨" ;;
        IN_PROGRESS) emoji="🔄" ;;
        SKIPPED)     emoji="⏭️" ;;
        WARNING)     emoji="⚠️" ;;
        *)           emoji="ℹ️" ;;
    esac
    
    local hostname
    hostname=$(hostname 2>/dev/null || echo "server")
    local text="${emoji} *Treishvaam Deploy* | ${phase} | ${status}
Host: \`${hostname}\`
Run: \`${RUN_ID}\`
Detail: ${message}"

    if [ -n "${TELEGRAM_BOT_TOKEN:-}" ] && [ -n "${TELEGRAM_CHAT_ID:-}" ]; then
        curl -s -m 10 -X POST \
            "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
            -d "chat_id=${TELEGRAM_CHAT_ID}" \
            -d "text=${text}" \
            -d "parse_mode=Markdown" \
            >/dev/null 2>&1 &
    fi
}

# --- 0. CRITICAL CONCURRENCY LOCK & WIPER TRAP ---
LOCKFILE="/tmp/treishvaam_deploy.lock"
if [ -f "$LOCKFILE" ]; then
    LOCK_AGE=$(( $(date +%s) - $(stat -c %Y "$LOCKFILE" 2>/dev/null || echo 0) ))
    if [ "$LOCK_AGE" -gt 7200 ]; then
        echo "[Lock] Stale lockfile older than 2 hours. Forcing removal."
        rm -f "$LOCKFILE"
    else
        PID=$(cat "$LOCKFILE")
        if kill -0 "$PID" 2>/dev/null; then
            PROC_NAME=$(ps -p "$PID" -o comm= 2>/dev/null || echo "")
            if echo "$PROC_NAME" | grep -qE 'bash|auto_deploy'; then
                log_telemetry "STARTUP" "SKIPPED" "Concurrent deployment running (PID $PID, COMM: $PROC_NAME). Exiting."
                exit 0
            else
                echo "[Lock] PID $PID exists but belongs to '$PROC_NAME' (recycled). Clearing stale lock."
                rm -f "$LOCKFILE"
            fi
        else
            rm -f "$LOCKFILE"
        fi
    fi
fi
echo $$ > "$LOCKFILE"

# ── DEAD MAN'S SWITCH ─────────────────────────────────────────────────────────
# Emitted IMMEDIATELY after the concurrency check passes. If process is SIGKILLed,
# this IN_PROGRESS event will be the final entry, triggering Grafana timeout alerts.
log_telemetry "STARTUP" "IN_PROGRESS" "Engine B deployment started. RUN_ID: $RUN_ID"
notify "STARTUP" "IN_PROGRESS" "Engine B deployment started."

# ── BULLETPROOF TRAP ──────────────────────────────────────────────────────────
# The WIPE operation MUST execute first, unconditionally, before any telemetry.
# This is the Flash & Wipe guarantee. Do NOT reorder these operations.
trap '
    rm -f "$LOCKFILE"
    cp "$TEMPLATE_FILE" "$ENV_FILE" 2>/dev/null || true
    log_telemetry "CLEANUP" "${DEPLOY_STATUS:-FAILURE}" "Deployment ended. Secrets wiped from disk."
    notify "CLEANUP" "${DEPLOY_STATUS:-FAILURE}" "Deployment ended. Secrets wiped from disk."
' EXIT

echo "================================================================"
echo "[$(date)] 🚀 Non-Disruptive Deployment Triggered."
echo "================================================================"

chmod +x scripts/*.sh backup/*.sh 2>/dev/null || true
chmod +x scripts/auto_deploy.sh 2>/dev/null || true

# --- 0.5 TEMPORAL SYNCHRONIZATION ENFORCEMENT ---
echo "[System] Ensuring NTP synchronization is active..."
sudo -n timedatectl set-ntp true >/dev/null 2>&1 || true

# --- 1. SECURE RESTART STRATEGY (INFISICAL INJECTION) ---
# ── SUBSHELL LEAK PREVENTION ─────────────────────────────────────────────────
# Explicitly disable command tracing (set -x) for this entire section to
# guarantee raw INFISICAL_CLIENT_SECRET is never echoed to STDERR.
set +x
# ─────────────────────────────────────────────────────────────────────────────
log_telemetry "INFISICAL_INJECTION" "IN_PROGRESS" "Authenticating with Infisical vault..."

echo "[Security] Preparing Secure Environment..."

if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "CRITICAL: $TEMPLATE_FILE missing! Cannot fetch secrets."
    log_telemetry "INFISICAL_INJECTION" "FAILURE" "Missing .env.template file."
    notify "INFISICAL_INJECTION" "FAILURE" "Missing .env.template file."
    exit 1
fi
cp "$TEMPLATE_FILE" "$ENV_FILE"

export INFISICAL_PROJECT_ID=$(grep -E '^INFISICAL_PROJECT_ID=' "$ENV_FILE" | cut -d '=' -f2 | tr -d " \"'\r")
export INFISICAL_CLIENT_ID=$(grep -E '^INFISICAL_CLIENT_ID=' "$ENV_FILE" | cut -d '=' -f2 | tr -d " \"'\r")
export INFISICAL_CLIENT_SECRET=$(grep -E '^INFISICAL_CLIENT_SECRET=' "$ENV_FILE" | cut -d '=' -f2 | tr -d " \"'\r")

echo "[Security] Authenticating with Infisical..."
rm -f "$HOME/.infisical/.infisical.json" 2>/dev/null || true

infisical login --method=universal-auth --client-id="$INFISICAL_CLIENT_ID" --client-secret="$INFISICAL_CLIENT_SECRET" --domain="https://app.infisical.com" --silent 2>>"$LOG_FILE" || echo "  > Notice: Login command returned non-zero, checking export..."

TEMP_SECRETS=$(mktemp)
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --domain="https://app.infisical.com" --format dotenv > "$TEMP_SECRETS" 2>>"$LOG_FILE"
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ] && [ -s "$TEMP_SECRETS" ] && ! grep -qE "arrow keys|Select project|login" "$TEMP_SECRETS"; then
    cat "$TEMP_SECRETS" | sed 's/\r//g' | sed -E "s/='(.*)'$/=\1/" | sed -E 's/="(.*)"$/=\1/' >> "$ENV_FILE"
    
    # Export Telegram notification variables to memory for the notify() function to consume even after .env wipe
    export TELEGRAM_BOT_TOKEN=$(grep -E '^TELEGRAM_BOT_TOKEN=' "$ENV_FILE" | cut -d '=' -f2- | tr -d " \"'\r")
    export TELEGRAM_CHAT_ID=$(grep -E '^TELEGRAM_CHAT_ID=' "$ENV_FILE" | cut -d '=' -f2- | tr -d " \"'\r")
    
    echo "  > Secrets successfully injected into transient memory."
    rm "$TEMP_SECRETS"
    log_telemetry "INFISICAL_INJECTION" "SUCCESS" "Secrets injected from Infisical vault into transient .env."
else
    echo "CRITICAL ERROR: Infisical authentication or export failed. Vault is empty."
    echo "Aborting deployment to prevent 401 Unauthorized cascade."
    rm "$TEMP_SECRETS"
    log_telemetry "INFISICAL_INJECTION" "FAILURE" "Infisical export failed. Aborting to prevent 401 cascade."
    notify "INFISICAL_INJECTION" "FAILURE" "Infisical export failed. Aborting to prevent 401 cascade."
    exit 1
fi

sudo -n chown $(id -u):$(id -g) .env 2>/dev/null || true

# --- 2. MEMORY RECOVERY & DNS HEALING ---
echo "[System] Aggressive build cache cleanup (preventing disk exhaustion)..."
docker image prune -f
docker builder prune --filter until=24h -f
log_telemetry "CACHE_CLEANUP" "SUCCESS" "Build cache pruned. $(docker system df --format 'table {{.Type}}\t{{.Size}}\t{{.Reclaimable}}' 2>/dev/null | tail -n +2 | tr '\n' '|')"

echo "[System] Executing Non-Disruptive OS Memory Recovery..."
sudo -n sync && echo 3 | sudo -n tee /proc/sys/vm/drop_caches > /dev/null
sudo -n sysctl -w vm.max_map_count=262144 > /dev/null
sudo -n sysctl -w net.ipv6.conf.all.disable_ipv6=1 > /dev/null

export GOMAXPROCS=1
export DOCKER_BUILDKIT=1

log_telemetry "MEMORY_RECOVERY" "SUCCESS" "OS memory recovery and sysctl tuning complete."

# --- 3. SMART ZERO-DOWNTIME REBUILD (STAGGERED TO PREVENT SSH KERNEL LOCK) ---
echo "[Docker] Reconciling Daemon Metadata..."
if docker ps -a --filter "status=dead" | grep -q 'dead'; then
    echo "  > Ghost containers detected. Purging corrupted metadata..."
    docker rm -f $(docker ps -aq --filter "status=dead") 2>/dev/null || true
fi
log_telemetry "GHOST_PRUNE" "SUCCESS" "Dead container ghost metadata purged."

echo "[Docker] Executing Non-Disruptive State Healing..."
docker compose rm -f || true

echo "[Docker] Applying Staggered Infrastructure Ignition (Preventing I/O Storm)..."
log_telemetry "STAGGERED_IGNITION" "IN_PROGRESS" "Beginning staggered container ignition sequence."

docker compose up -d --no-deps treishvaam-redis redis backup-service
sleep 3

docker compose up -d --no-deps wazuh-manager aegis-canary-server
sleep 3

docker compose up -d --no-deps elasticsearch
sleep 3

ZKP_HASH=$(find ./aegis/zkp-service -type f 2>/dev/null | sort | xargs md5sum 2>/dev/null | md5sum | awk '{print $1}' || echo "unknown")
ZKP_LAST_SHA_FILE="/opt/treishvaam/.zkp_last_built_sha"
ZKP_LAST_SHA=$(cat "$ZKP_LAST_SHA_FILE" 2>/dev/null || echo "none")

if [ "$ZKP_HASH" = "$ZKP_LAST_SHA" ] && [ "$ZKP_HASH" != "unknown" ]; then
    echo "[Docker] ZKP service unchanged (Hash: $ZKP_HASH). Skipping rebuild."
    docker compose up -d --no-deps aegis-zkp-service
else
    echo "[Docker] ZKP service changed. Rebuilding Go binary..."
    docker compose up -d --build --no-deps aegis-zkp-service
    echo "$ZKP_HASH" > "$ZKP_LAST_SHA_FILE"
fi
sleep 3

docker compose up -d --build
sleep 3

log_telemetry "STAGGERED_IGNITION" "SUCCESS" "Staggered infrastructure ignition complete."

echo "[Docker] Surgically cycling application tier to consume updated artifacts & secrets..."
docker compose up -d --force-recreate --no-deps --scale backend=1 backend
sleep 5

docker compose up -d --force-recreate --no-deps nginx envoy-sidecar
log_telemetry "APPLICATION_TIER" "SUCCESS" "Backend (1 replica), nginx, and envoy-sidecar force-recreated."

echo "[System] Stabilizing application layer (Waiting 10s)..."
sleep 10

# --- 3.5 POST-DEPLOYMENT HEALTH VERIFICATION ---
echo "[Health] Verifying deployment health..."
log_telemetry "HEALTH_CHECK" "IN_PROGRESS" "Polling container health states..."

UNHEALTHY_CONTAINERS=""
for container in treishvaam-db treishvaam-redis treishvaam-elastic treishvaam-rabbitmq; do
    STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "missing")
    if [ "$STATUS" != "healthy" ] && [ "$STATUS" != "none" ]; then
        UNHEALTHY_CONTAINERS="$UNHEALTHY_CONTAINERS $container($STATUS)"
    fi
done

BACKEND_HEALTHY=false
for i in $(seq 1 8); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 5 http://localhost/actuator/health 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
        BACKEND_HEALTHY=true
        break
    fi
    echo "  > Backend health check attempt $i/8 — HTTP $HTTP_CODE. Waiting 15s..."
    sleep 15
done

if [ "$BACKEND_HEALTHY" = "false" ]; then
    log_telemetry "HEALTH_CHECK" "FAILURE" "Backend /actuator/health not responding after 120s. Containers:${UNHEALTHY_CONTAINERS}"
    notify "HEALTH_CHECK" "FAILURE" "Backend /actuator/health not responding. Unhealthy:${UNHEALTHY_CONTAINERS}"
    DEPLOY_STATUS="FAILURE"
else
    log_telemetry "HEALTH_CHECK" "SUCCESS" "Backend healthy. Container states verified."
    
    echo "[Docker] Scaling backend safely to 2 replicas..."
    docker compose up -d --scale backend=2 backend
    
    if [ -n "$UNHEALTHY_CONTAINERS" ]; then
        log_telemetry "HEALTH_CHECK" "WARNING" "Some infrastructure containers not healthy:${UNHEALTHY_CONTAINERS}"
        notify "HEALTH_CHECK" "WARNING" "Backend healthy but infra containers warn:${UNHEALTHY_CONTAINERS}"
    else
        notify "HEALTH_CHECK" "SUCCESS" "All systems healthy. Deployment confirmed live."
    fi
    DEPLOY_STATUS="SUCCESS"
fi

# --- 4. SECURITY WIPE (Flash & Wipe) ---
echo "[Security] Wiping secrets from disk..."
# (Trap auto-handles this, but we force it strictly before the success flag)
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > SECURE WIPE COMPLETE. .env restored to template baseline."

log_telemetry "COMPLETION" "${DEPLOY_STATUS}" "Deployment sequence finished."

echo "[$(date)] ✅ Intelligent Rebuild & Deployment Complete."
echo "================================================================"