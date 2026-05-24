package com.treishvaam.financeapi.security.aegis;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
 * scoring logic for telemetry and TLS fingerprint heuristics. - EDITED (AEGIS Phase 6 - GEO
 * Integration): • Injected `isVerifiedAiCrawler` logic to perform reverse DNS / PTR checks. •
 * Bypasses risk scoring for legitimate AI agents (Googlebot, GPTBot) to ensure maximum Generative
 * Engine Optimization visibility without false-positive tarpitting.
 */
@Service
public class AegisBehavioralEngine {

    private static final Logger logger = LoggerFactory.getLogger(AegisBehavioralEngine.class);

    // In-memory cache for demo/fast access. In production, this syncs with Redis.
    private final Map<String, Integer> ja3RiskCache = new ConcurrentHashMap<>();

    /**
     * Calculates the behavioral risk score (0-100). 0 = Verified Human/Bot, 100 = Confirmed
     * Malicious.
     */
    public int calculateRiskScore(String realIp, String ja3Fingerprint, String biometricHash) {

        if (isVerifiedAiCrawler(realIp)) {
            logger.debug(
                    "AEGIS L5-BIE: Verified AI Crawler bypassed risk scoring for IP {}", realIp);
            return 0;
        }

        int riskScore = 0;

        // 1. JA3 Fingerprint Analysis
        if (ja3Fingerprint == null
                || ja3Fingerprint.isEmpty()
                || ja3Fingerprint.equals("UNKNOWN")) {
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

    /** Validates claiming AI bots via Reverse DNS to prevent User-Agent spoofing. */
    private boolean isVerifiedAiCrawler(String realIp) {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return false;

            HttpServletRequest request = attributes.getRequest();
            String userAgent = request.getHeader("User-Agent");

            if (userAgent == null) return false;

            boolean claimsToBeAi =
                    userAgent.contains("GPTBot")
                            || userAgent.contains("ClaudeBot")
                            || userAgent.contains("Google-Extended")
                            || userAgent.contains("anthropic-ai")
                            || userAgent.contains("PerplexityBot")
                            || userAgent.contains("Googlebot");

            if (claimsToBeAi) {
                // Perform Reverse DNS (PTR) verification to prevent IP spoofing
                java.net.InetAddress addr = java.net.InetAddress.getByName(realIp);
                String hostName = addr.getHostName();

                // If the PTR record matches known AI domains, verify it
                if (hostName.endsWith(".googlebot.com")
                        || hostName.endsWith(".search.msn.com")
                        || hostName.endsWith(".outbound-enterprise.openai.com")
                        || hostName.endsWith(".anthropic.com")) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.warn("AEGIS L5-BIE: Reverse DNS failed for claiming AI IP: {}", realIp);
        }
        return false;
    }
}
