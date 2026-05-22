/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - L8-BCSM Validator Result Record.
 *
 * <p>Scope: - Carries the individual score (0-100) and recommendation of a single validator.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation for Phase 4/5
 * integration.
 */
package com.treishvaam.financeapi.security.aegis.bcsm;

public record ValidatorResult(int score, SecurityDecision recommendation, String reason) {
    public static ValidatorResult timeout() {
        return new ValidatorResult(0, SecurityDecision.ALLOW, "VALIDATOR_TIMEOUT");
    }
}
