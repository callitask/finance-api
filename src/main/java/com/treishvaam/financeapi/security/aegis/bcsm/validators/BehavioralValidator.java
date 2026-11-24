/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 8 Validator (L5-BIE Node). - Votes on consensus based on the client's
 * behavioral entropy and biometric telemetry.
 *
 * <p>Scope: - Evaluates request risk without altering state.
 *
 * <p>Critical Dependencies: - Backend: AegisBehavioralEngine, AegisBcsm
 *
 * <p>Security Constraints: - Must complete evaluation within the strict 100ms L8 threshold.
 *
 * <p>Non-Negotiables: - Never execute blocking I/O here.
 *
 * <p>Change Intent: - Fix-forward remediation: Type strictness alignment.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of BehavioralValidator.
 * - EDITED (Remediation): • Aligned method signature to `evaluate(HttpServletRequest, String)`. •
 * Converted raw strings to `SecurityDecision` enums. • Phase 2 Implementation. - EDITED (Batch 3B -
 * P3.2, VULN-22b): • Header rename `X-AEGIS-Biometric` → `X-Aegis-Biometric-Hash` (matches the
 * frontend `src/lib/aegis-biometrics.ts` — the old header name produced false `+30` penalties on
 * every real browser request).
 *
 * <p>- EDITED (Step 0 - Phase 3B Surgical Polish): • Call `behavioralEngine.calculateRiskScore( ip,
 * ja3, biometrics, request.getQueryString())` — passes the query string explicitly. Root cause:
 * `AegisBcsm.evaluate()` runs this validator on a newly spawned Java 21 Virtual Thread;
 * `RequestContextHolder` is non-inheritable, so the entropy check was silently skipped there.
 */
package com.treishvaam.financeapi.security.aegis.bcsm.validators;

import com.treishvaam.financeapi.security.aegis.AegisBehavioralEngine;
import com.treishvaam.financeapi.security.aegis.bcsm.AegisValidator;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.bcsm.ValidatorResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class BehavioralValidator implements AegisValidator {

    private final AegisBehavioralEngine behavioralEngine;

    public BehavioralValidator(AegisBehavioralEngine behavioralEngine) {
        this.behavioralEngine = behavioralEngine;
    }

    @Override
    public ValidatorResult evaluate(HttpServletRequest request, String sessionId) {
        String ip = request.getRemoteAddr();
        String ja3 = request.getHeader("X-JA3-Fingerprint");
        String biometrics = request.getHeader("X-Aegis-Biometric-Hash");

        // Step 0 (virtual-thread blindspot fix): pass the query string explicitly so the
        // Shannon entropy check runs even when `RequestContextHolder` is null inside the
        // per-task virtual thread spawned by `AegisBcsm`.
        int riskScore =
                behavioralEngine.calculateRiskScore(ip, ja3, biometrics, request.getQueryString());

        SecurityDecision recommendation =
                riskScore > 80
                        ? SecurityDecision.BLOCK
                        : (riskScore > 50 ? SecurityDecision.WARN : SecurityDecision.ALLOW);
        return new ValidatorResult(riskScore, recommendation, "BEHAVIORAL_NODE");
    }
}
