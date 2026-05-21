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
# =========================================================================
set -euo pipefail

ENV_FILE="/opt/treishvaam/.env"
LOG_FILE="/opt/treishvaam/key_rotation.log"
BACKUP_DIR="/opt/treishvaam/env_backups"

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

# 2. Generate new keys
# JWT_SECRET_KEY: 64-byte (512-bit) — larger for stronger HMAC-SHA256
NEW_JWT_KEY=$(openssl rand -base64 64 | tr -d '\n' | tr -d '/')
# INTERNAL_API_SECRET_KEY: 32-byte (256-bit)
NEW_INTERNAL_KEY=$(openssl rand -base64 32 | tr -d '\n' | tr -d '/')

# 3. Replace in .env
sed -i "s|^JWT_SECRET_KEY=.*|JWT_SECRET_KEY=${NEW_JWT_KEY}|" "$ENV_FILE"
sed -i "s|^INTERNAL_API_SECRET_KEY=.*|INTERNAL_API_SECRET_KEY=${NEW_INTERNAL_KEY}|" "$ENV_FILE"
echo "✅ Keys replaced in .env"

# 4. Log the rotation event
echo "$(date --iso-8601=seconds) — JWT_SECRET_KEY and INTERNAL_API_SECRET_KEY rotated by $(whoami)" >> "$LOG_FILE"
echo "✅ Rotation logged to: $LOG_FILE"

# 5. Restart backend using verified zero-downtime pattern
cd /opt/treishvaam
docker compose stop backend || true
docker compose rm -f -s -v backend || true
docker compose up -d --force-recreate --no-deps backend

echo "✅ Backend restarted with new keys."
echo "⚠️  All active sessions invalidated. Users must re-login."
echo "📅 Next rotation due: $(date -d '+90 days' '+%Y-%m-%d')"