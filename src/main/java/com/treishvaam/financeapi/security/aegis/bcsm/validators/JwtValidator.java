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
 * <p>Change Intent: - Implement the 7-node Byzantine quorum.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation of JwtValidator. • Phase
 * 2 Implementation.
 */
package com.treishvaam.financeapi.security.aegis.bcsm.validators;

import com.treishvaam.financeapi.security.aegis.bcsm.AegisValidator;
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
    public ValidatorResult evaluate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return new ValidatorResult(0, "ALLOW", "JWT_NODE"); // Public route
        }

        String token = authHeader.substring(7);
        boolean isValid = pqcJwtService.validateToken(token);

        int score = isValid ? 0 : 100;
        String rec = isValid ? "ALLOW" : "BLOCK";

        return new ValidatorResult(score, rec, "JWT_NODE");
    }
}
