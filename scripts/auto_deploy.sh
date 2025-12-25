#!/bin/bash

# ==============================================================================
# TREISHVAAM FINANCE - ENTERPRISE WATCHDOG (AUTO DEPLOY)
# ==============================================================================
# Role: 
#   1. Watches Git for infrastructure/config changes.
#   2. Auto-selects the most recent branch (main vs develop).
#   3. Updates the server files (Self-Healing).
#   4. Injects secrets securely (Flash & Wipe) to restart services if needed.
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

# --- 1. BRANCH INTELLIGENCE ---
git fetch --all

TS_MAIN=$(git log -1 --format=%ct origin/main 2>/dev/null || echo 0)
TS_DEVELOP=$(git log -1 --format=%ct origin/develop 2>/dev/null || echo 0)

TARGET_BRANCH="main"

if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

# --- 2. DETECT CHANGES ---
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "================================================================"
    echo "[$(date)] 🚀 New activity detected on [$TARGET_BRANCH]."
    echo "  > Main TS: $TS_MAIN | Develop TS: $TS_DEVELOP"
    echo "================================================================"

    CHANGED_FILES=$(git diff --name-only HEAD "origin/$TARGET_BRANCH")
    
    # --- 3. SELF-HEALING UPDATE ---
    echo "[System] Syncing files with origin/$TARGET_BRANCH..."
    git checkout "$TARGET_BRANCH"
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
    
    # --- FIX: FORCE NEWLINE TO PREVENT VARIABLE MERGING ---
    echo "" >> "$ENV_FILE"
    # ----------------------------------------------------

    if infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv >> "$ENV_FILE"; then
        echo "  > Secrets injected."
    else
        echo "CRITICAL: Infisical fetch failed. Service restart may fail."
    fi
    
    # C. RESTART SERVICES (Passwordless)
    echo "[Docker] Rebuilding services..."
    
    docker compose down --remove-orphans
    docker compose up -d --build --force-recreate
    
    # D. CONDITIONAL RESTARTS
    if echo "$CHANGED_FILES" | grep -qE "^nginx/"; then
        echo "[Config] Nginx configuration changed. Restarting..."
        docker restart treishvaam-nginx
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