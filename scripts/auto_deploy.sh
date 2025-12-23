#!/bin/bash
# ----------------------------------------------------------------------
# COMPILE & DEPLOY SCRIPT (PROD ONLY)
# Purpose: 
#   1. Syncs Git.
#   2. COMPILES the Java Code (Maven).
#   3. Updates the WAR file.
#   4. Restarts Containers with Secrets.
# ----------------------------------------------------------------------

# 1. FIX PATH & ENVIRONMENT
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
PROJECT_DIR="/opt/treishvaam"
LOG_FILE="/var/log/treishvaam_deploy.log"

cd "$PROJECT_DIR" || exit 1

# --- SECURITY: LOAD PROJECT ID ---
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
    
    # Ensure permissions
    chmod +x scripts/*.sh backup/*.sh mvnw
    
    # --- 5. COMPILE JAVA CODE (Crucial Step) ---
    if echo "$CHANGED_FILES" | grep -qE "^src/|^pom.xml"; then
        echo "[$(date)] 🔨 Compiling Backend Code (Maven)..." >> "$LOG_FILE"
        
        # Run Maven Build (Skip tests to save time in prod)
        ./mvnw clean package -DskipTests >> "$LOG_FILE" 2>&1
        
        if [ $? -eq 0 ]; then
            echo "[$(date)] ✅ Build Successful. Updating WAR file..." >> "$LOG_FILE"
            # Move the new WAR to the Docker volume location
            cp target/*.war backend-app.war
        else
            echo "[$(date)] ❌ Build FAILED. Aborting deployment." >> "$LOG_FILE"
            echo "----------------------------------------------------------------" >> "$LOG_FILE"
            exit 1
        fi
    fi

    # 6. AGGRESSIVE RESTART
    echo "[$(date)] ☕ Restarting Backend Containers..." >> "$LOG_FILE"
    
    # Stop to force reload of WAR file and Environment
    docker-compose stop backend >> "$LOG_FILE" 2>&1 || true
    docker-compose rm -f -s -v backend >> "$LOG_FILE" 2>&1 || true
    
    # Start with Secrets
    run_secure docker-compose up -d --force-recreate backend >> "$LOG_FILE" 2>&1

    # --- CONDITIONAL SERVICES ---
    if echo "$CHANGED_FILES" | grep -qE "^nginx/"; then
        docker restart treishvaam-nginx >> "$LOG_FILE" 2>&1
    fi

    docker image prune -f >> "$LOG_FILE" 2>&1
    
    echo "[$(date)] ✅ Deployed [$TARGET_BRANCH] Successfully." >> "$LOG_FILE"
    echo "----------------------------------------------------------------" >> "$LOG_FILE"
fi