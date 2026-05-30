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
# Export secrets in dotenv format.
# We no longer pipe through sed. Infisical's single quotes protect special characters like '#' and '$' from Docker Compose parsing truncation.
infisical export --env=prod --format=dotenv >> $ENV_FILE

echo "✅ Secrets loaded successfully into $ENV_FILE."
echo "🚀 Ready to start backend: docker compose up -d"