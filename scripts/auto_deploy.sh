#!/bin/bash
# ----------------------------------------------------------------------
# SIMPLIFIED ENTERPRISE DEPLOY (PROD ONLY)
# Purpose: 
#   - Automatically deploys the latest code from (main OR develop).
#   - ALWAYS uses 'prod' secrets (Single Source of Truth).
#   - ALWAYS performs aggressive restarts to ensure stability.
# ----------------------------------------------------------------------

# 1. FIX PATH & ENVIRONMENT
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

cd "$PROJECT_DIR" || exit 1

# --- SECURITY: LOAD PROJECT ID ---
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# 2. FETCH & DETERMINE TARGET BRANCH
git fetch --all >> "$LOG_FILE" 2>&1

TS_MAIN=$(git log -1 --format=%ct origin/main 2>/dev/null || echo 0)
TS_DEVELOP=$(git log -1 --format=%ct origin/develop 2>/dev/null || echo 0)

TARGET_BRANCH="main"

# Even if we switch to develop, we will still use PROD secrets below
if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

# --- HELPER: SECURE EXECUTION WRAPPER ---
run_secure() {
    INFISICAL_CMD="infisical"
    if [ -f "/usr/local/bin/infisical" ]; then INFISICAL_CMD="/usr/local/bin/infisical"; fi
    if [ -f "/usr/bin/infisical" ]; then INFISICAL_CMD="/usr/bin/infisical"; fi

    if command -v $INFISICAL_CMD &> /dev/null; then
        echo "[$(date)] 🔐 Injecting 'prod' secrets via Infisical..." >> "$LOG_FILE"
        # HARDCODED TO PROD
        $INFISICAL_CMD run --projectId "$INFISICAL_PROJECT_ID" --env prod -- "$@"
    else
        echo "[$(date)] ❌ CRITICAL: Infisical not found. Deployment may fail." >> "$LOG_FILE"
        "$@"
    fi
}

# 3. CHECK FOR ACTIVITY
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New activity on [$TARGET_BRANCH]. Deploying with PROD config..." >> "$LOG_FILE"
    
    CHANGED_FILES=$(git diff --name-only HEAD "origin/$TARGET_BRANCH")
    
    # 4. FORCE SYNC (Self-Healing)
    echo "[$(date)] Forcing synchronization with origin/$TARGET_BRANCH..." >> "$LOG_FILE"
    git checkout "$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    git reset --hard "origin/$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    
    chmod +x scripts/*.sh backup/*.sh
    
    # 5. AGGRESSIVE RESTART (Always Restart Backend)
    echo "[$(date)] ☕ Rebuilding Backend..." >> "$LOG_FILE"
    
    # Stop/Remove to clear old environment variables
    docker-compose stop backend >> "$LOG_FILE" 2>&1 || true
    docker-compose rm -f -s -v backend >> "$LOG_FILE" 2>&1 || true
    
    # Start with PROD environment keys
    run_secure docker-compose up -d --build --force-recreate backend >> "$LOG_FILE" 2>&1

    # --- CONDITIONAL SERVICES ---
    if echo "$CHANGED_FILES" | grep -qE "^nginx/"; then
        echo "[$(date)] 🌐 Restarting Nginx..." >> "$LOG_FILE"
        docker restart treishvaam-nginx >> "$LOG_FILE" 2>&1
    fi

    if echo "$CHANGED_FILES" | grep -q "config/"; then
        echo "[$(date)] 📊 Restarting Monitoring Stack..." >> "$LOG_FILE"
        docker restart treishvaam-prometheus treishvaam-grafana treishvaam-loki >> "$LOG_FILE" 2>&1
    fi

    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Deployed [$TARGET_BRANCH] Successfully." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi