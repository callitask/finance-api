#!/bin/bash
# ----------------------------------------------------------------------
# SMART AUTO-DEPLOYMENT SCRIPT
# Purpose: Checks for Git updates and only restarts changed services.
# Run Frequency: Every minute (via Cron)
# ----------------------------------------------------------------------

PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

# Navigate to project directory
cd "$PROJECT_DIR" || exit 1

# 1. Fetch latest changes from Git
git fetch origin main

# 2. Compare Local Version vs Remote Version
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/main)

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New code detected. Starting Smart Deploy..." >> "$LOG_FILE"
    
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
        echo "[$(date)] ☕ Backend code changed. Rebuilding Java container..." >> "$LOG_FILE"
        docker-compose up -d --build backend >> "$LOG_FILE" 2>&1
    fi

    # 2. Nginx Config
    if echo "$CHANGED_FILES" | grep -qE "^nginx/"; then
        echo "[$(date)] 🌐 Nginx config changed. Restarting Proxy..." >> "$LOG_FILE"
        docker restart treishvaam-nginx >> "$LOG_FILE" 2>&1
    fi

    # 3. Prometheus Config
    if echo "$CHANGED_FILES" | grep -q "config/prometheus.yml"; then
        echo "[$(date)] 📊 Prometheus config changed. Restarting..." >> "$LOG_FILE"
        docker restart treishvaam-prometheus >> "$LOG_FILE" 2>&1
    fi

    # 4. Grafana Config / Dashboards
    if echo "$CHANGED_FILES" | grep -qE "config/grafana|dashboards/"; then
        echo "[$(date)] 📈 Grafana config/dashboards changed. Restarting..." >> "$LOG_FILE"
        docker restart treishvaam-grafana >> "$LOG_FILE" 2>&1
    fi

    # 5. Loki Config
    if echo "$CHANGED_FILES" | grep -q "config/loki-config.yml"; then
        echo "[$(date)] 🔍 Loki config changed. Restarting..." >> "$LOG_FILE"
        docker restart treishvaam-loki >> "$LOG_FILE" 2>&1
    fi

    # 6. Promtail Config
    if echo "$CHANGED_FILES" | grep -q "config/promtail-config.yml"; then
        echo "[$(date)] 🪵 Promtail config changed. Restarting..." >> "$LOG_FILE"
        docker restart treishvaam-promtail >> "$LOG_FILE" 2>&1
    fi

    # 7. Tempo Config
    if echo "$CHANGED_FILES" | grep -q "config/tempo.yaml"; then
        echo "[$(date)] ⏱️ Tempo config changed. Restarting..." >> "$LOG_FILE"
        docker restart treishvaam-tempo >> "$LOG_FILE" 2>&1
    fi
    
    # 8. Backup Service
    if echo "$CHANGED_FILES" | grep -q "backup/"; then
        echo "[$(date)] 💾 Backup scripts changed. Rebuilding Backup Service..." >> "$LOG_FILE"
        docker-compose up -d --build backup-service >> "$LOG_FILE" 2>&1
    fi

    # Cleanup unused images
    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Smart Deployment Complete." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi