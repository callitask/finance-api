#!/bin/bash
# ----------------------------------------------------------------------
# AGGRESSIVE AUTO-DEPLOYMENT SCRIPT (ALWAYS RESTART EDITION)
# Purpose: Automatically deploys AND RESTARTS on ANY git push.
# Security: Uses Infisical Orchestrator Injection.
# Resilience: Uses 'git reset --hard' to FORCE updates.
# ----------------------------------------------------------------------

# 1. FIX PATH & ENVIRONMENT
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

cd "$PROJECT_DIR" || exit 1

# --- SECURITY: LOAD ENV VARS ROBUSTLY ---
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# --- HELPER: SECURE EXECUTION WRAPPER ---
run_secure() {
    INFISICAL_CMD="infisical"
    if [ -f "/usr/local/bin/infisical" ]; then INFISICAL_CMD="/usr/local/bin/infisical"; fi
    if [ -f "/usr/bin/infisical" ]; then INFISICAL_CMD="/usr/bin/infisical"; fi

    if command -v $INFISICAL_CMD &> /dev/null; then
        echo "[$(date)] 🔐 Injecting secrets via Infisical..." >> "$LOG_FILE"
        $INFISICAL_CMD run --projectId "$INFISICAL_PROJECT_ID" --env prod -- "$@"
    else
        echo "[$(date)] ❌ CRITICAL: Infisical not found. Deployment may fail." >> "$LOG_FILE"
        "$@"
    fi
}

# 1. Fetch ALL branches
git fetch --all >> "$LOG_FILE" 2>&1

# 2. Determine Latest Branch
TS_MAIN=$(git log -1 --format=%ct origin/main 2>/dev/null || echo 0)
TS_DEVELOP=$(git log -1 --format=%ct origin/develop 2>/dev/null || echo 0)

TARGET_BRANCH="main"
if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

# 3. Smart Update Logic
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New activity detected on [$TARGET_BRANCH]. Deploying..." >> "$LOG_FILE"
    
    # Capture changed files just for logging
    CHANGED_FILES=$(git diff --name-only HEAD "origin/$TARGET_BRANCH")
    
    # 4. FORCE UPDATE
    echo "[$(date)] Forcing synchronization with origin/$TARGET_BRANCH..." >> "$LOG_FILE"
    git reset --hard "origin/$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    
    chmod +x scripts/*.sh backup/*.sh
    
    # --- ALWAYS RESTART BACKEND (Aggressive Mode) ---
    echo "[$(date)] ☕ Performing Secure Backend Restart..." >> "$LOG_FILE"
    
    # Stop and Remove old container to clear cached environment
    docker-compose stop backend >> "$LOG_FILE" 2>&1 || true
    docker-compose rm -f -s -v backend >> "$LOG_FILE" 2>&1 || true
    
    # Secure Start
    run_secure docker-compose up -d --build --force-recreate backend >> "$LOG_FILE" 2>&1

    # --- CONDITIONAL RESTARTS (Optional services) ---
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