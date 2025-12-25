#!/bin/bash

# ==============================================================================
# TREISHVAAM FINANCE - AUTO DEPLOYMENT SCRIPT
# ==============================================================================
# Role: Updates code, fetches secrets (Infisical), and restarts Docker services.
# Strategy: "Template & Append" to prevent secret loss during failures.
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
# We generally assume the user owns their own files. 
# We skip 'sudo chown' to avoid password prompts. 
# If permissions are broken, run 'sudo chown -R vboxuser:vboxuser .' manually ONCE.
echo "[1/6] Ensuring file ownership..."
# No-op: We rely on the user already being the owner.

# --- 2. Update Codebase ---
echo "[2/6] Pulling latest code from Git..."
git reset --hard
git pull origin main

if [ $? -ne 0 ]; then
    echo "CRITICAL: Git pull failed. Aborting."
    exit 1
fi

chmod +x scripts/auto_deploy.sh

# --- 3. Prepare Environment (.env) ---
echo "[3/6] Preparing environment variables..."

if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "CRITICAL: $TEMPLATE_FILE missing! Cannot restore Auth Keys."
    exit 1
fi

# Reset .env from Template
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > Restored .env from template (Auth keys loaded)."

# Load Auth Keys
set -a
source "$ENV_FILE"
set +a

# --- 4. Fetch Secrets (Infisical) ---
echo "[4/6] Fetching secrets from Infisical..."

if infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv >> "$ENV_FILE"; then
    echo "  > Secrets successfully injected into $ENV_FILE."
else
    echo "CRITICAL: Infisical fetch failed. Proceeding with caution..."
fi

# --- 5. Restart Services ---
echo "[5/6] Restarting Docker Services..."

# REMOVED 'sudo': User must be in 'docker' group
docker compose down --remove-orphans
docker compose up -d --build --force-recreate

# --- 6. Verification ---
echo "[6/6] Verifying deployment..."
sleep 15
if docker ps | grep -q "finance-api"; then
    echo "SUCCESS: Backend container is running."
    echo "DEPLOYMENT COMPLETE: $(date)"
else
    echo "WARNING: Backend container is NOT running. Check 'docker logs finance-api'."
    exit 1
fi