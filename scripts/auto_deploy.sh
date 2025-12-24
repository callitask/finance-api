#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# FIX: 'Source-Based' Memory Injection.
# We load secrets into Shell RAM using 'source', then run Docker.

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"
SECRETS_TEMP="$PROJECT_DIR/.secrets.temp"

# --- 1. IDENTITY & PERMISSIONS CHECK ---
if [ "$(id -u)" -eq 0 ]; then
    # Switch to vboxuser and ensure a login shell to load PATHs
    exec su - vboxuser -c "$0"
    exit
fi

# --- 2. LOGGING SETUP ---
exec > >(tee -a "$LOG_FILE") 2>&1
cd "$PROJECT_DIR" || { echo "❌ Critical: Project directory not found!"; exit 1; }

# --- 3. SECURITY TRAP ---
# Always wipe the secrets file, even if script crashes
cleanup() {
    if [ -f "$SECRETS_TEMP" ]; then
        rm -f "$SECRETS_TEMP"
    fi
}
trap cleanup EXIT

# --- 4. GIT RACE CHECK ---
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
    # Exclude ./data directory to prevent permission errors
    echo "🔧 Sanitizing script formats..."
    find . -path ./data -prune -o -name "*.sh" -type f -exec sed -i 's/\r$//' {} +
    chmod +x scripts/*.sh
else
    # EXIT if no changes (Prevents restart loop)
    exit 0
fi

# ==============================================================================
# DEPLOYMENT LOGIC (Source-Based Injection)
# ==============================================================================

echo "🔐 Authenticating..."

if [ ! -f "$ENV_FILE" ]; then
    echo "❌ Error: Identity file .env not found!"
    exit 1
fi

# 1. Load Identity (Client ID) using xargs (Safe for simple .env files with comments)
export $(grep -v '^#' "$ENV_FILE" | xargs)
export PATH=$PATH:/usr/local/bin:/usr/bin

echo "📥 Fetching Secrets..."

# 2. Export Secrets to Temp File
# We use 'infisical export' to get a clean KEY=VALUE file.
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$SECRETS_TEMP"

if [ ! -s "$SECRETS_TEMP" ]; then
    echo "❌ Critical: Failed to fetch secrets (File is empty)"
    exit 1
fi

# 3. Load Secrets into RAM (The Magic Step)
# 'set -a' tells bash to export every variable defined in the sourced file
set -a
source "$SECRETS_TEMP"
set +a

# 4. Wipe the file immediately
rm -f "$SECRETS_TEMP"

echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend (RAM Injected)..."

# 5. Run Docker Compose
# It will now find PROD_DB_URL in the environment variables we just sourced.
docker-compose up -d --force-recreate backend

# Prune old images
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"