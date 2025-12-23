#!/bin/bash
# ----------------------------------------------------------------------
# SMART AUTO-DEPLOYMENT SCRIPT (ENTERPRISE DYNAMIC EDITION)
# Purpose: Automatically deploys the LATEST updated branch (main OR develop).
# Security: Uses Infisical Orchestrator Injection (Zero-Secrets-on-Disk).
# Resilience: Uses 'git reset --hard' to prevent merge conflicts/divergence.
# Run Frequency: Every minute (via Cron)
# ----------------------------------------------------------------------

PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

# Navigate to project directory
cd "$PROJECT_DIR" || exit 1

# --- SECURITY: LOAD INFISICAL AUTH TOKENS ---
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# --- HELPER: SECURE EXECUTION WRAPPER ---
run_secure() {
    if command -v infisical &> /dev/null; then
        infisical run --projectId "$INFISICAL_PROJECT_ID" --env prod -- "$@"
    else
        echo "[WARN] Infisical CLI not found. Falling back to standard execution." >> "$LOG_FILE"
        "$@"
    fi
}

# 1. Fetch ALL branches to get latest info
git fetch --all >> "$LOG_FILE" 2>&1

# 2. Determine which branch is most recently updated
# We get the commit timestamp (%ct) of both remote branches
TS_MAIN=$(git log -1 --format=%ct origin/main 2>/dev/null || echo 0)
TS_DEVELOP=$(git log -1 --format=%ct origin/develop 2>/dev/null || echo 0)

TARGET_BRANCH="main"

if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

# 3. Compare Local State vs Target Remote State
# We compare the current HEAD hash with the remote target branch hash
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New activity detected on [$TARGET_BRANCH]. Starting Secure Deploy..." >> "$LOG_FILE"
    
    # Store the list of changed files between current state and the NEW target
    # This works even if we switch branches (it compares the two trees)
    CHANGED_FILES=$(git diff --name-only HEAD "origin/$TARGET_BRANCH")
    
    # 4. PERFORM UPDATE (Self-Healing)
    # We forcefully checkout and reset to the target remote to avoid "divergent branch" errors
    echo "[$(date)] Switching/Updating to origin/$TARGET_BRANCH..." >> "$LOG_FILE"
    git checkout "$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    git reset --hard "origin/$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    
    # Ensure scripts are executable (in case they were changed)
    chmod +x scripts/*.sh backup/*.sh
    
    # --- INTELLIGENT RESTART LOGIC ---

    # 1. Backend Code / Build Config
    if echo "$CHANGED_FILES" | grep -qE "^src/|^pom.xml|^Dockerfile"; then
        echo "[$(date)] ☕ Backend code changed. Performing Robust Rebuild..." >> "$LOG_FILE"
        
        # Force remove old containers to prevent sync errors
        docker-compose stop backend >> "$LOG_FILE" 2>&1 || true
        docker-compose rm -f -s -v backend >> "$LOG_FILE" 2>&1 || true
        
        # SECURE REBUILD
        run_secure docker-compose up -d --build --force-recreate backend >> "$LOG_FILE" 2>&1
    fi

    # 2. Infrastructure Config (Docker Compose)
    if echo "$CHANGED_FILES" | grep -q "docker-compose.yml"; then
        echo "[$(date)] 🏗️ Infrastructure config changed. Recreating services..." >> "$LOG_FILE"
        run_secure docker-compose up -d --remove-orphans >> "$LOG_FILE" 2>&1
    fi

    # 3. Nginx Config
    if echo "$CHANGED_FILES" | grep -qE "^nginx/"; then
        echo "[$(date)] 🌐 Nginx config changed. Restarting Proxy..." >> "$LOG_FILE"
        docker restart treishvaam-nginx >> "$LOG_FILE" 2>&1
    fi

    # 4. Observability Stack
    if echo "$CHANGED_FILES" | grep -q "config/"; then
        echo "[$(date)] 📊 Observability config changed. Restarting stack..." >> "$LOG_FILE"
        docker restart treishvaam-prometheus treishvaam-grafana treishvaam-loki treishvaam-promtail treishvaam-tempo >> "$LOG_FILE" 2>&1
    fi
    
    # 5. Backup Service
    if echo "$CHANGED_FILES" | grep -q "backup/"; then
        echo "[$(date)] 💾 Backup scripts changed. Rebuilding Backup Service..." >> "$LOG_FILE"
        run_secure docker-compose up -d --build backup-service >> "$LOG_FILE" 2>&1
    fi

    # Cleanup unused images
    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Deployed [$TARGET_BRANCH] Successfully." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi