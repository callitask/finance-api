package com.treishvaam.financeapi.security.aegis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Bridges Identity (Keycloak) with Behavioral Intelligence (AEGIS L5).
 *
 * <p>Scope: - Inspects incoming JWTs for custom AEGIS risk claims or enforces local behavioral
 * validation.
 *
 * <p>Critical Dependencies: - AegisBehavioralEngine: Supplies the runtime score.
 *
 * <p>Security Constraints: - If a token's risk score exceeds 80, it must be flagged for Step-Up ZKP
 * Authentication (Future Phase 5).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (AEGIS Phase 3): • Validation wrapper to
 * enforce behavioral context on authenticated endpoints.
 */
@Component
public class AegisJwtEnhancer {

  private static final Logger logger = LoggerFactory.getLogger(AegisJwtEnhancer.class);

  private final AegisBehavioralEngine behavioralEngine;

  public AegisJwtEnhancer(AegisBehavioralEngine behavioralEngine) {
    this.behavioralEngine = behavioralEngine;
  }

  public boolean isTokenBehaviorallySafe(
      Jwt jwt, String realIp, String ja3Fingerprint, String biometricHash) {
    int realtimeRisk = behavioralEngine.calculateRiskScore(realIp, ja3Fingerprint, biometricHash);

    if (realtimeRisk >= 80) {
      logger.warn(
          "AEGIS L5-BIE: Identity compromised or utilized by automated system. User: {}",
          jwt.getSubject());
      return false; // Triggers 403 Forbidden or Step-Up challenge
    }

    return true;
  }
}
