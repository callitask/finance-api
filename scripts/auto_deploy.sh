#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"

# --- 1. IDENTITY & PERMISSIONS CHECK ---
if [ "$(id -u)" -eq 0 ]; then
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
    
    # --- SELF-HEALING BLOCK ---
    # This automatically removes Windows (\r) line endings from ALL scripts
    # immediately after downloading them.
    echo "🔧 Sanitizing script formats..."
    find . -name "*.sh" -type f -exec sed -i 's/\r$//' {} +
    
    chmod +x scripts/*.sh
else
    exit 0
fi

# ==============================================================================
# DEPLOYMENT LOGIC (Direct Memory Injection)
# ==============================================================================

echo "🔐 Authenticating..."

if [ -f "$ENV_FILE" ]; then
    export $(grep -v '^#' "$ENV_FILE" | xargs)
else
    echo "❌ Error: Identity file .env not found!"
    exit 1
fi

export PATH=$PATH:/usr/local/bin:/usr/bin

echo "📥 Fetching Production Secrets into RAM..."
INFISICAL_DATA=$(infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv)

if [ -z "$INFISICAL_DATA" ]; then
    echo "❌ Critical: Failed to fetch secrets from Infisical!"
    exit 1
fi

export $(echo "$INFISICAL_DATA" | xargs)

echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend..."
docker-compose up -d --force-recreate backend
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"