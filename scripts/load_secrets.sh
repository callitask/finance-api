#!/bin/bash

# ==============================================================================
# TREISHVAAM FINANCE - MANUAL SECRET LOADER
# ==============================================================================
# Role: Manually fetches secrets into .env for debugging/maintenance sessions.
# Usage: ./scripts/load_secrets.sh
# ==============================================================================

PROJECT_DIR="/opt/treishvaam"
ENV_FILE=".env"
TEMPLATE_FILE=".env.template"

echo "=========================================="
echo "MANUAL SECRET LOADER"
echo "=========================================="

cd "$PROJECT_DIR" || exit 1

if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "ERROR: $TEMPLATE_FILE not found. Cannot authenticate with Infisical."
    exit 1
fi

echo "[1/2] Restoring Auth Keys from Template..."
cp "$TEMPLATE_FILE" "$ENV_FILE"

# Load Auth Keys for CLI
set -a
source "$ENV_FILE"
set +a

echo "[2/2] Fetching Secrets from Infisical..."
if infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv >> "$ENV_FILE"; then
    echo "SUCCESS: Secrets loaded into $ENV_FILE"
    echo "---------------------------------------------------"
    echo "WARNING: .env now contains live secrets."
    echo "When finished debugging, delete it or run a deploy."
    echo "---------------------------------------------------"
else
    echo "FAILED: Could not fetch secrets."
    exit 1
fi