/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 8 Validator (L2-PPO Node). - Validates if the requested path conforms
 * to the daily endpoint manifest.
 *
 * <p>Scope: - Checks path against MTD requirements.
 *
 * <p>Critical Dependencies: - Backend: AegisBcsm
 *
 * <p>Security Constraints: - Requests to expired temporal paths are penalized.
 *
 * <p>Non-Negotiables: - Safe string comparison only.
 *
 * <p>Change Intent: - Fix-forward remediation: Type strictness alignment.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of
 * TemporalPathValidator. - EDITED (Remediation): • Aligned method signature to
 * `evaluate(HttpServletRequest, String)`. • Converted raw strings to `SecurityDecision` enums. •
 * Phase 2 Implementation.
 *
 * <p>- EDITED (Batch 3B - P3.2, VULN-22b): • Strict `Boolean.TRUE.equals(request.getAttribute(
 * "AEGIS_TEMPORAL_VALID"))` check (was `!= null` — false `+60` penalties on every non-root API call
 * because the attribute was never stamped).
 */
package com.treishvaam.financeapi.security.aegis.bcsm.validators;

import com.treishvaam.financeapi.security.aegis.bcsm.AegisValidator;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.bcsm.ValidatorResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class TemporalPathValidator implements AegisValidator {

    @Override
    public ValidatorResult evaluate(HttpServletRequest request, String sessionId) {
        // P3.2 (VULN-22b): Accept only strict Boolean.TRUE (stamped by AegisMainFilter pre-BCSM)
        boolean isTemporalValid = Boolean.TRUE.equals(request.getAttribute("AEGIS_TEMPORAL_VALID"));
        boolean isPublicStatic =
                request.getRequestURI().startsWith("/sitemaps")
                        || request.getRequestURI().equals("/");

        int score = (!isTemporalValid && !isPublicStatic) ? 60 : 0;
        SecurityDecision rec = score > 0 ? SecurityDecision.WARN : SecurityDecision.ALLOW;

        return new ValidatorResult(score, rec, "TEMPORAL_NODE");
    }
}
