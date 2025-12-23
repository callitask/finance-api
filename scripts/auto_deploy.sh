#!/bin/bash
# ----------------------------------------------------------------------
# FINAL ENTERPRISE DEPLOY (AUTH-FIXED + COMPILE)
# Purpose: 
#   1. Detects Real User to fix Infisical Auth (Root vs User issue).
#   2. Compiles Java Code (Maven).
#   3. Deploys with PROD secrets.
# ----------------------------------------------------------------------

# 1. FIX PATH & ENVIRONMENT
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

cd "$PROJECT_DIR" || exit 1

# --- CRITICAL: FIX INFISICAL AUTH FOR SUDO/ROOT ---
# If running as root (sudo), use the real user's Infisical config
if [ -n "$SUDO_USER" ]; then
    REAL_HOME=$(getent passwd "$SUDO_USER" | cut -d: -f6)
    export INFISICAL_CONFIG_DIR="$REAL_HOME/.infisical"
    echo "[$(date)] 🔧 Running as sudo. Using Infisical config from: $INFISICAL_CONFIG_DIR" >> "$LOG_FILE"
fi

# --- SECURITY: LOAD ENV VARS ---
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# 2. FETCH & DETERMINE TARGET BRANCH
git fetch --all >> "$LOG_FILE" 2>&1

TS_MAIN=$(git log -1 --format=%ct origin/main 2>/dev/null || echo 0)
TS_DEVELOP=$(git log -1 --format=%ct origin/develop 2>/dev/null || echo 0)

TARGET_BRANCH="main"
if [ "$TS_DEVELOP" -gt "$TS_MAIN" ]; then
    TARGET_BRANCH="develop"
fi

# --- HELPER: SECURE EXECUTION WRAPPER ---
run_secure() {
    INFISICAL_CMD="infisical"
    if [ -f "/usr/local/bin/infisical" ]; then INFISICAL_CMD="/usr/local/bin/infisical"; fi
    if [ -f "/usr/bin/infisical" ]; then INFISICAL_CMD="/usr/bin/infisical"; fi

    if command -v $INFISICAL_CMD &> /dev/null; then
        echo "[$(date)] 🔐 Injecting 'prod' secrets via Infisical..." >> "$LOG_FILE"
        $INFISICAL_CMD run --projectId "$INFISICAL_PROJECT_ID" --env prod -- "$@"
    else
        echo "[$(date)] ❌ CRITICAL: Infisical not found. Deployment may fail." >> "$LOG_FILE"
        "$@"
    fi
}

# 3. CHECK FOR ACTIVITY
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

if [ "$LOCAL" != "$REMOTE" ]; then
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
    echo "[$(date)] 🚀 New activity on [$TARGET_BRANCH]. Starting Build & Deploy..." >> "$LOG_FILE"
    
    CHANGED_FILES=$(git diff --name-only HEAD "origin/$TARGET_BRANCH")
    
    # 4. FORCE SYNC
    echo "[$(date)] Forcing synchronization with origin/$TARGET_BRANCH..." >> "$LOG_FILE"
    git checkout "$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    git reset --hard "origin/$TARGET_BRANCH" >> "$LOG_FILE" 2>&1
    
    chmod +x scripts/*.sh backup/*.sh mvnw
    
    # --- 5. COMPILE JAVA CODE (Crucial Step) ---
    if echo "$CHANGED_FILES" | grep -qE "^src/|^pom.xml"; then
        echo "[$(date)] 🔨 Compiling Backend Code (Maven)..." >> "$LOG_FILE"
        
        # Run Maven as the original user to avoid permission issues if possible, or just root
        ./mvnw clean package -DskipTests >> "$LOG_FILE" 2>&1
        
        if [ $? -eq 0 ]; then
            echo "[$(date)] ✅ Build Successful. Updating WAR file..." >> "$LOG_FILE"
            cp target/*.war backend-app.war
        else
            echo "[$(date)] ❌ Build FAILED. Aborting deployment." >> "$LOG_FILE"
            echo "----------------------------------------------------------------" >> "$LOG_FILE"
            exit 1
        fi
    fi

    # 6. AGGRESSIVE RESTART
    echo "[$(date)] ☕ Restarting Backend Containers..." >> "$LOG_FILE"
    
    docker-compose stop backend >> "$LOG_FILE" 2>&1 || true
    docker-compose rm -f -s -v backend >> "$LOG_FILE" 2>&1 || true
    
    run_secure docker-compose up -d --force-recreate backend >> "$LOG_FILE" 2>&1

    # --- CONDITIONAL SERVICES ---
    if echo "$CHANGED_FILES" | grep -qE "^nginx/"; then
        docker restart treishvaam-nginx >> "$LOG_FILE" 2>&1
    fi

    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Deployed [$TARGET_BRANCH] Successfully." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi