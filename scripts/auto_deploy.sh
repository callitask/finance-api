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
#
#   - EDITED (CI/CD Remediation - Engine B Timeout Hotfix):
#     • Increased the `/actuator/health` polling loop from 8 attempts (120s) to 15 attempts (225s).
#     • Reason: The JVM `backend` container requires a 160s `start_period` on the 4.8GB swapped VM. The previous 120s timeout caused Engine B to prematurely abort deployments and emit `FAILURE` telemetry before the JVM could finish igniting.
#
#   - EDITED (Remediation Hardening - Infisical Stateless Identity Fix):
#     • Replaced `infisical login` stateful expectations with direct stateless JWT token extraction via `grep -oE 'eyJ...'`.
#     • Exported `INFISICAL_TOKEN` natively to memory to bypass the CLI's broken file-cache dependency for Machine Identities.
#     • Prevented export `EOF` (`^D`) prompt failures by feeding the token directly into the export process.
#
#   - EDITED (Smart System Ghost Eradication Expansion - 2026-06-30):
#     • Upgraded the Daemon Metadata Reconciliation block to capture both `status=dead` AND `status=created` containers simultaneously.
#     • Reason: A previous ungraceful network crash left `aegis-zkp-service` physically locked in a `Created` limbo state, which bypassed the `dead` filter. This blocked subsequent rebuilds by hoarding internal DNS bindings. Extracting and mathematically purging both locked states via `$GHOST_NODES` guarantees absolute zero-state recovery without manual SSH intervention.
#
#   - EDITED (GitHub Actions Strict Abort Override & Telegram Synchronous Fix):
#     • Added 'set +e' at the top of the execution layer.
#     • Removed the background '&' operator from the Telegram curl payload in notify().
#     • Reason: GitHub Actions injects 'set -e' natively, which prematurely aborted Engine B on trivial Docker daemon warnings (like ghost reconciliation panics), halting the script in 31 seconds. The Telegram background request was failing because the EXIT trap killed the background curl process before network transmission could complete. Synchronizing the curl request ensures delivery.
#
#   - EDITED (Anti-Blind Boot Cascade Fix - 2026-07-02):
#     • Retained `set +e` to survive ghost metadata warnings, but injected explicit exit code validation (`$?`) directly after `docker compose up -d --build`.
#     • Reason: A Java OOM during the build phase previously crashed Maven (exit code 1). Because `set +e` was active, the script blindly continued into the application restart phase. Compounding this, the Git Runner canceled and wiped the `.env` file during the OOM thrash. This caused the script to inject blank passwords into the containers, resulting in a fatal NOAUTH redis loop and database lockout. Validating the build exit code forces a graceful, secure abort before the application layer is touched.
#
#   - EDITED (Concurrency Double-Wipe Trap Fix - 2026-07-02):
#     • Wrapped the EXIT trap logic in an `if [ "$OWNS_LOCK" = "true" ]` validation gate.
#     • Reason: If a second `git push` occurred while a deployment was already running, the second instance of Engine B would hit the concurrency lock, gracefully exit, and trigger its own `EXIT` trap. This un-gated trap would wipe the `.env` file and delete the lockfile out from under the *actively running* first deployment, crashing the live production databases with `NOAUTH` blank passwords. Now, only the process that successfully claims the lock is permitted to wipe the vault.
#
#   - EDITED (Secret Integrity Gate - INC-20260702-02 NOAUTH Fix):
#     • Injected a strict validation block checking for empty REDIS_PASSWORD and PROD_DB_PASSWORD immediately after Infisical extraction.
#     • Reason: Prevents deployment from continuing if Infisical successfully connects but writes empty values (e.g. from an empty vault or manual SSH poisoning cycle), permanently eliminating the risk of baking blank passwords into the Docker daemon.
#
#   - EDITED (Git Context Boundary Telemetry Upgrades - 2026-07-03):
#     • Upgraded `notify()` to ingest the `.deploy_meta` file generated by Engine A and natively inject absolute Timestamp, Hash, and Message metadata into Telegram payload.
#     • Reason: Engine B executes outside the Git repository boundary, requiring a structural cross-engine metadata pass to satisfy the auditable notification requirement.
#
#   - EDITED (Autonomic Kernel Recovery & Telemetry Synchronization - 2026-07-03):
#     • Integrated `sudo /opt/treishvaam/scripts/kernel-mount-recovery.sh` fallback on `docker rm -f` failure to autonomously heal Split-Brain daemon locks.
#     • Aligned the EXIT trap telemetry terminal phase to `COMPLETION` (was `CLEANUP`) to mathematically align with Engine A's polling logic and prevent infinite runner hangs on failed deployments.
#
#   - EDITED (Orchestrator-Driven Tiered Ignition & Active Memory Reclaim - 2026-07-03):
#     • Injected `sudo sysctl -w vm.overcommit_memory=1` into the baseline memory layer.
#     • Replaced the general compose ignition with an Orchestrator-Driven Tiered Ignition matrix.
#     • Added sequential kernel page-cache flushing via `drop_caches` between distinct component tiers.
#     • Reason: Fixes the memory registration denials causing public container build failures under severe cgroup constraints without introducing broken health-gate loops on distroless or scratch container layers.
#
#   - EDITED (Kernel Overcommit Logging Fix - 2026-07-06):
#     • Removed `>/dev/null 2>&1 || true` from the `sudo sysctl vm.overcommit_memory` command.
#     • Added explicit error handling and telemetry warnings.
#     • Reason: The Ansible playbook wasn't run, leaving `vboxuser` without sudo rights for `sysctl`. Hiding the output caused a silent failure where the kernel denied JVM memory allocations and killed the backend containers on boot (Exit Code 1). Explicitly logging this unmasks infrastructure failures.
#
#   - EDITED (Atomic Concurrency & Network Flap Resilience - 2026-07-06):
#     • Replaced bash-based PID concurrency lock with atomic kernel-level `flock` (File Descriptor 200).
#     • Added a 3-attempt retry loop to the Telegram `curl` dispatch.
#     • Removed stray `set -x` to prevent log pollution.
#     • Reason: GitHub Actions `cancel-in-progress` caused a race condition where two deployments wrote to telemetry simultaneously, corrupting the JSON. `flock` mathematically guarantees single-thread execution. The network flap during `docker compose rm` dropped the Telegram HTTP packet; the retry loop guarantees delivery.
#
#   - EDITED (SIGPIPE Assassination Fix - 2026-07-06):
#     • Removed 'exec > >(tee -a "$LOG_FILE") 2>&1' subshell redirection.
#     • Reason: The deploy.yml workflow natively redirects standard output via systemd-run. The internal tee subshell created a fatal double-redirection I/O collision. During the heavy Tier 3 load, the fragile pipe collapsed, sending a SIGPIPE that silently killed the deployment script exactly before Tier 4.
#
#   - EDITED (Telegram Silent Drop & VirtualBox STP Flap Fix - 2026-07-07):
#     • Increased the notify() curl retry loop from 3 attempts (6s) to 15 attempts (30s).
#     • Unmasked curl stderr (`curl -sS`) and routed output into `deploy_pipeline.log`.
#     • Reason: `docker compose rm -f` tears down the `treish_net` bridge interface. On VirtualBox bridged adapters, this triggers a Spanning Tree Protocol (STP) network flap that drops external routing for 15-30 seconds. The previous 6-second window expired while the network was physically disconnected, sending the Telegram SUCCESS ping into a black hole invisibly. Expanding the window and logging standard error guarantees delivery resilience and visibility.
# ==============================================================================

