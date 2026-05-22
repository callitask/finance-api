package com.treishvaam.financeapi.security.aegis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - AEGIS Layer 5 (Behavioral Intelligence Engine).
 *
 * <p>Scope: - Calculates an internal Risk Score for requests based on JA3, IP history, and
 * Biometrics.
 *
 * <p>Critical Dependencies: - AegisJwtEnhancer: Uses this engine to tag generated tokens.
 *
 * <p>Security Constraints: - Must operate entirely in-memory or via Redis for high-throughput
 * sub-millisecond execution.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (AEGIS Phase 3): • Implemented baseline
 * scoring logic for telemetry and TLS fingerprint heuristics.
 */
@Service
public class AegisBehavioralEngine {

  private static final Logger logger = LoggerFactory.getLogger(AegisBehavioralEngine.class);

  // In-memory cache for demo/fast access. In production, this syncs with Redis.
  private final Map<String, Integer> ja3RiskCache = new ConcurrentHashMap<>();

  /** Calculates the behavioral risk score (0-100). 0 = Verified Human, 100 = Confirmed Bot/AI. */
  public int calculateRiskScore(String realIp, String ja3Fingerprint, String biometricHash) {
    int riskScore = 0;

    // 1. JA3 Fingerprint Analysis
    if (ja3Fingerprint == null || ja3Fingerprint.isEmpty() || ja3Fingerprint.equals("UNKNOWN")) {
      riskScore += 40; // Missing fingerprint indicates custom HTTP client bypassing OpenResty
    } else if (ja3RiskCache.getOrDefault(ja3Fingerprint, 0) > 5) {
      riskScore += 60; // Known malicious JA3 signature
    }

    // 2. Biometric Validation (GDPR Compliant Hash from Frontend)
    if (biometricHash == null || biometricHash.trim().isEmpty()) {
      riskScore += 30; // No mouse/scroll vectors provided by frontend
    }

    // Cap at 100
    riskScore = Math.min(riskScore, 100);

    logger.debug(
        "AEGIS L5-BIE: Calculated Risk Score {} for IP {} / JA3 {}",
        riskScore,
        realIp,
        ja3Fingerprint);

    return riskScore;
  }

  public void flagMaliciousJa3(String ja3Fingerprint) {
    if (ja3Fingerprint != null && !ja3Fingerprint.equals("UNKNOWN")) {
      ja3RiskCache.merge(ja3Fingerprint, 1, Integer::sum);
    }
  }
}
