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
        boolean isTemporalValid = request.getAttribute("AEGIS_TEMPORAL_VALID") != null;
        boolean isPublicStatic =
                request.getRequestURI().startsWith("/sitemaps")
                        || request.getRequestURI().equals("/");

        int score = (!isTemporalValid && !isPublicStatic) ? 60 : 0;
        SecurityDecision rec = score > 0 ? SecurityDecision.WARN : SecurityDecision.ALLOW;

        return new ValidatorResult(score, rec, "TEMPORAL_NODE");
    }
}
