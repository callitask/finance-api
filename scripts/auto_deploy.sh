#!/bin/bash
# ----------------------------------------------------------------------
# AUTO-DEPLOYMENT SCRIPT
# Purpose: Checks for Git updates, rebuilds Docker container, and restarts.
# Run Frequency: Every minute (via Cron)
# ----------------------------------------------------------------------

PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

# Navigate to project directory
cd "$PROJECT_DIR" || exit 1

# 1. Fetch latest changes from Git (Silent check)
git fetch origin main

# 2. Compare Local Version vs Remote Version
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/main)

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New code detected. Starting deployment sequence..." >> "$LOG_FILE"
    
    # 3. Pull the new code
    echo "[$(date)] Pulling from Git..." >> "$LOG_FILE"
    git pull origin main >> "$LOG_FILE" 2>&1
    
    # 4. Ensure scripts are executable (in case of permission loss)
    chmod +x scripts/*.sh

    # 5. Rebuild and Restart Backend
    # --build: Forces Docker to re-run the Multi-Stage build (compiling new Java code)
    echo "[$(date)] 🏗️  Rebuilding Backend Container (This handles Maven build)..." >> "$LOG_FILE"
    docker-compose up -d --build backend >> "$LOG_FILE" 2>&1
    
    # 6. Restart Nginx (to apply any config changes)
    echo "[$(date)] 🔄 Restarting Nginx Proxy..." >> "$LOG_FILE"
    docker restart treishvaam-nginx >> "$LOG_FILE" 2>&1
    
    # 7. Cleanup (Remove old images to save disk space)
    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Deployment Complete. System is live." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi