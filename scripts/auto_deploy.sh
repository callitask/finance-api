#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# FIX: 'Source-Based' Injection with EXPORT enforcement.

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"
SECRETS_TEMP="$PROJECT_DIR/.secrets.temp"

# --- 1. IDENTITY CHECK ---
# We must run as the correct user (vboxuser)
if [ "$(whoami)" != "vboxuser" ]; then
    echo "⚠️ Switching to vboxuser..."
    exec su - vboxuser -c "$0"
    exit
fi

# --- 2. LOGGING SETUP ---
exec > >(tee -a "$LOG_FILE") 2>&1
cd "$PROJECT_DIR" || { echo "❌ Critical: Project directory not found!"; exit 1; }

# --- 3. SECURITY TRAP ---
cleanup() {
    rm -f "$SECRETS_TEMP"
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
    
    # SELF-HEALING: Fix Windows line endings (Exclude data dirs)
    find . -path ./data -prune -o -name "*.sh" -type f -exec sed -i 's/\r$//' {} +
    chmod +x scripts/*.sh
else
    # EXIT if no changes
    exit 0
fi

# ==============================================================================
# DEPLOYMENT LOGIC
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

# Export Secrets to Temp File
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$SECRETS_TEMP"

if [ ! -s "$SECRETS_TEMP" ]; then
    echo "❌ Critical: Failed to fetch secrets!"
    exit 1
fi

# LOAD SECRETS INTO RAM (CRITICAL STEP)
# set -a ensures that every variable sourced is automatically EXPORTED to child processes (Docker)
set -a
source "$SECRETS_TEMP"
set +a

# Verify injection (Debug Log)
if [ -z "$PROD_DB_URL" ]; then
    echo "❌ Critical: Secrets failed to load into RAM!"
    exit 1
else
    echo "✅ Secrets loaded into RAM successfully."
fi

# Wipe file immediately
rm -f "$SECRETS_TEMP"

echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend..."

# Run Docker Compose
# Since variables are exported above, Docker will pick them up natively.
docker-compose up -d --force-recreate backend

# Prune old images
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"