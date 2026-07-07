#!/bin/bash
# /**
#  * AI-CONTEXT:
#  *
#  * Purpose:
#  * - Operational helper script to provide an instant, zero-friction health snapshot of the Treishvaam production stack.
#  *
#  * Scope:
#  * - Aggregates Docker container status, resource usage, disk health, telemetry output, and backend JVM actuator health.
#  * - Intended for manual execution by an engineer via SSH.
#  *
#  * Critical Dependencies:
#  * - Requires `docker`, `df`, `curl`, and `python3` to be available on the host machine.
#  * - Relies on `treishvaam-nginx` routing `localhost/actuator/health` to the backend.
#  *
#  * Security Constraints:
#  * - Must only be executable by authorized users on the host machine.
#  * - Does not expose internal API metrics to external networks.
#  *
#  * Change Intent:
#  * - Resolve FLAW-11 by removing the friction of tunneling into Grafana just to verify if the server is healthy post-deployment.
#  *
#  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - ADDED (CI/CD Remediation - Ops Hardening):
#  * • Created instant deployment health report script.
#  * • Reason: Allows engineers to rapidly diagnose disk pressure, memory OOM risks, and deployment telemetry parsing via a single CLI command without heavy observability UI overhead.
#  * • Date/Phase: 2026-06-25
#  *
#  * - EDITED (Phase 4 Remediation Integration - 2026-07-07):
#  * • Verified semantic formatting and Python JSON parsing logic for telemetry.
#  * • Reason: Ensuring the script safely consumes the upgraded Engine B NDJSON telemetry without syntax failure.
#  */

echo "═══════════════════════════════════════════"
echo "  TREISHVAAM DEPLOYMENT HEALTH REPORT"
echo "  Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "═══════════════════════════════════════════"

echo ""
echo "📦 CONTAINER STATUS:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Image}}" | head -30

echo ""
echo "📊 RESOURCE USAGE (TOP 5):"
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}" 2>/dev/null | \
    sort -k2 -rh | head -6

echo ""
echo "💾 DISK HEALTH:"
df -h /
echo ""
docker system df

echo ""
echo "🚀 LAST DEPLOYMENT:"
tail -n 5 /opt/treishvaam/logs/deploy_telemetry.ndjson | \
    python3 -c "
import sys, json
for line in sys.stdin:
    try:
        d = json.loads(line.strip())
        print(f\"  {d['timestamp']} | {d['phase']:30s} | {d['status']:12s} | {d['message'][:60]}\")
    except: pass
"

echo ""
echo "❤️ BACKEND HEALTH:"
curl -s -m 5 http://localhost/actuator/health 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "  Backend not reachable via localhost"
echo ""