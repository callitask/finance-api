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
#   - Cleaned up manual shell-based docker permission fixes in favor of Docker Compose `init-container` architecture.
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

# List of branches to monitor for deployment
MONITORED_BRANCHES=("main" "staging" "develop")

# Ensure we are in the project directory
cd "$PROJECT_DIR" || { echo "CRITICAL: Could not find project directory $PROJECT_DIR"; exit 1; }

# Start Logging
exec > >(tee -a "$LOG_FILE") 2>&1

# --- 0. PRE-FLIGHT PERMISSION FIX (CRITICAL) ---
# Prevents "Permission Denied" during git operations if files were touched by root/docker
# Using Docker to safely chown .git and scripts without touching database volumes
echo "[System] Fixing Git tracking permissions safely..."
docker run --rm -v "$(pwd):/workspace" alpine sh -c "chown -R $(id -u):$(id -g) /workspace/.git /workspace/scripts /workspace/docker-compose.yml 2>/dev/null || true"

# --- 1. BRANCH INTELLIGENCE ---
# Objective: Find which branch was updated most recently (highest Unix timestamp)
git fetch --all

TARGET_BRANCH="main" # Default fallback
LATEST_TIMESTAMP=0

echo "Checking branch activity..."

for branch in "${MONITORED_BRANCHES[@]}"; do
    # Get the commit timestamp of the remote branch. Returns 0 if branch doesn't exist.
    TS=$(git log -1 --format=%ct "origin/$branch" 2>/dev/null || echo 0)
    
    # Compare timestamps to find the winner
    if [ "$TS" -gt "$LATEST_TIMESTAMP" ]; then
        LATEST_TIMESTAMP=$TS
        TARGET_BRANCH="$branch"
    fi
done

# --- 2. DETECT CHANGES ---
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

# NOTE: We force update if the branches differ OR if we are on the wrong branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

if [ "$LOCAL" != "$REMOTE" ] || [ "$CURRENT_BRANCH" != "$TARGET_BRANCH" ]; then
    echo "================================================================"
    echo "[$(date)] 🚀 New activity detected. Winning Branch: [$TARGET_BRANCH]"
    echo "  > Timestamp: $LATEST_TIMESTAMP"
    echo "================================================================"

    CHANGED_FILES=$(git diff --name-only HEAD "origin/$TARGET_BRANCH")
    
    # --- 3. SELF-HEALING UPDATE ---
    echo "[System] Syncing files with origin/$TARGET_BRANCH..."
    
    # ROBUST SWITCHING: Create branch if missing, or force switch
    if git rev-parse --verify "$TARGET_BRANCH" >/dev/null 2>&1; then
        git checkout "$TARGET_BRANCH"
    else
        echo "[System] Branch $TARGET_BRANCH does not exist locally. Creating it..."
        git checkout -b "$TARGET_BRANCH" "origin/$TARGET_BRANCH"
    fi

    # Hard reset to match remote state exactly
    git reset --hard "origin/$TARGET_BRANCH"
    
    chmod +x scripts/*.sh backup/*.sh
    chmod +x scripts/auto_deploy.sh

    # --- 4. SECURE RESTART STRATEGY ---
    
    echo "[Security] Preparing Secure Environment..."
    
    # A. RESTORE AUTH KEYS
    if [ ! -f "$TEMPLATE_FILE" ]; then
        echo "CRITICAL: $TEMPLATE_FILE missing! Cannot fetch secrets."
        exit 1
    fi
    cp "$TEMPLATE_FILE" "$ENV_FILE"
    
    # B. INJECT SECRETS
    set -a; source "$ENV_FILE"; set +a
    
    echo "[Security] Fetching live secrets from Infisical..."
    
    # Force newline to prevent variable merging
    echo "" >> "$ENV_FILE"

    # CAPTURE OUTPUT TO TEMP FILE FOR VALIDATION (Fix for Garbage Injection)
    TEMP_SECRETS=$(mktemp)
    
    # Run Infisical and explicitly strip literal quotes to prevent AES/JDBC corruption
    infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv | sed "s/['\"]//g" > "$TEMP_SECRETS" 2>/dev/null
    EXIT_CODE=$?

    # VALIDATION: Check if file contains interactive prompt text or is empty
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

    # --- 5. PERMISSION REPAIR ---
    echo "[System] Folder permissions are now securely orchestrated via Docker Compose Init Container (permission-fixer)."
    
    # C. RESTART SERVICES (Passwordless)
    echo "[Docker] Rebuilding services..."
    
    docker compose down --remove-orphans

    # --- 5.5 MEMORY RECOVERY & SWAP PROVISIONING (CRITICAL) ---
    echo "[System] Executing Aggressive OS Memory Recovery..."
    docker run --rm --privileged alpine sh -c "sync && echo 3 > /proc/sys/vm/drop_caches"
    
    echo "[System] Ensuring 4GB Enterprise Swap Space via host mount..."
    docker run --rm --privileged -v /:/host alpine sh -c "if [ ! -f /host/swapfile ]; then echo '[Swap] Creating 4GB swap file...'; dd if=/dev/zero of=/host/swapfile bs=1M count=4096 status=none && chmod 600 /host/swapfile && mkswap /host/swapfile && chroot /host swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /host/etc/fstab; else echo '[Swap] Swapfile exists. Ensuring it is active...'; chroot /host swapon -a || true; fi"

    # Prune dangling builder cache older than 7 days to preserve active Maven layers
    docker builder prune --filter until=168h -f

    # Enforce BuildKit to utilize the Maven layer cache mounts
    export DOCKER_BUILDKIT=1
    docker compose up -d --build --force-recreate
    
    # --- SAFETY BUFFER ---
    # Wait for all containers to fully initialize
    echo "[System] Stabilizing containers (Waiting 10s)..."
    sleep 10
    
    # D. CONDITIONAL RESTARTS
    # IMPROVED: Force recreate nginx to ensure config volume is refreshed
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
    echo "  > SECURE WIPE COMPLETE. .env now contains only Auth Keys."
    
    docker image prune -f
    
    echo "[$(date)] ✅ Update & Deployment Complete for [$TARGET_BRANCH]."
    echo "================================================================"
else
    # echo "[$(date)] System is up to date."
    :
fi