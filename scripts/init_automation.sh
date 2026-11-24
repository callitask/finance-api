#!/bin/bash
# ----------------------------------------------------------------------
# ONE-TIME SETUP SCRIPT
# Purpose: Registers the auto_deploy.sh script to run every minute.
# NOTE: Engine A/B (deploy.yml + auto_deploy.sh) supersedes the legacy
#       1-minute cron registered by this script. The cron entry is retained
#       below for backward compatibility only.
# ----------------------------------------------------------------------

DEPLOY_SCRIPT="/opt/treishvaam/scripts/auto_deploy.sh"
LOG_FILE="/var/log/treishvaam_deploy.log"

echo "Configuring Treishvaam Auto-Pilot..."

# 1. Prepare Log File
sudo touch "$LOG_FILE"
sudo chmod 644 "$LOG_FILE"

# 2. Make Deploy Script Executable
sudo chmod +x "$DEPLOY_SCRIPT"

# 3. Add to Cron (Runs every minute)
# This command adds the job ONLY if it doesn't already exist
(crontab -l 2>/dev/null | grep -v "auto_deploy.sh"; echo "* * * * * $DEPLOY_SCRIPT >> /dev/null 2>&1") | crontab -

echo "✅ Success! The server will now check for updates every minute."
echo "   Logs: tail -f $LOG_FILE"