# ── GITHUB ACTIONS EXECUTION OVERRIDE ─────────────────────────────────────────
# GitHub Actions inherently executes shell scripts with set -e (Exit on Error).
# We MUST explicitly override this to prevent premature assassination on non-fatal
# Docker daemon warnings. However, we MUST manually validate critical exit codes.
set +e
# ─────────────────────────────────────────────────────────────────────────────

export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# ── ENGINE B TELEMETRY GLOBALS ────────────────────────────────────────────────
# Generated once per run for full correlation across all log events.
# uuidgen is part of uuid-runtime, standard on Ubuntu 24.
RUN_ID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "no-uuid-$(date +%s)")
TELEMETRY_DIR="/opt/treishvaam/logs"
TELEMETRY_FILE="$TELEMETRY_DIR/deploy_telemetry.ndjson"
DEPLOY_STATUS="FAILURE"   # Pessimistic default. Set to SUCCESS only on clean completion.
OWNS_LOCK="false"         # Defaults to false. Prevents the EXIT trap from executing a Double-Wipe.
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

# ── log_telemetry() ───────────────────────────────────────────────────────────
# Emits a single NDJSON line to the telemetry file.
# ARGS: $1=phase (string), $2=status (string), $3=message (string)
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
# Features absolute metadata boundary-crossing to inject Git context.
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
    
    # Safely cross the boundary to extract Git context written by Engine A
    local git_commit="Unknown"
    local git_msg="No metadata available"
    if [ -f "/opt/treishvaam/.deploy_meta" ]; then
        source "/opt/treishvaam/.deploy_meta"
        git_commit="${GIT_COMMIT:-Unknown}"
        # Strip internal backticks from message to prevent Markdown corruption in Telegram
        git_msg=$(echo "${GIT_MSG:-No metadata available}" | tr -d '\`')
    fi
    
    local timestamp
    timestamp=$(date -u +"%Y-%m-%d %H:%M:%S UTC")

    local text="${emoji} *Treishvaam Deploy* | ${phase} | ${status}
