#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# 1. Checks Git for updates
# 2. Syncs files
# 3. DIRECT MEMORY INJECTION (Fixes the wrapper issue)
# 4. Deploys

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"

# --- 1. IDENTITY & PERMISSIONS CHECK ---
if [ "$(id -u)" -eq 0 ]; then
    # Use 'su -' to ensure we get a clean user environment
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
    chmod +x scripts/*.sh
else
    # STOP if no changes (Prevents restart loop)
    exit 0
fi

# ==============================================================================
# DEPLOYMENT LOGIC (Direct Memory Injection)
# ==============================================================================

echo "🔐 Authenticating..."

# 1. Load Identity from .env (Client ID / Secret)
if [ -f "$ENV_FILE" ]; then
    export $(grep -v '^#' "$ENV_FILE" | xargs)
else
    echo "❌ Error: Identity file .env not found!"
    exit 1
fi

# 2. Ensure PATH has Infisical
export PATH=$PATH:/usr/local/bin:/usr/bin

# 3. INJECT SECRETS INTO MEMORY
# We fetch secrets as 'KEY=VALUE' strings and export them to this shell.
# This bypasses the 'infisical run' wrapper which fails in scripts.
echo "📥 Fetching Production Secrets into RAM..."
INFISICAL_DATA=$(infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv)

if [ -z "$INFISICAL_DATA" ]; then
    echo "❌ Critical: Failed to fetch secrets from Infisical!"
    exit 1
fi

# Export all fetched secrets to the current environment
export $(echo "$INFISICAL_DATA" | xargs)

# 4. Check Health
echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend (Direct Env)..."

# 5. RUN DOCKER COMPOSE
# Since we exported the secrets above, docker-compose finds them natively.
docker-compose up -d --force-recreate backend

# Prune old images
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"