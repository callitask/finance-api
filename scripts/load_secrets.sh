#!/bin/bash
# ===================================================
# TREISHVAAM FINANCE - SECURE SECRET LOADER (INFISICAL)
# Environment: Linux / Docker Compose
# ===================================================

# /**
#  * AI-CONTEXT:
#  * #  * Purpose:
#  * - Pulls secrets securely from Infisical and populates the .env file for Docker Compose.
#  * #  * Scope:
#  * - Responsible for authenticating with Infisical, exporting variables, and sanitizing output.
#  * - Must ensure the final `.env` file does NOT contain single quotes, as Docker Compose will interpret them literally, causing JDBC URL errors.
#  * #  * Critical Dependencies:
#  * - Backend: Docker Compose expects a clean, quote-free `.env` file.
#  * - Infrastructure: Relies on `infisical` CLI being installed and configured.
#  * #  * Security Constraints:
#  * - Must never output secrets to standard output or logs.
#  * - The `.env` file must be generated locally and never checked into version control.
#  * #  * Non-Negotiables:
#  * - The `sed` sanitization step is mandatory to prevent Spring Boot connection failures.
#  * #  * Change Intent:
#  * - Added a `sed` command to strip literal single and double quotes from the Infisical output before it is written to the `.env` file.
#  * #  * Future AI Guidance:
#  * - Do not remove the `sed` sanitization step.
#  * #  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - ADDED:
#  * • `sed` command to remove quotes from Infisical output.
#  * • Why: Docker Compose does not strip quotes from `.env` values, causing Spring Boot to receive literal quotes around the JDBC URL, leading to a "Failed to determine suitable jdbc url" error.
#  * • Date: April 20, 2026.
#  * #  * - DO-NOT-DELETE RULE:
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
# Export secrets in dotenv format, but Infisical wraps values in single quotes.
# We pipe the output through sed to remove all single and double quotes.
infisical export --env=prod --format=dotenv | sed "s/['\"]//g" >> $ENV_FILE

echo "✅ Secrets loaded successfully into $ENV_FILE."
echo "🧹 Sanitization complete: Quotes removed to satisfy Docker Compose parser."
echo "🚀 Ready to start backend: docker compose up -d"