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
#   - Hardened permission handling and Nginx reload strategy to fix 404s/deployment stalls.
#
# Future AI Guidance:
#   - Do not remove the permission fix (sudo chown) without verifying non-root container user mapping.
#
# IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#   - ADDED:
#     • Initial deployment logic
#   - EDITED:
#     • Added Pre-Flight Permission Fix (sudo chown) to prevent Git lockouts.
#     • Upgraded Nginx restart to 'force-recreate' to guarantee config reload.
#     • Reason: Fix 404s caused by stale Nginx config and permission denied errors.
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
if command -v sudo >/dev/null 2>&1; then
    # Only run if we are not root but sudo is available
    if [ "$EUID" -ne 0 ]; then
        echo "[System] Fixing file permissions..."
        sudo chown -R $USER:$USER .
    fi
fi

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
    
    # Run Infisical. 
    infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$TEMP_SECRETS" 2>/dev/null
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

    # --- 5. PERMISSION REPAIR (CRITICAL FIX FOR NON-ROOT CONTAINER) ---
    echo "[System] Fixing permissions for Non-Root User..."
    # We ensure these folders exist and are writable by the container (UID 100/101)
    mkdir -p logs uploads sitemaps
    chmod -R 777 logs uploads sitemaps
    # ------------------------------------------------------------------
    
    # C. RESTART SERVICES (Passwordless)
    echo "[Docker] Rebuilding services..."
    
    docker compose down --remove-orphans
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