#!/bin/bash
# /**
#  * AI-CONTEXT:
#  *
#  * Purpose:
#  * - Operational helper script to provide real-time, color-coded visibility into Engine B deployments.
#  *
#  * Scope:
#  * - Tails and parses `/opt/treishvaam/deploy_pipeline.log`.
#  * - Intended for manual execution by an engineer via SSH.
#  *
#  * Critical Dependencies:
#  * - Relies on `auto_deploy.sh` writing to the standardized log path.
#  *
#  * Security Constraints:
#  * - Must only be executable by authorized users (vboxuser/root) on the host machine.
#  * - Does not expose logs to external networks.
#  *
#  * Change Intent:
#  * - Resolve FLAW-11 by removing the friction of tunneling into Grafana just to monitor an active deployment.
#  *
#  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - ADDED (CI/CD Remediation - Ops Hardening):
#  * • Created color-coded deployment watcher.
#  * • Reason: Engineers were flying blind or forced to use complex Loki queries to watch deployments. This restores standard `tail -f` visibility with semantic color highlighting.
#  * • Date/Phase: 2026-06-25
#  */

echo "🔍 Watching Treishvaam deployment pipeline..."
echo "Press Ctrl+C to exit"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

LOG_FILE="/opt/treishvaam/deploy_pipeline.log"

if [ ! -f "$LOG_FILE" ]; then
    echo -e "${RED}[ERROR] Log file not found: $LOG_FILE${NC}"
    echo "This file is created automatically when Engine B (auto_deploy.sh) runs."
    exit 1
fi

tail -f "$LOG_FILE" | while IFS= read -r line; do
    if echo "$line" | grep -qiE 'error|fail|critical|denied|exception'; then
        echo -e "${RED}[ERROR]   $line${NC}"
    elif echo "$line" | grep -qiE 'success|complete|healthy|done'; then
        echo -e "${GREEN}[OK]      $line${NC}"
    elif echo "$line" | grep -qiE 'warning|notice|wait|skipping|stale'; then
        echo -e "${YELLOW}[WARN]    $line${NC}"
    elif echo "$line" | grep -qiE 'container|image|network|volume'; then
        echo -e "${CYAN}[DOCKER]  $line${NC}"
    else
        echo "          $line"
    fi
done