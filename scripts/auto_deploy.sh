#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# FIX: 'Inline' Command Injection.
# We fetch secrets and pass them directly as env vars to the docker command.

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"

# --- 1. IDENTITY CHECK ---
if [ "$(whoami)" != "vboxuser" ]; then
    echo "⚠️ Switching to vboxuser..."
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
    
    # SELF-HEALING
    find . -path ./data -prune -o -name "*.sh" -type f -exec sed -i 's/\r$//' {} +
    chmod +x scripts/*.sh
else
    # EXIT if no changes
    exit 0
fi

# ==============================================================================
# DEPLOYMENT LOGIC (Inline Command Injection)
# ==============================================================================

echo "🔐 Authenticating..."

if [ ! -f "$ENV_FILE" ]; then
    echo "❌ Error: Identity file .env not found!"
    exit 1
fi

# Load Identity (Client ID)
set -a
source "$ENV_FILE"
set +a
export PATH=$PATH:/usr/local/bin:/usr/bin

echo "📥 Fetching Secrets..."

# Fetch raw secrets in KEY=VALUE format
SECRETS=$(infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv)

if [ -z "$SECRETS" ]; then
    echo "❌ Critical: Failed to fetch secrets from Infisical!"
    exit 1
fi

echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend (Inline Injection)..."

# EXECUTE DOCKER WITH INLINE SECRETS
# This passes the secrets as environment variables *only* for this command.
# 'eval' is used here to parse the newline-separated secrets string correctly into the command environment.
env $(echo "$SECRETS" | xargs) docker-compose up -d --force-recreate backend

# Prune old images
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"