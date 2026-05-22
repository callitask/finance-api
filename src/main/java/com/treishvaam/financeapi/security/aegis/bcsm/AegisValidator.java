/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - L8-BCSM Validator Interface.
 *
 * <p>Scope: - Standardizes how security modules (Behavioral, Temporal, Keycloak) vote on requests.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation for Phase 4/5
 * integration.
 */
package com.treishvaam.financeapi.security.aegis.bcsm;

import jakarta.servlet.http.HttpServletRequest;

public interface AegisValidator {
  ValidatorResult evaluate(HttpServletRequest request, String sessionId);
}
