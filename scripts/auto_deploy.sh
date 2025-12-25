#!/bin/bash

# ==============================================================================
# TREISHVAAM FINANCE - AUTO DEPLOYMENT SCRIPT
# ==============================================================================
# Role: Updates code, fetches secrets (Infisical), and restarts Docker services.
# Strategy: "Flash & Wipe" (Secrets exist on disk only during startup).
# ==============================================================================

# --- Configuration ---
PROJECT_DIR="/opt/treishvaam"
ENV_FILE=".env"
TEMPLATE_FILE=".env.template"
LOG_FILE="deploy.log"
APP_USER="vboxuser"

# Ensure we are in the project directory
cd "$PROJECT_DIR" || { echo "CRITICAL: Could not find project directory $PROJECT_DIR"; exit 1; }

# Start Logging
exec > >(tee -a "$LOG_FILE") 2>&1
echo "=========================================="
echo "DEPLOYMENT STARTED: $(date)"
echo "=========================================="

# --- 1. Fix Permissions ---
echo "[1/7] Ensuring file ownership (Skipped sudo)..."
# sudo chown -R $APP_USER:$APP_USER "$PROJECT_DIR"

# --- 2. Update Codebase ---
echo "[2/7] Pulling latest code from Git..."

# DYNAMIC BRANCH DETECTION
# This ensures we pull updates for the branch currently checked out (e.g., develop), not just main.
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "  > Detected active branch: $CURRENT_BRANCH"

git reset --hard
git pull origin "$CURRENT_BRANCH"

if [ $? -ne 0 ]; then
    echo "CRITICAL: Git pull failed. Aborting."
    exit 1
fi

chmod +x scripts/auto_deploy.sh
chmod +x scripts/load_secrets.sh

# --- 3. Prepare Environment (.env) ---
echo "[3/7] Preparing environment variables..."

if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "CRITICAL: $TEMPLATE_FILE missing! Cannot restore Auth Keys."
    exit 1
fi

# Reset .env from Template (This restores the Infisical Auth Keys)
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > Restored .env from template (Auth keys loaded)."

# Load Auth Keys into Shell
set -a
source "$ENV_FILE"
set +a

# --- 4. Fetch Secrets (Infisical) ---
echo "[4/7] Fetching secrets from Infisical..."

if infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv >> "$ENV_FILE"; then
    echo "  > Secrets successfully injected into $ENV_FILE."
else
    echo "CRITICAL: Infisical fetch failed."
    # We proceed anyway, relying on metadata if restart, but likely will fail if new vars needed
fi

# --- 5. Restart Services ---
echo "[5/7] Restarting Docker Services..."

# Docker reads .env HERE and bakes it into container metadata
docker compose down --remove-orphans
docker compose up -d --build --force-recreate

# --- 6. SECURITY WIPE (The "Flash" Step) ---
echo "[6/7] Securing Environment..."
# We overwrite .env with the template again, removing all actual secrets
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > SECURE WIPE: Secrets removed from disk. .env now contains only Auth Keys."

# --- 7. Verification ---
echo "[7/7] Verifying deployment..."
sleep 15
if docker ps | grep -q "finance-api"; then
    echo "SUCCESS: Backend container is running."
    echo "DEPLOYMENT COMPLETE: $(date)"
else
    echo "WARNING: Backend container is NOT running. Check 'docker logs finance-api'."
    exit 1
fi