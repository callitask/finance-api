#!/bin/bash
# ----------------------------------------------------------------------
# FINAL ROBUST DEPLOY (WAIT-FOR-DB + USER CONTEXT)
# Purpose: Ensures DB is ready before backend starts.
#          Replicates manual command exactly as 'vboxuser'.
# ----------------------------------------------------------------------

# --- 1. ROOT CHECK & USER SWITCH ---
if [ "$(id -u)" -eq 0 ]; then
    echo "[$(date)] ⚠️ Running as root. Switching to 'vboxuser'..." >> /var/log/treishvaam_deploy.log
    su vboxuser -c "cd /opt/treishvaam && export \$(grep -v '^#' .env | xargs) && /opt/treishvaam/scripts/auto_deploy.sh"
    exit 0
fi

# ======================================================================
#  RUNNING AS 'vboxuser'
# ======================================================================

PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

cd "$PROJECT_DIR" || exit 1

# 1. LOAD ENV
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
    
    echo "[$(date)] ☕ Restarting Backend (Robust Mode)..." >> "$LOG_FILE"

    # --- ROBUST RESTART LOGIC ---
    
    # 1. Clean up old backend containers (Ignore errors if they don't exist)
    docker-compose stop backend >> "$LOG_FILE" 2>&1 || true
    docker-compose rm -f -s -v backend >> "$LOG_FILE" 2>&1 || true
    
    # 2. Ensure Database is Healthy FIRST
    echo "[$(date)] ⏳ Waiting for Database to be healthy..." >> "$LOG_FILE"
    # This ensures the DB container is actually running and marked healthy
    until [ "`docker inspect -f {{.State.Health.Status}} treishvaam-db`" == "healthy" ]; do
        sleep 2;
        echo -n "." >> "$LOG_FILE";
    done;
    echo " DB is Healthy." >> "$LOG_FILE"

    # 3. Start Backend with Secrets (The Magic Command)
    if command -v infisical &> /dev/null; then
        echo "[$(date)] 🔐 Injecting secrets via Infisical..." >> "$LOG_FILE"
        infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- docker-compose up -d --force-recreate backend >> "$LOG_FILE" 2>&1
    else
        echo "[$(date)] ❌ CRITICAL: Infisical not found." >> "$LOG_FILE"
        /usr/local/bin/infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- docker-compose up -d --force-recreate backend >> "$LOG_FILE" 2>&1
    fi
    
    # Cleanup
    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Deployed Successfully." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi