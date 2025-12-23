#!/bin/bash
# ----------------------------------------------------------------------
# FINAL SIMPLIFIED DEPLOYMENT (EXACT MANUAL REPLICA)
# Purpose: Replicates your manual terminal command 100% correctly.
# Run Context: This script can be run by Root/Cron, and it will auto-switch
#              to 'vboxuser' to execute the logic.
# ----------------------------------------------------------------------

# --- 1. ROOT CHECK & USER SWITCH ---
# If running as root (Cron/Sudo), switch to 'vboxuser' and run the EXACT command chain.
if [ "$(id -u)" -eq 0 ]; then
    echo "[$(date)] ⚠️ Running as root. Switching to 'vboxuser' to match manual environment..." >> /var/log/treishvaam_deploy.log
    
    # We construct the command string exactly as you type it manually.
    # 1. Go to dir
    # 2. Export .env (to get INFISICAL_PROJECT_ID)
    # 3. Run the Auto Deploy script again (as vboxuser)
    
    su vboxuser -c "cd /opt/treishvaam && export \$(grep -v '^#' .env | xargs) && /opt/treishvaam/scripts/auto_deploy.sh"
    exit 0
fi

# ======================================================================
#  BELOW THIS LINE RUNS AS 'vboxuser' (Your working environment)
# ======================================================================

PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

cd "$PROJECT_DIR" || exit 1

# 1. LOAD ENV (Just to be safe, though the 'su' command above handled it)
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# 2. GIT OPERATIONS
git fetch --all >> "$LOG_FILE" 2>&1

TS_MAIN=$(git log -1 --format=%ct origin/main 2>/dev/null || echo 0)
TS_DEVELOP=$(git log -1 --format=%ct origin/develop 2>/dev/null || echo 0)

TARGET_BRANCH="main"
if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

# 3. EXECUTE DEPLOYMENT
if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New activity on [$TARGET_BRANCH]. Deploying..." >> "$LOG_FILE"
    
    # Git Sync
    git checkout "$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    git reset --hard "origin/$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    chmod +x scripts/*.sh backup/*.sh
    
    echo "[$(date)] ☕ Executing Infisical Start Command..." >> "$LOG_FILE"

    # --- THE MAGIC COMMAND (Exact Replica) ---
    # We use full paths just to be safe, but the logic is identical.
    
    if command -v infisical &> /dev/null; then
        # This is your command:
        infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- docker-compose up -d --force-recreate >> "$LOG_FILE" 2>&1
    else
        # Fallback if infisical isn't in PATH (try /usr/local/bin)
        /usr/local/bin/infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- docker-compose up -d --force-recreate >> "$LOG_FILE" 2>&1
    fi
    
    # Cleanup
    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Deployed Successfully." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi