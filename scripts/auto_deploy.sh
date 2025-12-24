#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# 1. Checks Git for updates (Main vs Develop)
# 2. Syncs ALL files (Nginx, Scripts, Configs, Backend)
# 3. Executes the "Manual Simulation" command for zero-config startup
# 4. Prevents restart loops by checking commit hashes

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"

# --- 1. IDENTITY & PERMISSIONS CHECK ---
# We must run as 'vboxuser' to access Docker socket and Infisical Token
if [ "$(id -u)" -eq 0 ]; then
    # Use 'su -' to simulate a full login shell (sets PATH correctly)
    exec su - vboxuser -c "$0"
    exit
fi

# --- 2. LOGGING SETUP ---
exec > >(tee -a "$LOG_FILE") 2>&1

cd "$PROJECT_DIR" || { echo "❌ Critical: Project directory not found!"; exit 1; }

# --- 3. GIT RACE CHECK (Main vs Develop) ---
# Fetch quietly to update remote references
git fetch --all -q

# Get timestamps of latest commits
TS_MAIN=$(git show -s --format=%ct origin/main)
TS_DEVELOP=$(git show -s --format=%ct origin/develop)

TARGET_BRANCH="main"
if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

# Reset to the winning branch
CURRENT_HASH=$(git rev-parse HEAD)
TARGET_HASH=$(git rev-parse "origin/$TARGET_BRANCH")

if [ "$CURRENT_HASH" != "$TARGET_HASH" ]; then
    echo "----------------------------------------------------------------"
    echo "[$(date)] 🚀 Update detected on $TARGET_BRANCH! Starting Deployment..."
    echo "🔄 Syncing files (Nginx, Scripts, App)..."
    
    # Force sync to match remote exactly
    git checkout "$TARGET_BRANCH"
    git reset --hard "origin/$TARGET_BRANCH"
    
    # Ensure scripts are executable after pull
    chmod +x scripts/*.sh
else
    # CRITICAL: Stop here if no changes to prevent restart loops
    exit 0
fi

# ==============================================================================
# DEPLOYMENT LOGIC (Runs only if update detected)
# ==============================================================================

echo "🔐 Preparing Runtime Environment..."

# 1. Load Machine Identity (Client ID/Secret)
if [ -f "$ENV_FILE" ]; then
    export $(grep -v '^#' "$ENV_FILE" | xargs)
else
    echo "❌ Error: Identity file .env not found!"
    exit 1
fi

# 2. Ensure PATH includes Infisical (Critical for Cron/Script execution)
export PATH=$PATH:/usr/local/bin:/usr/bin

# 3. Check Database Health before restart
echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Infrastructure & Backend (Infisical Wrapper)..."

# 4. EXECUTE THE PROVEN MANUAL COMMAND
# - Updates Nginx, Tunnel, and Backend configurations if files changed
# - Injects secrets via Memory (No bridge file)
# - --force-recreate ensures containers pick up the new JAR/Config
infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- docker-compose up -d --force-recreate

# Prune old images to save disk space
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"