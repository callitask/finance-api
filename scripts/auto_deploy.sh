#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# STRATEGY: Wrapper Method (Matches Manual Deployment)

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"

# --- 1. IDENTITY & PERMISSIONS CHECK ---
if [ "$(id -u)" -eq 0 ]; then
    # Switch to vboxuser and ensure a login shell to load PATHs
    exec su - vboxuser -c "$0"
    exit
fi

# --- 2. LOGGING SETUP ---
exec > >(tee -a "$LOG_FILE") 2>&1
cd "$PROJECT_DIR" || { echo "❌ Critical: Project directory not found!"; exit 1; }

# --- 3. GIT RACE CHECK ---
git fetch --all -q
TS_MAIN=$(git show -s --format=%ct origin/main)
TS_DEVELOP=$(git show -s --format=%ct origin/develop)

TARGET_BRANCH="main"
if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

CURRENT_HASH=$(git rev-parse HEAD)
TARGET_HASH=$(git rev-parse "origin/$TARGET_BRANCH")

if [ "$CURRENT_HASH" != "$TARGET_HASH" ]; then
    echo "----------------------------------------------------------------"
    echo "[$(date)] 🚀 Update detected on $TARGET_BRANCH! Syncing..."
    git checkout "$TARGET_BRANCH"
    git reset --hard "origin/$TARGET_BRANCH"
    
    # SELF-HEALING: Fix Windows line endings
    echo "🔧 Sanitizing script formats..."
    find . -name "*.sh" -type f -exec sed -i 's/\r$//' {} +
    chmod +x scripts/*.sh
else
    # EXIT if no changes (Prevents restart loop)
    exit 0
fi

# ==============================================================================
# DEPLOYMENT LOGIC (Wrapper Method)
# ==============================================================================

echo "🔐 Authenticating..."

if [ ! -f "$ENV_FILE" ]; then
    echo "❌ Error: Identity file .env not found!"
    exit 1
fi

# 1. SAFELY LOAD IDENTITY (Client ID/Secret)
# 'set -a' automatically exports all variables defined in the source
set -a
source "$ENV_FILE"
set +a

# 2. Ensure PATH includes Infisical
export PATH=$PATH:/usr/local/bin:/usr/bin

echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend (Infisical Wrapper)..."

# 3. RUN WITH WRAPPER (Exact match to manual command)
# This injects secrets directly into the process memory, avoiding file parsing issues.
infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- docker-compose up -d --force-recreate backend

# Prune old images
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"