Host: \`${hostname}\`
Time: \`${timestamp}\`
Commit: \`${git_commit}\`
Msg: \`${git_msg}\`
Run: \`${RUN_ID}\`
Detail: ${message}"

    if [ -n "${TELEGRAM_BOT_TOKEN:-}" ] && [ -n "${TELEGRAM_CHAT_ID:-}" ]; then
        # Must be synchronous. Added expanded 15-attempt retry loop to survive VirtualBox 
        # STP network flaps during bridge teardowns which take 15-30 seconds to stabilize.
        local max_retries=15
        local attempt=1
        local success=false
        
        while [ $attempt -le $max_retries ]; do
            # Using -sS suppresses progress meters but unmasks errors directly to log file
            if curl -sS -m 10 -X POST \
                "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
                -d "chat_id=${TELEGRAM_CHAT_ID}" \
                -d "text=${text}" \
                -d "parse_mode=Markdown" \
                >> "$LOG_FILE" 2>&1; then
                success=true
                break
            fi
            echo "[WARN] Telegram network flap (Attempt $attempt/$max_retries). Retrying in 2s..." >> "$LOG_FILE"
            attempt=$((attempt + 1))
            sleep 2
        done
        
        if [ "$success" = "false" ]; then
            echo "[CRITICAL WARN] Telegram notification failed after $max_retries attempts. Network unreachable." >> "$LOG_FILE"
        fi
    fi
}

# --- 0. CRITICAL CONCURRENCY LOCK (ATOMIC) & WIPER TRAP ---
LOCKFILE="/tmp/treishvaam_deploy.lock"

# Open file descriptor 200 for the lockfile
exec 200>"$LOCKFILE"

# Attempt an exclusive, non-blocking lock. If it fails, another deployment is running.
if ! flock -n 200; then
    echo "[Lock] Concurrent deployment running (flock denied). Exiting gracefully." >> "$LOG_FILE"
    log_telemetry "STARTUP" "SKIPPED" "Concurrent deployment running (flock denied). Exiting gracefully."
    exit 0
fi

# Write our PID into the lockfile for diagnostic visibility
echo $$ >&200

OWNS_LOCK="true" # Lock explicitly acquired atomically. Authorized to execute Flash & Wipe.

# ── DEAD MAN'S SWITCH ─────────────────────────────────────────────────────────
log_telemetry "STARTUP" "IN_PROGRESS" "Engine B deployment started. RUN_ID: $RUN_ID"
notify "STARTUP" "IN_PROGRESS" "Engine B deployment started."

# ── BULLETPROOF TRAP ──────────────────────────────────────────────────────────
# We deliberately DO NOT delete the lockfile here. `flock` operates on the inode. 
# The kernel automatically releases the lock when the script terminates.
trap '
    if [ "$OWNS_LOCK" = "true" ]; then
        cp "$TEMPLATE_FILE" "$ENV_FILE" 2>/dev/null || true
        log_telemetry "COMPLETION" "${DEPLOY_STATUS:-FAILURE}" "Deployment ended. Secrets wiped from disk."
        notify "COMPLETION" "${DEPLOY_STATUS:-FAILURE}" "Deployment ended. Secrets wiped from disk."
    fi
' EXIT

echo "================================================================"
echo "[$(date)] 🚀 Non-Disruptive Deployment Triggered."
echo "================================================================"

chmod +x scripts/*.sh backup/*.sh 2>/dev/null || true
chmod +x scripts/auto_deploy.sh 2>/dev/null || true

# --- 0.5 TEMPORAL & KERNEL MEMORY OVERCOMMIT ENFORCEMENT ---
echo "[System] Forcing kernel overcommit memory allocation limits..."
if ! sudo -n sysctl -w vm.overcommit_memory=1; then
    echo "[CRITICAL WARNING] Failed to set vm.overcommit_memory=1. Backend JVM allocations WILL likely be denied by the kernel (Exit Code 1). Please run the Ansible ZSI playbook to grant sudo rights."
    log_telemetry "SYSTEM_TUNE" "WARNING" "Failed to set vm.overcommit_memory. Host kernel may deny memory allocations."
    notify "SYSTEM_TUNE" "WARNING" "Failed to set vm.overcommit_memory. Host kernel may deny memory allocations. Check sudoers!"
fi
echo "[System] Ensuring NTP synchronization is active..."
sudo -n timedatectl set-ntp true >/dev/null 2>&1 || true

# --- 1. SECURE RESTART STRATEGY (INFISICAL INJECTION) ---
set +x
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
export INFISICAL_DISABLE_UPDATE_CHECK=true
rm -f "$HOME/.infisical/.infisical.json" 2>/dev/null || true

# ── STATELESS JWT EXTRACTION ─────────────────────────────────────────────────
set +x
RAW_TOKEN=$(infisical login --method=universal-auth --client-id="$INFISICAL_CLIENT_ID" --client-secret="$INFISICAL_CLIENT_SECRET" --domain="https://app.infisical.com" --silent 2>/dev/null | grep -oE 'eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+' | head -n 1 || true)

if [ -z "$RAW_TOKEN" ]; then
    echo "CRITICAL ERROR: Infisical authentication failed. Could not extract JWT token."
    log_telemetry "INFISICAL_INJECTION" "FAILURE" "Infisical login failed. Token extraction yielded empty string."
    notify "INFISICAL_INJECTION" "FAILURE" "Infisical login failed. Check template credentials."
    exit 1
fi

export INFISICAL_TOKEN="$RAW_TOKEN"

TEMP_SECRETS=$(mktemp)
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$TEMP_SECRETS" 2>>"$LOG_FILE"
EXPORT_EXIT_CODE=$?

if [ $EXPORT_EXIT_CODE -eq 0 ] && grep -q "=" "$TEMP_SECRETS"; then
    cat "$TEMP_SECRETS" | sed 's/\r//g' | sed -E "s/='(.*)'$/=\1/" | sed -E 's/="(.*)"$/=\1/' >> "$ENV_FILE"
    
    export TELEGRAM_BOT_TOKEN=$(grep -E '^TELEGRAM_BOT_TOKEN=' "$ENV_FILE" | cut -d '=' -f2- | tr -d " \"'\r")
    export TELEGRAM_CHAT_ID=$(grep -E '^TELEGRAM_CHAT_ID=' "$ENV_FILE" | cut -d '=' -f2- | tr -d " \"'\r")
    
    # ── SECRET INTEGRITY GATE (ANTI-NOAUTH POISONING) ──────────────────────────
    LOCAL_REDIS_PASS=$(grep -E '^REDIS_PASSWORD=' "$ENV_FILE" | cut -d '=' -f2- | tr -d " \"'\r")
    LOCAL_DB_PASS=$(grep -E '^PROD_DB_PASSWORD=' "$ENV_FILE" | cut -d '=' -f2- | tr -d " \"'\r")

    if [ -z "$LOCAL_REDIS_PASS" ] || [ -z "$LOCAL_DB_PASS" ]; then
        echo "CRITICAL ERROR: Secret Integrity Gate Failed!"
        rm -f "$TEMP_SECRETS"
        log_telemetry "INFISICAL_INJECTION" "FAILURE" "Secret Integrity Gate failed: Critical passwords are blank."
        notify "INFISICAL_INJECTION" "FAILURE" "Secret Integrity Gate failed. Blank passwords detected."
        exit 1
    fi

    echo "  > Secrets successfully injected into transient memory."
    rm "$TEMP_SECRETS"
    log_telemetry "INFISICAL_INJECTION" "SUCCESS" "Secrets injected from Infisical vault into transient .env."
else
    echo "CRITICAL ERROR: Infisical export returned empty variables or update warnings."
    rm "$TEMP_SECRETS"
    log_telemetry "INFISICAL_INJECTION" "FAILURE" "Infisical export stream blocked or contained no valid keys."
    notify "INFISICAL_INJECTION" "FAILURE" "Infisical export returned invalid data."
    exit 1
fi

sudo -n chown $(id -u):$(id -g) .env 2>/dev/null || true

# --- 2. MEMORY RECOVERY & SYSTEM RECLAIM ---
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

# --- 3. RECONCILING DAEMON METADATA ---
echo "[Docker] Reconciling Daemon Metadata (Dead/Limbo state eradication)..."
GHOST_NODES=$(docker ps -aq --filter "status=dead" --filter "status=created")
if [ -n "$GHOST_NODES" ]; then
    echo "  > Limbo containers detected. Purging corrupted metadata..."
    if ! docker rm -f $GHOST_NODES 2>/dev/null; then
        echo "  > [WARNING] Standard purge failed (Kernel lock suspected). Engaging Autonomic Mount Recovery..."
        sudo -n /opt/treishvaam/scripts/kernel-mount-recovery.sh || echo "  > [CRITICAL] Autonomic recovery failed. Proceeding with caution..."
        # Structural 15s network-stabilization buffer to absorb Spanning Tree Protocol (STP) network adapter flaps
        echo "  > Waiting 15 seconds for VirtualBox network bridge to stabilize..."
        sleep 15
    fi
fi
log_telemetry "GHOST_PRUNE" "SUCCESS" "Dead/Limbo container ghost metadata purged."

echo "[Docker] Executing Non-Disruptive State Healing..."
docker compose rm -f || true

# --- 4. ORCHESTRATOR-DRIVEN HEALTH-GATED TIERED IGNITION MATRIX ---
echo "[Docker] Applying Hardened Tiered Ignition Sequence..."
log_telemetry "STAGGERED_IGNITION" "IN_PROGRESS" "Beginning orchestrator-driven tiered ignition sequence."

# ── TIER 1: Core Data Foundation ──
echo "[Ignition - Tier 1] Starting Core Database and Caching layers..."
docker compose up -d --no-deps treishvaam-db keycloak-db treishvaam-redis redis minio

echo "[Ignition - Tier 1] Reclaiming volatile memory allocations..."
sleep 10
sudo -n sync && echo 3 | sudo -n tee /proc/sys/vm/drop_caches > /dev/null

# ── TIER 2: Queue & Analytics Infrastructure ──
echo "[Ignition - Tier 2] Starting Search Engine and Messaging pipelines..."
docker compose up -d --no-deps elasticsearch rabbitmq wazuh-manager

echo "[Ignition - Tier 2] Reclaiming volatile memory allocations..."
sleep 10
sudo -n sync && echo 3 | sudo -n tee /proc/sys/vm/drop_caches > /dev/null

# ── TIER 3: Security Foundations & Utilities ──
echo "[Ignition - Tier 3] Evaluating Cryptographic Microservices..."
ZKP_HASH=$(find ./aegis/zkp-service -type f 2>/dev/null | sort | xargs md5sum 2>/dev/null | md5sum | awk '{print $1}' || echo "unknown")
ZKP_LAST_SHA_FILE="/opt/treishvaam/.zkp_last_built_sha"
ZKP_LAST_SHA=$(cat "$ZKP_LAST_SHA_FILE" 2>/dev/null || echo "none")

if [ "$ZKP_HASH" = "$ZKP_LAST_SHA" ] && [ "$ZKP_HASH" != "unknown" ]; then
    echo "  > ZKP service unchanged (Hash: $ZKP_HASH). Skipping rebuild."
    docker compose up -d --no-deps aegis-zkp-service
else
    echo "  > ZKP service changed. Rebuilding Go binary..."
    docker compose up -d --build --no-deps aegis-zkp-service
    echo "$ZKP_HASH" > "$ZKP_LAST_SHA_FILE"
fi

echo "  > Igniting collateral security and diagnostic utility systems..."
docker compose up -d --no-deps aegis-canary-server wazuh-agent promtail prometheus tempo grafana backup-service tunnel permission-fixer

echo "[Ignition - Tier 3] Reclaiming volatile memory allocations..."
sleep 10
sudo -n sync && echo 3 | sudo -n tee /proc/sys/vm/drop_caches > /dev/null

# ── TIER 4: Application Execution Layer ──
echo "[Ignition - Tier 4] Rebuilding and firing Core Java Application layer (1 replica)..."
docker compose up -d --build --no-deps --scale backend=1 backend
BUILD_EXIT_CODE=$?

if [ $BUILD_EXIT_CODE -ne 0 ]; then
    echo "CRITICAL ERROR: Application build tier failed (Exit Code: $BUILD_EXIT_CODE)."
    log_telemetry "STAGGERED_IGNITION" "FAILURE" "Docker compose build tier failed with exit code $BUILD_EXIT_CODE."
    notify "STAGGERED_IGNITION" "FAILURE" "Application build tier failed (OOM/Syntax error). Aborting."
    exit 1
fi
sleep 5

# ── TIER 5: Edge Web proxies ──
echo "[Ignition - Tier 5] Igniting reverse routing layer..."
docker compose up -d --force-recreate --no-deps nginx envoy-sidecar
log_telemetry "APPLICATION_TIER" "SUCCESS" "Tiered cluster ignition completed successfully."

echo "[System] Stabilizing application layer (Waiting 10s)..."
sleep 10

# --- 5. POST-DEPLOYMENT HEALTH VERIFICATION ---
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
for i in $(seq 1 15); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 5 http://localhost/actuator/health 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
        BACKEND_HEALTHY=true
        break
    fi
    echo "  > Backend health check attempt $i/15 — HTTP $HTTP_CODE. Waiting 15s..."
    sleep 15
done

if [ "$BACKEND_HEALTHY" = "false" ]; then
    log_telemetry "HEALTH_CHECK" "FAILURE" "Backend /actuator/health not responding after 225s. Containers:${UNHEALTHY_CONTAINERS}"
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

# --- 6. ATOMIC SECURITY WIPE ---
echo "[Security] Wiping secrets from disk..."
# Note: The EXIT trap will seamlessly handle the wipe if this block is missed, 
# but executing it here keeps the procedural flow clean before the final telemetry log.
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > SECURE WIPE COMPLETE. .env restored to template baseline."

log_telemetry "COMPLETION" "${DEPLOY_STATUS}" "Deployment sequence finished."

echo "[$(date)] ✅ Hardened Tiered Rebuild & Deployment Complete."
echo "================================================================"