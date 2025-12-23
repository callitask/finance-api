#!/bin/bash
# ----------------------------------------------------------------------
# SMART AUTO-DEPLOYMENT SCRIPT (ENTERPRISE EDITION)
# Purpose: Checks for Git updates and performs SECURE, STATE-SAFE restarts.
# Security: Uses Infisical Orchestrator Injection (Zero-Secrets-on-Disk).
# Run Frequency: Every minute (via Cron)
# ----------------------------------------------------------------------

PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

# Navigate to project directory
cd "$PROJECT_DIR" || exit 1

# --- SECURITY: LOAD INFISICAL AUTH TOKENS ---
# We load the Machine Identity (Client ID/Secret) from .env
# This DOES NOT load the DB passwords (they are not in the file anymore)
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# --- HELPER: SECURE EXECUTION WRAPPER ---
# Wraps docker-compose commands with Infisical to inject secrets in-memory
run_secure() {
    # Check if Infisical CLI is installed
    if command -v infisical &> /dev/null; then
        # Fetch secrets -> Inject to Docker Compose -> Start Containers
        infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- "$@"
    else
        echo "[WARN] Infisical CLI not found. Falling back to standard execution." >> "$LOG_FILE"
        "$@"
    fi
}

# 1. Fetch latest changes from Git
git fetch origin main

# 2. Compare Local Version vs Remote Version
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/main)

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New code detected. Starting Enterprise Secure Deploy..." >> "$LOG_FILE"
    
    # Store the list of changed files
    CHANGED_FILES=$(git diff --name-only HEAD origin/main)
    
    # Pull the new code
    echo "[$(date)] Pulling changes..." >> "$LOG_FILE"
    git pull origin main >> "$LOG_FILE" 2>&1
    
    # Ensure scripts are executable
    chmod +x scripts/*.sh backup/*.sh
    
    # --- INTELLIGENT RESTART LOGIC ---

    # 1. Backend Code / Build Config
    if echo "$CHANGED_FILES" | grep -qE "^src/|^pom.xml|^Dockerfile"; then
        echo "[$(date)] ☕ Backend code changed. Performing Robust Rebuild..." >> "$LOG_FILE"
        
        # Force remove old containers to prevent sync errors
        docker-compose stop backend >> "$LOG_FILE" 2>&1 || true
        docker-compose rm -f -s -v backend >> "$LOG_FILE" 2>&1 || true
        
        # SECURE REBUILD: Injects secrets for the build/up process
        run_secure docker-compose up -d --build --force-recreate backend >> "$LOG_FILE" 2>&1
    fi

    # 2. Infrastructure Config (Docker Compose)
    # If the infrastructure definition changes, we must recreate ALL services securely
    if echo "$CHANGED_FILES" | grep -q "docker-compose.yml"; then
        echo "[$(date)] 🏗️ Infrastructure config changed. specific services..." >> "$LOG_FILE"
        # We use run_secure to ensure DB/Keycloak get their passwords
        run_secure docker-compose up -d --remove-orphans >> "$LOG_FILE" 2>&1
    fi

    # 3. Nginx Config
    if echo "$CHANGED_FILES" | grep -qE "^nginx/"; then
        echo "[$(date)] 🌐 Nginx config changed. Restarting Proxy..." >> "$LOG_FILE"
        docker restart treishvaam-nginx >> "$LOG_FILE" 2>&1
    fi

    # 4. Observability Stack (Prometheus/Grafana/Loki)
    if echo "$CHANGED_FILES" | grep -q "config/"; then
        echo "[$(date)] 📊 Observability config changed. Restarting stack..." >> "$LOG_FILE"
        docker restart treishvaam-prometheus treishvaam-grafana treishvaam-loki treishvaam-promtail treishvaam-tempo >> "$LOG_FILE" 2>&1
    fi
    
    # 5. Backup Service
    if echo "$CHANGED_FILES" | grep -q "backup/"; then
        echo "[$(date)] 💾 Backup scripts changed. Rebuilding Backup Service..." >> "$LOG_FILE"
        # Backup service needs DB password, so we use run_secure
        run_secure docker-compose up -d --build backup-service >> "$LOG_FILE" 2>&1
    fi

    # Cleanup unused images
    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Smart Deployment Complete." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi