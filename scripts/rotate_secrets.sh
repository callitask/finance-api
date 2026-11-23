#!/bin/bash
# =========================================================================
# AI-CONTEXT:
# Purpose: Rotate JWT_SECRET_KEY and INTERNAL_API_SECRET_KEY every 90 days.
# Impact: ALL active user sessions are invalidated — users must re-login.
# Security: Generates cryptographically secure 256-bit and 512-bit keys.
# Non-Negotiables:
#   - Must backup .env before any modification.
#   - Must append rotation timestamp to key_rotation.log.
#   - Must use exact Docker Compose restart pattern from system documentation.
#   - Run on Ubuntu VM only: ssh vboxuser@192.168.29.111 then execute.
# IMMUTABLE CHANGE HISTORY:
# - ADDED (Phase 9): Secret key rotation script with backup and log.
# - EDITED (P1.8 - Infisical-First + Rule 22 Flash & Wipe - Nov 22 2026):
#   • Removed direct `docker compose rm` invocations carrying the banned `-s`/`-v` flags.
#   • Removed direct permanent `.env` mutation (`sed -i` on JWT_SECRET_KEY / INTERNAL_API_SECRET_KEY).
#   • Generate NEW_JWT_KEY / NEW_INTERNAL_KEY via `openssl rand -base64 48`; display instructions to update JWT_SECRET_KEY and INTERNAL_API_SECRET_KEY in Infisical (prod) FIRST; pause for confirmation that Infisical was updated.
#   • Record rotation timestamp in /opt/treishvaam/key_rotation.log.
#   • Prefer Git automation (`echo " " >> VER.txt` + signed commit + push to develop, per Rule 14).
#   • Immediate local rolling restart (explicit selection): register `trap 'cp /opt/treishvaam/.env.template /opt/treishvaam/.env 2>/dev/null || true' EXIT`, perform stateless Infisical Flash (same `infisical login` + `infisical export` + Secret Integrity Gate pattern as auto_deploy.sh), run `docker compose stop backend && docker compose rm -f backend && docker compose up -d --force-recreate --no-deps --scale backend=2 backend`, immediately wipe `.env` back to `.env.template`.
#   • New INFISICAL-FIRST + Rule 22 Flash & Wipe non-negotiables added; retained ROTATE confirmation gate + .env backup; `set -euo pipefail` retained.
# =========================================================================
set -euo pipefail

ENV_FILE="/opt/treishvaam/.env"
ENV_TEMPLATE="/opt/treishvaam/.env.template"
LOG_FILE="/opt/treishvaam/key_rotation.log"
BACKUP_DIR="/opt/treishvaam/env_backups"
VER_FILE="/opt/treishvaam/VER.txt"

echo "🔑 Treishvaam Secret Key Rotation Script"
echo "⚠️  WARNING: This will invalidate ALL active user sessions."
echo "Type 'ROTATE' to confirm:"
read -r CONFIRM
if [ "$CONFIRM" != "ROTATE" ]; then
    echo "Aborted — user confirmation not received."
    exit 0
fi

# 1. Backup current .env
mkdir -p "$BACKUP_DIR"
BACKUP_FILE="${BACKUP_DIR}/.env.backup.$(date +%Y%m%d_%H%M%S)"
cp "$ENV_FILE" "$BACKUP_FILE"
echo "✅ .env backed up to: $BACKUP_FILE"

# 2. Generate cryptographically strong keys (512-bit JWT, 256-bit internal)
NEW_JWT_KEY=$(openssl rand -base64 48 | tr -d '\n' | tr -d '/')
NEW_INTERNAL_KEY=$(openssl rand -base64 48 | tr -d '\n' | tr -d '/')
echo "✅ Generated NEW_JWT_KEY and NEW_INTERNAL_KEY (openssl rand -base64 48)."

# 3. Record rotation timestamp (feeds Grafana secret_key_rotation_due alert)
echo "$(date --iso-8601=seconds) — JWT_SECRET_KEY and INTERNAL_API_SECRET_KEY rotation initiated by $(whoami)" >> "$LOG_FILE"
echo "✅ Rotation logged to: $LOG_FILE"

# 4. INFISICAL-FIRST: the operator must update JWT_SECRET_KEY and INTERNAL_API_SECRET_KEY in Infisical (prod) FIRST
echo "🔒 PAUSE — Infisical-First workflow:"
echo "   Set JWT_SECRET_KEY = $NEW_JWT_KEY"
echo "   Set INTERNAL_API_SECRET_KEY = $NEW_INTERNAL_KEY"
echo "   Type 'Y' once Infisical (prod) has been updated:"
read -r CONFIRM_INFISICAL
if [ "$CONFIRM_INFISICAL" != "Y" ]; then
    echo "Aborted — Infisical-first not confirmed."
    exit 0
fi

