#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# FIX: Physical '.env' Injection.
# We temporarily append secrets to .env so Docker reads them natively.

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"
ENV_BACKUP="$PROJECT_DIR/.env.backup"

# --- 1. IDENTITY CHECK ---
if [ "$(whoami)" != "vboxuser" ]; then
    echo "⚠️ Switching to vboxuser..."
    exec su - vboxuser -c "$0"
    exit
fi

# --- 2. LOGGING SETUP ---
exec > >(tee -a "$LOG_FILE") 2>&1
cd "$PROJECT_DIR" || { echo "❌ Critical: Project directory not found!"; exit 1; }

# --- 3. SECURITY TRAP ---
# RESTORE THE .env FILE NO MATTER WHAT
restore_env() {
    if [ -f "$ENV_BACKUP" ]; then
        echo "🔒 Restoring original .env file..."
        mv "$ENV_BACKUP" "$ENV_FILE"
    fi
}
trap restore_env EXIT

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
    
    # SELF-HEALING
    find . -path ./data -prune -o -name "*.sh" -type f -exec sed -i 's/\r$//' {} +
    chmod +x scripts/*.sh
else
    # EXIT if no changes
    exit 0
fi

# ==============================================================================
# DEPLOYMENT LOGIC (Physical .env Injection)
# ==============================================================================

echo "🔐 Authenticating..."

if [ ! -f "$ENV_FILE" ]; then
    echo "❌ Error: Identity file .env not found!"
    exit 1
fi

# 1. Load Identity for Infisical CLI
set -a
source "$ENV_FILE"
set +a
export PATH=$PATH:/usr/local/bin:/usr/bin

echo "📥 Injecting Secrets into .env..."

# 2. BACKUP EXISTING .ENV
cp "$ENV_FILE" "$ENV_BACKUP"

# 3. APPEND SECRETS TO THE ACTUAL .ENV FILE
# This ensures Docker Compose reads them natively as file variables.
echo "" >> "$ENV_FILE"
echo "# --- DYNAMIC SECRETS (AUTO-INJECTED) ---" >> "$ENV_FILE"
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv >> "$ENV_FILE"

# Validation
if ! grep -q "PROD_DB_URL" "$ENV_FILE"; then
    echo "❌ Critical: Infisical failed to inject secrets into .env!"
    # Restore immediately
    mv "$ENV_BACKUP" "$ENV_FILE"
    exit 1
fi

echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend..."

# 4. RUN DOCKER
# Now Docker just reads the local .env file. No magic required.
docker-compose up -d --force-recreate backend

# Prune old images
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"
# Trap will automatically restore the clean .env here