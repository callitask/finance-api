cat << 'EOF' > /opt/treishvaam/scripts/auto_deploy.sh
#!/bin/bash

# ==========================================
# TREISHVAAM FINANCE - ENTERPRISE AUTO DEPLOY
# ==========================================
# FIX: Uses 'Combined File' injection to prevent xargs corruption.

# --- CONFIGURATION ---
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"
ENV_FILE="$PROJECT_DIR/.env"
TEMP_ENV_FILE="$PROJECT_DIR/.env.temp"

# --- 1. IDENTITY & PERMISSIONS CHECK ---
if [ "$(id -u)" -eq 0 ]; then
    # Restart script as vboxuser with clean environment
    exec su - vboxuser -c "$0"
    exit
fi

# --- 2. LOGGING SETUP ---
exec > >(tee -a "$LOG_FILE") 2>&1
cd "$PROJECT_DIR" || { echo "❌ Critical: Project directory not found!"; exit 1; }

# --- 3. SECURITY TRAP ---
# Ensure temporary file is ALWAYS deleted, even on crash
cleanup() {
    rm -f "$TEMP_ENV_FILE"
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
    echo "🔧 Sanitizing script formats..."
    find . -name "*.sh" -type f -exec sed -i 's/\r$//' {} +
    chmod +x scripts/*.sh
else
    # EXIT if no changes (Prevents restart loop)
    exit 0
fi

# ==============================================================================
# DEPLOYMENT LOGIC (Combined File Injection)
# ==============================================================================

echo "🔐 Authenticating..."

if [ ! -f "$ENV_FILE" ]; then
    echo "❌ Error: Identity file .env not found!"
    exit 1
fi

# Load identity for Infisical command
export $(grep -v '^#' "$ENV_FILE" | xargs)
export PATH=$PATH:/usr/local/bin:/usr/bin

echo "📥 Generatng Combined Config..."

# 1. Start with the static .env (Config)
cat "$ENV_FILE" > "$TEMP_ENV_FILE"
echo "" >> "$TEMP_ENV_FILE" # Ensure newline

# 2. Append secrets from Infisical (Secrets)
# We append directly to file to avoid xargs/shell corruption
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv >> "$TEMP_ENV_FILE"

if [ ! -s "$TEMP_ENV_FILE" ]; then
    echo "❌ Critical: Combined env file is empty!"
    exit 1
fi

# 3. Secure the file
chmod 600 "$TEMP_ENV_FILE"

echo "⏳ Checking Database Health..."
until docker inspect --format '{{.State.Health.Status}}' treishvaam-db | grep -q "healthy"; do
    echo "   ...waiting for DB"
    sleep 3
done

echo "🚀 Restarting Backend (Combined File)..."

# 4. RUN DOCKER COMPOSE
# We use --env-file to provide ALL variables (Config + Secrets) at once.
# This ensures substitution variables like ${PROD_DB_URL} are resolved correctly.
docker-compose --env-file "$TEMP_ENV_FILE" up -d --force-recreate backend

# Prune old images
docker image prune -f

echo "[$(date)] ✅ Deployment Complete."
echo "----------------------------------------------------------------"
EOF