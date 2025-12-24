#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# FIX: 'Brute Force' .env Rewrite.
# We completely rebuild the .env file from Infisical before every deploy.

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
# DEPLOYMENT LOGIC (Brute Force Rewrite)
# ==============================================================================

echo "🔐 Authenticating..."

# We need the Client ID/Secret to talk to Infisical.
# We assume these are ALREADY in the current .env or shell.
# If .env exists, source it strictly for the Infisical CLI auth.
if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
fi

export PATH=$PATH:/usr/local/bin:/usr/bin

echo "📥 Rebuilding .env file from Secrets..."

# 1. FETCH ALL SECRETS (Config + Secrets)
# We fetch the ENTIRE environment from Infisical and overwrite .env
# This ensures .env is always fresh and correct.
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$ENV_FILE"

# 2. VALIDATE
if ! grep -q "PROD_DB_URL" "$ENV_FILE"; then
    echo "❌ Critical: Infisical failed to write secrets to .env!"
    exit 1
fi

echo "✅ .env file rebuilt successfully."

echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend..."

# 3. RUN DOCKER
# Docker will read the freshly written .env file automatically.
docker-compose up -d --force-recreate backend

# Prune old images
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"