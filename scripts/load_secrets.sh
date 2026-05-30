#!/bin/bash
# ===================================================
# TREISHVAAM FINANCE - SECURE SECRET LOADER (INFISICAL)
# Environment: Linux / Docker Compose
# ===================================================

# /**
#  * AI-CONTEXT:
#  *
#  * Purpose:
#  * - Pulls secrets securely from Infisical and populates the .env file for Docker Compose.
#  *
#  * Scope:
#  * - Responsible for authenticating with Infisical, exporting variables, and sanitizing output.
#  * - Must ensure the final `.env` file does NOT contain single quotes, as Docker Compose will interpret them literally, causing JDBC URL errors.
#  *
#  * Critical Dependencies:
#  * - Backend: Docker Compose expects a clean, quote-free `.env` file.
#  * - Infrastructure: Relies on `infisical` CLI being installed and configured.
#  *
#  * Security Constraints:
#  * - Must never output secrets to standard output or logs.
#  * - The `.env` file must be generated locally and never checked into version control.
#  *
#  * Non-Negotiables:
#  * - The `sed` sanitization step is mandatory to prevent Spring Boot connection failures.
#  *
#  * Change Intent:
#  * - Added a `sed` command to strip literal single and double quotes from the Infisical output before it is written to the `.env` file.
#  *
#  * Future AI Guidance:
#  * - Do not remove the `sed` sanitization step.
#  *
#  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - ADDED:
#  * • `sed` command to remove quotes from Infisical output.
#  * • Why: Docker Compose does not strip quotes from `.env` values, causing Spring Boot to receive literal quotes around the JDBC URL, leading to a "Failed to determine suitable jdbc url" error.
#  * • Date: April 20, 2026.
#  *
#  * - EDITED (Quote Sanitization Reversal & YAML Protection):
#  * • Removed `sed "s/['\"]//g"` pipeline completely.
#  * • Why: Stripping single quotes from the `.env` file exposed passwords containing `#` and `$` directly to Docker Compose's YAML parser, which interpreted them as comments/substitutions, permanently truncating `REDIS_PASSWORD` and causing NOAUTH crashes. Docker Compose natively requires secrets to be quoted to protect special characters.
#  *
#  * - EDITED (Dual-Env OS Shadow-Kill & Fail Guard):
#  * • Implemented temporary file for Infisical export with strict `EXIT_CODE` abort guard.
#  * • Added targeted boundary `sed` to generate `.env.backend` and dynamic `unset` loop to destroy OS shadow variables.
#  * • Why: Ensures CI/CD runner empty variables do not override Docker Compose values and protects passwords with internal special characters from truncation.
#  *
#  * - DO-NOT-DELETE RULE:
#  * This IMMUTABLE CHANGE HISTORY section must never be deleted,
#  * truncated, rewritten, or regenerated.
#  * Future AI must append only.
#  */

set -e

ENV_FILE=".env"
TEMPLATE_FILE=".env.template"

echo "🔐 Authenticating with Infisical..."
# Ensure you are logged in via machine identity or user account before running this script
# Example: infisical login --method=universal-auth --client-id=... --client-secret=...

echo "📄 Preparing $ENV_FILE..."
cp $TEMPLATE_FILE $ENV_FILE

echo "📥 Pulling secrets for 'prod' environment..."
TEMP_SECRETS=$(mktemp)
infisical export --env=prod --format=dotenv > "$TEMP_SECRETS"
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ] || [ ! -s "$TEMP_SECRETS" ]; then
    echo "❌ CRITICAL: Infisical fetch failed. Aborting to protect production."
    rm -f "$TEMP_SECRETS"
    exit 1
fi

# Append quoted secrets to .env (Docker Compose YAML interpolation for infra containers)
cat "$TEMP_SECRETS" >> "$ENV_FILE"

# Generate .env.backend: strip ONLY surrounding single quotes (safe for special chars)
sed "s/^\([A-Za-z_][A-Za-z0-9_]*\)='\(.*\)'$/\1=\2/" "$ENV_FILE" > "${ENV_FILE}.backend"

# OS shadow-kill: unset template variables from the active shell before docker compose runs
while IFS= read -r line; do
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ -z "$line" ]] && continue
    var_name=$(echo "$line" | cut -d'=' -f1 | tr -d ' ')
    [[ "$var_name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] && unset "$var_name"
done < "$TEMPLATE_FILE"

rm -f "$TEMP_SECRETS"
echo "✅ Secrets loaded. .env.backend generated. OS shadow overrides neutralised."
echo "🚀 Ready to start backend: docker compose up -d"