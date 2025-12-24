#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# 1. Checks Git for updates (Main vs Develop)
# 2. Compiles WAR via Runner (handled externally)
# 3. Injects Secrets via Secure Bridge
# 4. Restarts Docker Services ONLY if changes detected

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"
BRIDGE_FILE="$PROJECT_DIR/.env.bridge"

# --- 1. IDENTITY & PERMISSIONS CHECK ---
# Ensure we are running as 'vboxuser' to access Docker socket
if [ "$(id -u)" -eq 0 ]; then
    # Only log if we are actually switching user to prevent log spam
    # exec su vboxuser -c "$0"
    exec su vboxuser -c "$0"
    exit
fi

# --- 2. LOGGING SETUP ---
exec > >(tee -a "$LOG_FILE") 2>&1

cd "$PROJECT_DIR" || { echo "❌ Critical: Project directory not found!"; exit 1; }

# --- 3. SECURITY: TRAP & CLEANUP ---
# Ensure the bridge file is ALWAYS deleted, even if script crashes
cleanup_secrets() {
    if [ -f "$BRIDGE_FILE" ]; then
        # Silent cleanup to reduce log noise
        rm -f "$BRIDGE_FILE"
    fi
}
trap cleanup_secrets EXIT

# --- 4. GIT RACE CHECK (Main vs Develop) ---
# Fetch quietly
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
    echo "🔄 Syncing to $TARGET_BRANCH..."
    git checkout "$TARGET_BRANCH"
    git reset --hard "origin/$TARGET_BRANCH"
else
    # CRITICAL FIX: STOP HERE if no changes.
    # This prevents the "Restart Loop" running every minute.
    exit 0
fi

# ==============================================================================
#DEPLOYMENT LOGIC (Only runs if we did NOT exit above)
# ==============================================================================

# --- 5. SECURE BRIDGE INJECTION ---
echo "🔐 Authenticating with Infisical..."

# Load Machine Identity (Client ID/Secret)
if [ -f "$ENV_FILE" ]; then
    export $(grep -v '^#' "$ENV_FILE" | xargs)
else
    echo "❌ Error: Identity file .env not found!"
    exit 1
fi

# EXPORT secrets to the temporary bridge file
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$BRIDGE_FILE"

# Security Lock: Ensure only this user can read the file
chmod 600 "$BRIDGE_FILE"

if [ ! -s "$BRIDGE_FILE" ]; then
    echo "❌ Critical: Infisical failed to export secrets. Bridge file is empty."
    exit 1
fi

# --- 6. HEALTH CHECK & RESTART ---
echo "⏳ Checking Database Health..."
# Ensure DB is up before backend tries to connect
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend with Injected Secrets..."

# Use the Bridge File explicitly
docker-compose --env-file "$BRIDGE_FILE" up -d --force-recreate backend

# Prune old images to save disk space
docker image prune -f

echo "[$(date)] ✅ Deployment Complete. Secrets Wiped."
echo "----------------------------------------------------------------"