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
 * <p>Change Intent: - Implement the 7-node Byzantine quorum.
 *
 * <p>Future AI Guidance: - Retain the score mapping logic to align with standard 0-100 L8 scales.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of BehavioralValidator.
 * • Phase 2 Implementation.
 */
package com.treishvaam.financeapi.security.aegis.bcsm.validators;

import com.treishvaam.financeapi.security.aegis.AegisBehavioralEngine;
import com.treishvaam.financeapi.security.aegis.bcsm.AegisValidator;
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
    public ValidatorResult evaluate(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String ja3 = request.getHeader("X-JA3-Fingerprint");
        String biometrics = request.getHeader("X-AEGIS-Biometric");

        int riskScore = behavioralEngine.calculateRiskScore(ip, ja3, biometrics);

        String recommendation = riskScore > 80 ? "BLOCK" : (riskScore > 50 ? "WARN" : "ALLOW");
        return new ValidatorResult(riskScore, recommendation, "BEHAVIORAL_NODE");
    }
}
