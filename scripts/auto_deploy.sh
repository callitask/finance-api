#!/bin/bash
# ----------------------------------------------------------------------
# FINAL ENTERPRISE DEPLOY (USER-CONTEXT EDITION)
# Purpose: 
#   1. Detects if running as root.
#   2. Switches to 'vboxuser' to match manual success EXACTLY.
#   3. Deploys with PROD secrets using your exact manual command logic.
# ----------------------------------------------------------------------

# --- 1. SELF-CORRECTION: DROP PRIVILEGES ---
# If this script is run as root (e.g., via Cron/Sudo), re-run it as 'vboxuser'
if [ "$(id -u)" -eq 0 ]; then
    echo "[$(date)] ⚠️ Running as root. Switching to 'vboxuser' for consistent environment..." >> /var/log/treishvaam_deploy.log
    # Pass the script execution to vboxuser with a clean login shell
    su - vboxuser -c "/opt/treishvaam/scripts/auto_deploy.sh"
    exit 0
fi

# ======================================================================
#  BELOW THIS LINE RUNS AS 'vboxuser' (Just like manual mode)
# ======================================================================

# 2. SETUP ENVIRONMENT
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

# Ensure we are in the right place
cd "$PROJECT_DIR" || exit 1

# Load Environment Variables (Exact method from your manual command)
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# 3. FETCH LATEST CODE
git fetch --all >> "$LOG_FILE" 2>&1

TS_MAIN=$(git log -1 --format=%ct origin/main 2>/dev/null || echo 0)
TS_DEVELOP=$(git log -1 --format=%ct origin/develop 2>/dev/null || echo 0)

TARGET_BRANCH="main"
if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

# 4. CHECK FOR CHANGES
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New activity on [$TARGET_BRANCH]. Deploying as user: $(whoami)..." >> "$LOG_FILE"
    
    CHANGED_FILES=$(git diff --name-only HEAD "origin/$TARGET_BRANCH")
    
    # 5. FORCE SYNC
    echo "[$(date)] Forcing synchronization with origin/$TARGET_BRANCH..." >> "$LOG_FILE"
    git checkout "$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    git reset --hard "origin/$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    
    chmod +x scripts/*.sh backup/*.sh
    
    # 6. RESTART BACKEND (Exact Manual Command Logic)
    echo "[$(date)] ☕ Restarting Backend (Manual-Mode Logic)..." >> "$LOG_FILE"
    
    # Clean up old state first
    docker-compose stop backend >> "$LOG_FILE" 2>&1 || true
    docker-compose rm -f -s -v backend >> "$LOG_FILE" 2>&1 || true
    
    # EXECUTE THE "MAGIC COMMAND"
    # We check for infisical existence, then run it exactly as you do
    if command -v infisical &> /dev/null; then
        echo "[$(date)] 🔐 Injecting 'prod' secrets via Infisical..." >> "$LOG_FILE"
        infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- docker-compose up -d --force-recreate backend >> "$LOG_FILE" 2>&1
    else
        echo "[$(date)] ❌ CRITICAL: Infisical not found in user path." >> "$LOG_FILE"
        docker-compose up -d --force-recreate backend >> "$LOG_FILE" 2>&1
    fi

    # --- CONDITIONAL SERVICES ---
    if echo "$CHANGED_FILES" | grep -qE "^nginx/"; then
        docker restart treishvaam-nginx >> "$LOG_FILE" 2>&1
    fi

    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Deployed [$TARGET_BRANCH] Successfully." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi