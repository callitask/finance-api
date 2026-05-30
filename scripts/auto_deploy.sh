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
#   - Nginx (for reload triggers)
#
# Security Constraints:
#   - Secrets must never be persisted to disk (Flash & Wipe strategy).
#   - .env file is transient.
#
# Non-Negotiables:
#   - Must run every minute via cron.
#   - Must handle permission errors gracefully.
#
# Change Intent:
#   - Eliminate IPv6 blackhole timeouts during image pulls and fix internal Docker DNS drops.
#
# Future AI Guidance:
#   - Do NOT use `sudo chown -R $USER:$USER .` as it corrupts MariaDB host-mounted data.
#   - Memory recovery `drop_caches` remains via an ephemeral privileged container.
#
# IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#   - ADDED:
#     • Initial deployment logic
#   - EDITED:
#     • Added Pre-Flight Permission Fix (sudo chown) to prevent Git lockouts.
#     • Upgraded Nginx restart to 'force-recreate' to guarantee config reload.
#     • Reason: Fix 404s caused by stale Nginx config and permission denied errors.
#   - EDITED:
#     • Added `sed "s/['\"]//g"` pipeline to the Infisical export command.
#     • Reason: Resolves backend crash (`IllegalStateException: LINKEDIN_TOKEN_ENCRYPTION_KEY missing or empty`). Infisical wraps `.env` values in literal single quotes, which corrupts the exact 32-byte Base64 array required by the Java AES-256-GCM cipher. Stripping quotes ensures clean variable hydration.
#   - EDITED:
#     • Injected a proactive OS Memory Recovery sequence (`drop_caches` and `docker builder prune`) before `docker compose up`.
#     • Reason: Fixes a severe out-of-memory crash loop (OOM Kill / Exit 137). When scaling to 2 replicas, the instantaneous memory spike of booting two Spring Boot JVMs and their respective Python processes simultaneously exhausted the VM's RAM. Evicting the Linux page cache guarantees maximum available RAM for the container initialization phase.
#   - EDITED:
#     • Elevated Step 5 (Permission Repair) to utilize `sudo` when creating and chmodding the `logs`, `uploads`, and `sitemaps` bind-mount directories.
#     • Reason: Fixes `java.io.FileNotFoundException: /app/logs/backend.json (Permission denied)` crash loop. Docker daemon creates host volumes as root, causing the unprivileged CI user to fail at modifying them, starving the non-root Spring Boot JVM of write access.
#   - EDITED:
#     • Replaced all `sudo` commands with ephemeral `docker run --rm alpine` privilege escalations.
#     • Restricted Git permission repair to `.git` to prevent MariaDB data corruption.
#     • Reason: `sudo` commands were silently failing because `vboxuser` requires an interactive password prompt. Using the Docker daemon guarantees root-level host modifications (folder creation, chmod 777, and sysctl drop_caches) without interactive blocking.
#   - EDITED:
#     • Removed inline Docker volume permission repair.
#     • Reason: Delegated to a native Docker Compose init-container (`permission-fixer`) to eliminate race conditions between bash script execution and container orchestration. Memory flush via privileged Docker run remains.
#   - EDITED (Rolling Cache Retention & BuildKit):
#     • Swapped destructive `docker builder prune -a -f` for a rolling retention strategy (`--filter until=168h -f`).
#     • Enforced `export DOCKER_BUILDKIT=1` before `docker compose up`.
#     • Reason: A destructive whole-cache wipe deletes the internal Maven dependencies, forcing a massive 5-minute internet re-download loop on every push. Using BuildKit caching + 7-day retention balances minimal disk growth with maximum deployment speed.
#   - EDITED (OOM Prevention & Infrastructure Stabilization):
#     • Injected a privileged Docker alpine sequence to dynamically provision and mount a 4GB swap file on the Ubuntu host (`/swapfile`).
#     • Reason: A catastrophic Linux OOM Killer event eradicated MariaDB, Redis, and Keycloak from the Docker daemon because the host's 4.8GB RAM was instantly depleted by the dual-replica backend JVMs. Provisioning 4GB of swap directly via chroot ensures the memory buffer exists without requiring manual SSH intervention.
#   - EDITED (Cron Concurrency & DNS Healing):
#     • Added PID-verified Lockfile (`/tmp/treishvaam_deploy.lock`).
#     • Added host DNS flush (`nsenter -t 1 -m -u -n -i systemctl restart systemd-resolved`).
#     • Reason: The 1-minute cron job was overlapping with active 3-minute deployments, causing the second script to run `docker compose down` and terminate the first script's active image pulls (Resulting in `Interrupted` and `SERVFAIL` logs). The lockfile guarantees sequential execution, while the DNS flush ensures VirtualBox NAT routes do not stall `quay.io` pulls.
#   - EDITED (IPv6 & DNS Healing Fix):
#     • Removed `systemctl restart systemd-resolved` from the script.
#     • Added `sysctl -w net.ipv6.conf.all.disable_ipv6=1` via nsenter.
#     • Added a second `set -a; source "$ENV_FILE"; set +a` after Infisical hydration.
#     • Reason: The previous DNS restart severed the Docker daemon's internal `127.0.0.11` bridge, causing the backend to crash with `java.net.UnknownHostException: Failed to resolve 'redis' (SERVFAIL)`. The IPv6 disable command fixes the `dial tcp [2600:...]:443: i/o timeout` registry pull errors without breaking local DNS. The re-source command guarantees shell-level variables are hydrated before `docker compose up` executes.
#   - EDITED (Bash Semicolon Crash Resolution & BuildKit Limits):
#     • Removed the `sed` quote-stripper and the final `source "$ENV_FILE"` command from the Infisical export block.
#     • Restored `export GOMAXPROCS=1`.
#     • Reason: Docker Compose natively reads `.env` dynamically and strips quotes automatically. Forcing Bash to evaluate it caused a fatal syntax error because the MariaDB TDE string (`1;828FA...`) contains a semicolon, crashing the deploy script before `docker compose up` could execute, resulting in permanently missing database and proxy containers.
#   - EDITED (Environment Shadowing & Crash Loop Fix):
#     • Replaced `set -a; source "$ENV_FILE"; set +a` with an isolated `grep`/`cut` extraction for `INFISICAL_PROJECT_ID`.
#     • Reason: Sourcing the `.env.template` polluted the active Bash shell with empty strings (e.g., `REDIS_PASSWORD=""`). Docker Compose strictly prioritizes the host's active shell environment over the generated `.env` file. This caused the backend to receive an empty Redis password (triggering `NOAUTH HELLO` crashes) and the database to receive an empty encryption key (causing a nameless `Dead` container).
#   - EDITED (Quote Sanitization Restoration):
#     • Re-introduced `sed -i "s/['\"]//g"` against the temporary Infisical secrets file before appending to `.env`.
#     • Reason: Infisical wraps secrets in literal quotes. Without stripping them, Docker Compose injects the literal quotes into the container environment. This caused Redis Lettuce to send `'mypassword'` instead of `mypassword`, resulting in fatal `NOAUTH HELLO` exceptions across the backend fleet.
#   - EDITED (Quote Sanitization Reversal & YAML Protection):
#     • Removed `sed -i "s/['\"]//g"` completely.
#     • Reason: Stripping single quotes from the `.env` file exposed passwords containing `#` and `$` directly to Docker Compose's YAML parser, which interpreted them as comments/substitutions, permanently truncating `REDIS_PASSWORD` and causing the `NOAUTH` crashes to persist. Docker Compose natively requires secrets to be quoted to protect special characters. The literal quote injection into Spring Boot will be solved by relying exclusively on compose `environment:` mapping instead of `env_file`.
#   - EDITED (Dual-Env OS Shadow-Kill):
#     • Replaced global `sed` quote-stripping with targeted boundary `sed` to generate `.env.backend`.
#     • Added dynamic `unset` loop against `.env.template` variables to destroy OS-level empty strings.
#     • Added `.env.backend` to Flash & Wipe destruction.
#     • Reason: Fixes DB `key id 1 is missing` and Redis `NOAUTH HELLO` crash loops by preventing the GitHub Runner's empty shell variables from overriding Docker Compose injection, while preserving password internal special characters.
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
        # Silently exit without logging to prevent massive log bloat every minute
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
# Prevents "Permission Denied" during git operations if files were touched by root/docker
echo "[System] Fixing Git tracking permissions safely..."
docker run --rm -v "$(pwd):/workspace" alpine sh -c "chown -R $(id -u):$(id -g) /workspace/.git /workspace/scripts /workspace/docker-compose.yml 2>/dev/null || true"