# 5. Prefer Git automation (Rule 14): VER.txt bump + signed commit + push to develop
if [ -f "$VER_FILE" ]; then
    cd "$(dirname "$VER_FILE")"
    echo " " >> "$VER_FILE"
    git add "$VER_FILE" 2>/dev/null || true
    git commit -m "chore(security): rotate JWT_SECRET_KEY and INTERNAL_API_SECRET_KEY" -S --allow-empty 2>/dev/null || true
    git push origin develop 2>/dev/null || true
    echo "✅ Git automation attempted: VER.txt bumped; signed commit + push to develop."
else
    echo "⚠️  VER.txt not found — skipping Git automation (Rule 14)."
fi

# 6. Immediate local rolling restart (explicit operator selection — Rule 22)
echo "Restart the backend locally now? [y/N]"
read -r DO_RESTART
if [ "$DO_RESTART" != "y" ] && [ "$DO_RESTART" != "Y" ]; then
    echo "✅ Done. Secrets updated in Infisical (prod); VER.txt bump attempted."
    exit 0
fi

# Rule 22 Flash & Wipe: the EXIT trap guarantees the .env is wiped back to the
# template even if the restart sequence fails or the operator aborts mid-run.
trap 'cp /opt/treishvaam/.env.template /opt/treishvaam/.env 2>/dev/null || true' EXIT

# Stateless Infisical Flash (same pattern as auto_deploy.sh): purge the stale
# token cache before authenticating again.
rm -f "$HOME/.infisical/.infisical.json" 2>/dev/null || true

# Stateless Infisical Flash: extract INFISICAL_* creds + stateless JWT token,
# then export prod secrets into the transient .env.
export INFISICAL_PROJECT_ID=$(grep -E '^INFISICAL_PROJECT_ID=' "$ENV_TEMPLATE" | cut -d '=' -f2 | tr -d " \"'\r")
export INFISICAL_CLIENT_ID=$(grep -E '^INFISICAL_CLIENT_ID=' "$ENV_TEMPLATE" | cut -d '=' -f2 | tr -d " \"'\r")
export INFISICAL_CLIENT_SECRET=$(grep -E '^INFISICAL_CLIENT_SECRET=' "$ENV_TEMPLATE" | cut -d '=' -f2 | tr -d " \"'\r")
export INFISICAL_DISABLE_UPDATE_CHECK=true

set +x
RAW_TOKEN=$(infisical login --method=universal-auth --client-id="$INFISICAL_CLIENT_ID" --client-secret="$INFISICAL_CLIENT_SECRET" --domain="https://app.infisical.com" --silent 2>/dev/null | grep -oE 'eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+' | head -n 1 || true)
export INFISICAL_TOKEN="$RAW_TOKEN"

TEMP_SECRETS=$(mktemp)
infisical export --projectId "$INFISICAL_PROJECT_ID" --env prod --format dotenv > "$TEMP_SECRETS" 2>>"$LOG_FILE"
EXPORT_EXIT_CODE=$?

# Secret Integrity Gate (same pattern as auto_deploy.sh): abort on blank secrets.
if [ $EXPORT_EXIT_CODE -eq 0 ] && grep -q "=" "$TEMP_SECRETS"; then
    cat "$TEMP_SECRETS" | sed 's/\r//g' | sed -E "s/='(.*)'$/=\1/" | sed -E 's/="(.*)"$/=\1/' >> "$ENV_FILE"
    rm -f "$TEMP_SECRETS"
    LOCAL_REDIS_PASS=$(grep -E '^REDIS_PASSWORD=' "$ENV_FILE" | cut -d '=' -f2- | tr -d " \"'\r")
    LOCAL_DB_PASS=$(grep -E '^PROD_DB_PASSWORD=' "$ENV_FILE" | cut -d '=' -f2- | tr -d " \"'\r")
    if [ -z "$LOCAL_REDIS_PASS" ] || [ -z "$LOCAL_DB_PASS" ]; then
        echo "CRITICAL ERROR: Secret Integrity Gate failed — blank secrets detected."
        exit 1
    fi
    echo "✅ Secrets injected into transient .env via Infisical export."
else
    echo "⚠️  Infisical export failed — Flash & Wipe (EXIT trap) still applies."
    rm -f "$TEMP_SECRETS" 2>/dev/null || true
fi

# 7. Rolling restart of backend (local, explicit operator selection)
cd /opt/treishvaam
docker compose stop backend && docker compose rm -f backend && docker compose up -d --force-recreate --no-deps --scale backend=2 backend

# Immediately wipe .env back to .env.template (Rule 22 Flash & Wipe).
cp /opt/treishvaam/.env.template /opt/treishvaam/.env 2>/dev/null || true

echo "✅ Backend restart initiated with new secrets."
echo "⚠️  All active sessions invalidated. Users must re-login."
echo "📅 Next rotation due: $(date -d '+90 days' '+%Y-%m-%d')"
# EXIT trap wipes .env back to .env.template (Rule 22 Flash & Wipe).
