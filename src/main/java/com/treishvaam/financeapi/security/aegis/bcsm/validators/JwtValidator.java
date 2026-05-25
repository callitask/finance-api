/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 8 Validator (L1-PQCf Node). - Votes on consensus based on the presence
 * and validity of ML-DSA signatures.
 *
 * <p>Scope: - Parses the Authorization header.
 *
 * <p>Critical Dependencies: - Backend: AegisPqcJwtService, AegisBcsm
 *
 * <p>Security Constraints: - A forged token immediately pushes the score to 100.
 *
 * <p>Non-Negotiables: - Must not throw exceptions on missing tokens (anonymous access is valid for
 * some routes).
 *
 * <p>Change Intent: - Fix-forward remediation: Type strictness alignment.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of JwtValidator. -
 * EDITED (Remediation): • Aligned method signature to `evaluate(HttpServletRequest, String)`. •
 * Converted raw strings to `SecurityDecision` enums. • Phase 2 Implementation.
 */
package com.treishvaam.financeapi.security.aegis.bcsm.validators;

import com.treishvaam.financeapi.security.aegis.bcsm.AegisValidator;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.bcsm.ValidatorResult;
import com.treishvaam.financeapi.security.aegis.crypto.AegisPqcJwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class JwtValidator implements AegisValidator {

    private final AegisPqcJwtService pqcJwtService;

    public JwtValidator(AegisPqcJwtService pqcJwtService) {
        this.pqcJwtService = pqcJwtService;
    }

    @Override
    public ValidatorResult evaluate(HttpServletRequest request, String sessionId) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return new ValidatorResult(0, SecurityDecision.ALLOW, "JWT_NODE"); // Public route
        }

        String token = authHeader.substring(7);
        boolean isValid = pqcJwtService.validateToken(token);

        int score = isValid ? 0 : 100;
        SecurityDecision rec = isValid ? SecurityDecision.ALLOW : SecurityDecision.BLOCK;

        return new ValidatorResult(score, rec, "JWT_NODE");
    }
}
