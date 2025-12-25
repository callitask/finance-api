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

# --- 1. Fix Permissions (Crucial Step) ---
# Ensure vboxuser owns the directory to prevent permission lockouts from previous sudo runs
echo "[1/6] Fixing permissions..."
sudo chown -R $APP_USER:$APP_USER "$PROJECT_DIR"

# --- 2. Update Codebase ---
echo "[2/6] Pulling latest code from Git..."
git reset --hard
git pull origin main

if [ $? -ne 0 ]; then
    echo "CRITICAL: Git pull failed. Aborting."
    exit 1
fi

# Make sure this script is executable for next time
chmod +x scripts/auto_deploy.sh

# --- 3. Prepare Environment (.env) ---
echo "[3/6] Preparing environment variables..."

# Check if template exists (The source of truth for Auth Keys)
if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "CRITICAL: $TEMPLATE_FILE missing! Cannot restore Auth Keys."
    echo "ACTION REQUIRED: Create .env.template with INFISICAL_CLIENT_ID/SECRET manually."
    exit 1
fi

# Reset .env from Template (This restores the Infisical Auth Keys)
cp "$TEMPLATE_FILE" "$ENV_FILE"
echo "  > Restored .env from template (Auth keys loaded)."

# Load Auth Keys into Shell for Infisical CLI
set -a
source "$ENV_FILE"
set +a

# --- 4. Fetch Secrets (Infisical) ---
echo "[4/6] Fetching secrets from Infisical..."

# Append secrets to .env
# We use >> to append, keeping the Auth Keys at the top
if infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv >> "$ENV_FILE"; then
    echo "  > Secrets successfully injected into $ENV_FILE."
else
    echo "CRITICAL: Infisical fetch failed. Check Client ID/Secret."
    # We do NOT exit here. We try to deploy. 
    # If secrets are missing, Spring Boot will fail fast, which is better than hanging.
fi

# --- 5. Restart Services ---
echo "[5/6] Restarting Docker Services..."

# Force recreation of containers to pick up new Env Vars and Code
# We use 'sudo' for Docker, but we point to the .env file we just built
sudo docker compose down --remove-orphans
sudo docker compose up -d --build --force-recreate

# --- 6. Verification ---
echo "[6/6] Verifying deployment..."
sleep 10
if sudo docker ps | grep -q "finance-api"; then
    echo "SUCCESS: Backend container is running."
    echo "DEPLOYMENT COMPLETE: $(date)"
else
    echo "WARNING: Backend container is NOT running. Check 'docker logs finance-api'."
    exit 1
fi