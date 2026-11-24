/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 8 Validator (L4-ADA Node). - Votes on consensus based on whether the
 * client has hit honeypot traps.
 *
 * <p>Scope: - Reads from deception registry flags (Redis `aegis:deception:score:<ip>`).
 *
 * <p>Security Constraints: - A high deception score almost unilaterally drives the consensus toward
 * Tarpit or Emergency. - Fail-open on Redis error/timeout (legitimate users shouldn't be tarpitted
 * on transient network hiccups).
 *
 * <p>Change Intent: - Wired real deception registry via Redis (P3.2, VULN-22b); removed the
 * attacker-controllable `X-AEGIS-Trap-Flag` header check.
 *
 * <p>Critical Dependencies: - Backend: AegisBcsm, RedisTemplate.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of DeceptionValidator. -
 * EDITED (Remediation): • Aligned method signature to `evaluate(HttpServletRequest, String)`. •
 * Converted raw strings to `SecurityDecision` enums. • Phase 2 Implementation. - EDITED (Batch 3B -
 * P3.2, VULN-22b): • Removed attacker-controllable `X-AEGIS-Trap-Flag` header check. • Injected
 * `RedisTemplate<String, String>` via constructor. • Queries Redis key
 * `aegis:deception:score:<clientIp>` (written by AegisDeceptionFilter); `100` → `90 / BLOCK`
 * (EMERGENCY), else `0 / ALLOW`; fail-open on Redis error/timeout.
 */
package com.treishvaam.financeapi.security.aegis.bcsm.validators;

import com.treishvaam.financeapi.security.aegis.bcsm.AegisValidator;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.bcsm.ValidatorResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeceptionValidator implements AegisValidator {

    private final RedisTemplate<String, String> redisTemplate;

    public DeceptionValidator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public ValidatorResult evaluate(HttpServletRequest request, String sessionId) {
        // Deception registry: `aegis:deception:score:<ip>` written by AegisDeceptionFilter
        String clientIp = request.getRemoteAddr();
        String redisScore = null;
        try {
            redisScore = redisTemplate.opsForValue().get("aegis:deception:score:" + clientIp);
        } catch (Exception e) {
            // Fail-open: Redis error/timeout must not tarpit legitimate users
            redisScore = null;
        }
        boolean hasHitHoneypot = redisScore != null && redisScore.equals("100");

        int score = hasHitHoneypot ? 100 : 0;
        SecurityDecision rec = hasHitHoneypot ? SecurityDecision.EMERGENCY : SecurityDecision.ALLOW;

        return new ValidatorResult(score, rec, "DECEPTION_NODE");
    }
}
