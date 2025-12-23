#!/bin/bash
# ----------------------------------------------------------------------
# SMART AUTO-DEPLOYMENT SCRIPT (FIXED PATH EDITION)
# Purpose: Automatically deploys the LATEST updated branch (main OR develop).
# Security: Uses Infisical Orchestrator Injection (Zero-Secrets-on-Disk).
# Resilience: Uses 'git reset --hard' to prevent merge conflicts.
# ----------------------------------------------------------------------

# 1. FIX THE ENVIRONMENT (CRITICAL FOR CRON)
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

# Navigate to project directory
cd "$PROJECT_DIR" || exit 1

# --- SECURITY: LOAD INFISICAL AUTH TOKENS ---
if [ -f .env ]; then
    # Automatically export all variables from .env ignoring comments
    set -a
    source .env
    set +a
fi

# --- HELPER: SECURE EXECUTION WRAPPER ---
run_secure() {
    # Try to find infisical in common locations if not in PATH
    INFISICAL_CMD="infisical"
    if ! command -v infisical &> /dev/null; then
        if [ -f "/usr/local/bin/infisical" ]; then
            INFISICAL_CMD="/usr/local/bin/infisical"
        elif [ -f "/usr/bin/infisical" ]; then
            INFISICAL_CMD="/usr/bin/infisical"
        fi
    fi

    if command -v $INFISICAL_CMD &> /dev/null; then
        # LOG THAT WE ARE USING SECRETS
        echo "[$(date)] 🔐 Injecting secrets via Infisical..." >> "$LOG_FILE"
        $INFISICAL_CMD run --projectId "$INFISICAL_PROJECT_ID" --env prod -- "$@"
    else
        echo "[$(date)] ❌ CRITICAL: Infisical not found in PATH! Secrets will be missing." >> "$LOG_FILE"
        echo "[DEBUG] PATH is: $PATH" >> "$LOG_FILE"
        "$@"
    fi
}

# 1. Fetch ALL branches to get latest info
git fetch --all >> "$LOG_FILE" 2>&1

# 2. Determine which branch is most recently updated
TS_MAIN=$(git log -1 --format=%ct origin/main 2>/dev/null || echo 0)
TS_DEVELOP=$(git log -1 --format=%ct origin/develop 2>/dev/null || echo 0)

TARGET_BRANCH="main"

if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

# 3. Compare Local State vs Target Remote State
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New activity detected on [$TARGET_BRANCH]. Starting Secure Deploy..." >> "$LOG_FILE"
    
    CHANGED_FILES=$(git diff --name-only HEAD "origin/$TARGET_BRANCH")
    
    # 4. PERFORM UPDATE (Self-Healing)
    echo "[$(date)] Switching/Updating to origin/$TARGET_BRANCH..." >> "$LOG_FILE"
    git checkout "$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    git reset --hard "origin/$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    
    chmod +x scripts/*.sh backup/*.sh
    
    # --- INTELLIGENT RESTART LOGIC ---

    # 1. Backend Code / Build Config
    if echo "$CHANGED_FILES" | grep -qE "^src/|^pom.xml|^Dockerfile"; then
        echo "[$(date)] ☕ Backend code changed. Performing Robust Rebuild..." >> "$LOG_FILE"
        docker-compose stop backend >> "$LOG_FILE" 2>&1 || true
        docker-compose rm -f -s -v backend >> "$LOG_FILE" 2>&1 || true
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

    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Deployed [$TARGET_BRANCH] Successfully." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi
EOF