# --- 1. BRANCH INTELLIGENCE ---
git fetch --all

TARGET_BRANCH="main" # Default fallback
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
    echo "  > Timestamp: $LATEST_TIMESTAMP"
    echo "================================================================"

    CHANGED_FILES=$(git diff --name-only HEAD "origin/$TARGET_BRANCH")
    
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
    
    # We extract INFISICAL_PROJECT_ID safely without polluting the bash environment
    # Shell environment overrides Docker Compose .env files. We must NEVER source the template globally.
    export INFISICAL_PROJECT_ID=$(grep -E '^INFISICAL_PROJECT_ID=' "$ENV_FILE" | cut -d '=' -f2 | tr -d ' "\'')
    
    echo "[Security] Fetching live secrets from Infisical..."
    echo "" >> "$ENV_FILE"

    TEMP_SECRETS=$(mktemp)
    
    infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$TEMP_SECRETS" 2>/dev/null
    EXIT_CODE=$?

    if [ $EXIT_CODE -eq 0 ] && [ -s "$TEMP_SECRETS" ] && ! grep -qE "arrow keys|Select project|login" "$TEMP_SECRETS"; then
        cat "$TEMP_SECRETS" >> "$ENV_FILE"
        echo "  > Secrets injected successfully."
        rm "$TEMP_SECRETS"
    else
        echo "CRITICAL: Infisical fetch failed or returned interactive prompt."
        echo "  > Possible Cause: Machine is not authenticated. Please run 'infisical login'."
        echo "  > ABORTING DEPLOYMENT to prevent crashing production with empty/corrupt secrets."
        rm "$TEMP_SECRETS"
        exit 1
    fi

    # --- DUAL-ENV ARCHITECTURE: Generate quote-free .env.backend for Spring Boot JVM ---
    # The .env file retains Infisical single quotes for Docker Compose YAML interpolation
    # (protects # and $ in passwords from YAML parser truncation in redis/db containers).
    # The .env.backend strips ONLY surrounding single quotes using a targeted regex,
    # safe for passwords containing internal apostrophes, semicolons, and special chars.
    echo "[Security] Generating quote-free .env.backend for Spring Boot JVM injection..."
    sed "s/^\([A-Za-z_][A-Za-z0-9_]*\)='\(.*\)'$/\1=\2/" "$ENV_FILE" > "${ENV_FILE}.backend"
    echo "  > .env.backend generated (surrounding quotes stripped)."

    # --- OS SHADOW-KILL: Unset all template variables from the active shell ---
    # CRITICAL: Docker Compose prioritises active shell environment over .env file.
    # The GitHub Actions runner exports empty strings (e.g. REDIS_PASSWORD="") into
    # its shell environment. These empty strings silently override the .env file,
    # injecting nulls into every container and causing the NOAUTH and keyfile crashes.
    # This unset loop permanently destroys those shell overrides before compose runs.
    echo "[Security] Executing OS shadow-kill: unsetting shell environment overrides..."
    while IFS= read -r line; do
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ -z "$line" ]] && continue
        var_name=$(echo "$line" | cut -d'=' -f1 | tr -d ' ')
        [[ "$var_name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] && unset "$var_name"
    done < "$TEMPLATE_FILE"
    echo "  > OS shell overrides neutralised."

    # Reset ownership of .env files to ensure the unprivileged runner can wipe them later
    docker run --rm -v "$(pwd):/workspace" alpine sh -c \
        "chown $(id -u):$(id -g) /workspace/.env /workspace/.env.backend 2>/dev/null || true"

    # --- 5. PERMISSION REPAIR ---
    echo "[System] Folder permissions are now securely orchestrated via Docker Compose Init Container (permission-fixer)."
    
    echo "[Docker] Rebuilding services..."
    docker compose down --remove-orphans

    # --- 5.5 MEMORY RECOVERY, SWAP & DNS HEALING (CRITICAL) ---
    echo "[System] Executing Aggressive OS Memory Recovery..."
    docker run --rm --privileged alpine sh -c "sync && echo 3 > /proc/sys/vm/drop_caches"
    
    echo "[System] Disabling host IPv6 to prevent Registry Pull Timeouts (i/o timeout on 2600:)..."
    docker run --rm --privileged --pid=host alpine nsenter -t 1 -m -u -n -i sysctl -w net.ipv6.conf.all.disable_ipv6=1
    docker run --rm --privileged --pid=host alpine nsenter -t 1 -m -u -n -i sysctl -w net.ipv6.conf.default.disable_ipv6=1

    echo "[System] Ensuring 4GB Enterprise Swap Space via host mount..."
    docker run --rm --privileged -v /:/host alpine sh -c "if [ ! -f /host/swapfile ]; then echo '[Swap] Creating 4GB swap file...'; dd if=/dev/zero of=/host/swapfile bs=1M count=4096 status=none && chmod 600 /host/swapfile && mkswap /host/swapfile && chroot /host swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /host/etc/fstab; else echo '[Swap] Swapfile exists. Ensuring it is active...'; chroot /host swapon -a || true; fi"

    docker builder prune --filter until=168h -f

    # Enforce single-threaded build extraction to prevent 1.9GB RAM spikes from assassinating the GH Runner
    export GOMAXPROCS=1
    export DOCKER_BUILDKIT=1
    docker compose up -d --build --force-recreate
    
    echo "[System] Stabilizing containers (Waiting 10s)..."
    sleep 10
    
    # D. CONDITIONAL RESTARTS
    if echo "$CHANGED_FILES" | grep -qE "^nginx/"; then
        echo "[Config] Nginx configuration changed. Force-Reloading..."
        docker compose up -d --force-recreate --no-deps nginx
    fi

    if echo "$CHANGED_FILES" | grep -q "config/"; then
        echo "[Config] Monitoring stack changed. Restarting..."
        docker restart treishvaam-prometheus treishvaam-grafana treishvaam-loki
    fi
    
    # E. SECURITY WIPE (Flash & Wipe)
    echo "[Security] Wiping secrets from disk..."
    cp "$TEMPLATE_FILE" "$ENV_FILE"
    rm -f "${ENV_FILE}.backend"
    echo "  > SECURE WIPE COMPLETE. .env restored to template. .env.backend destroyed."
    
    docker image prune -f
    
    echo "[$(date)] ✅ Update & Deployment Complete for [$TARGET_BRANCH]."
    echo "================================================================"
else
    :
fi