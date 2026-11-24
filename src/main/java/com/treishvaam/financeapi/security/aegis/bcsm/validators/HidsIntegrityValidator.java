/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 8 Validator (Host Integrity Node). - Correlates host-level Wazuh HIDS
 * alerts with the incoming request IP.
 *
 * <p>Scope: - Acts as a bridge between container requests and host-level security context.
 *
 * <p>Security Constraints: - Fail-open to `ALLOW` if Wazuh agent is unreachable. - HIDS alerts are
 * written to Redis by Wazuh active-response webhooks or future HIDS bridges.
 *
 * <p>Change Intent: - Wired real HIDS alert lookup via Redis (P3.2, VULN-22b).
 *
 * <p>Critical Dependencies: - Backend: AegisBcsm, RedisTemplate.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of
 * HidsIntegrityValidator. - EDITED (Remediation): • Aligned method signature to
 * `evaluate(HttpServletRequest, String)`. • Converted raw strings to `SecurityDecision` enums. •
 * Phase 2 Implementation. - EDITED (Batch 3B - P3.2, VULN-22b): • Injected `RedisTemplate<String,
 * String>` via constructor. • Queries Redis key `aegis:hids:alert:<clientIp>` inside fail-safe
 * `try/catch`; `90 / BLOCK` if found (non-blank), else `0 / ALLOW` (fail-open on Redis
 * error/timeout).
 */
package com.treishvaam.financeapi.security.aegis.bcsm.validators;

import com.treishvaam.financeapi.security.aegis.bcsm.AegisValidator;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.bcsm.ValidatorResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class HidsIntegrityValidator implements AegisValidator {

    private final RedisTemplate<String, String> redisTemplate;

    public HidsIntegrityValidator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public ValidatorResult evaluate(HttpServletRequest request, String sessionId) {
        // HIDS alert bridge: `aegis:hids:alert:<clientIp>` written by Wazuh active-response
        // webhook or future HIDS bridge. Fail-open on Redis error/timeout.
        String clientIp = request.getRemoteAddr();
        boolean alertFound = false;
        try {
            String alert = redisTemplate.opsForValue().get("aegis:hids:alert:" + clientIp);
            alertFound = alert != null && !alert.isBlank();
        } catch (Exception e) {
            // Fail-open: Redis error/timeout must not block legitimate users
        }
        int score = alertFound ? 90 : 0;
        SecurityDecision rec = alertFound ? SecurityDecision.BLOCK : SecurityDecision.ALLOW;

        return new ValidatorResult(score, rec, "HIDS_NODE");
    }
}
