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
 *
 * <p>- EDITED (Batch 3B - P3.2, VULN-22b): • Wired `ShannonEntropyCalculator` into
 * `calculateRiskScore(...)`: inspects the current request's `getQueryString()` (via
 * `RequestContextHolder`); if non-null, length > 64, and NOT an OAuth callback param
 * (`code=`/`state=`/`session_state=`), computes `double h =
 * ShannonEntropyCalculator.calculate(queryString)` and adds `+20` to the risk score when `h > 5.2`
 * (cap at 100 preserved). • Behavior preserved: JA3 + biometric-hash scoring unchanged; all other
 * logic identical.
 *
 * <p>- EDITED (Step 0 - Phase 3B Surgical Polish): • Added 4-arg overload `calculateRiskScore(
 * String realIp, String ja3Fingerprint, String biometricHash, String queryString)`; the 3-arg
 * overload now delegates to it passing `null`. Inside the 4-arg method, when `queryString == null`,
 * the previous `RequestContextHolder` fallback applies. Caller `BehavioralValidator` passes
 * `request.getQueryString()` directly — `RequestContextHolder` is non-inheritable, so it returned
 * null inside the per-task virtual thread spawned by `AegisBcsm` and the entropy check silently
 * skipped (virtual-thread blindspot fix).
 *
 * <p>- EDITED (2026-11-26 - Batch 2 §2.8 Virtual-Thread AI-Crawler Header Fix): • Added 5-arg
 * overload `calculateRiskScore(realIp, ja3Fingerprint, biometricHash, queryString, userAgent)` with
 * 4-arg delegating `null` for `userAgent`; updated `isVerifiedAiCrawler(realIp, explicitUserAgent)`
 * to prefer `explicitUserAgent` over `RequestContextHolder` so AI crawler verification works inside
 * `AegisBcsm` virtual threads.
 */
@Service
public class AegisBehavioralEngine {

    private static final Logger logger = LoggerFactory.getLogger(AegisBehavioralEngine.class);

    // In-memory cache for demo/fast access. In production, this syncs with Redis.
    private final Map<String, Integer> ja3RiskCache = new ConcurrentHashMap<>();

    /**
     * Calculates the behavioral risk score (0-100). 0 = Verified Human/Bot, 100 = Confirmed
     * Malicious. Delegates to the 4-arg overload passing {@code null} (previous behavior).
     */
    public int calculateRiskScore(String realIp, String ja3Fingerprint, String biometricHash) {
        return calculateRiskScore(realIp, ja3Fingerprint, biometricHash, null);
    }

    /**
     * 4-arg overload (Step 0 — virtual-thread blindspot fix): delegates to the 5-arg overload
     * passing {@code null} for {@code userAgent} (preserving {@code RequestContextHolder}
     * fallback).
     */
    public int calculateRiskScore(
            String realIp, String ja3Fingerprint, String biometricHash, String queryString) {
        return calculateRiskScore(realIp, ja3Fingerprint, biometricHash, queryString, null);
    }

    /**
     * 5-arg overload (Batch 2 — virtual-thread AI-crawler blindspot fix): accepts both {@code
     * queryString} and {@code userAgent} explicitly. When either is {@code null}, falls back to
     * {@code RequestContextHolder}.
     */
    public int calculateRiskScore(
            String realIp,
            String ja3Fingerprint,
            String biometricHash,
            String queryString,
            String userAgent) {

        if (isVerifiedAiCrawler(realIp, userAgent)) {
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

        // 3. Shannon Entropy of query string (P3.2, VULN-22b) — obfuscated exploit/DGA payload
        // detection. Step 0: prefer the explicitly-passed `queryString`; the RequestContextHolder
        // fallback only applies when it is null.
        String effectiveQueryString = queryString;
        if (effectiveQueryString == null) {
            try {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null && attrs.getRequest() != null) {
                    effectiveQueryString = attrs.getRequest().getQueryString();
                }
            } catch (Exception e) {
                logger.debug("AEGIS L5-BIE: No request context for entropy check", e);
            }
        }
        if (effectiveQueryString != null && effectiveQueryString.length() > 64) {
            double entropy = ShannonEntropyCalculator.calculate(effectiveQueryString);
            boolean isOauthCallbackParam =
                    effectiveQueryString.contains("code=")
                            || effectiveQueryString.contains("state=")
                            || effectiveQueryString.contains("session_state=");
            if (entropy > 5.2 && !isOauthCallbackParam) {
                riskScore += 20; // High entropy on long queries = DGA/obfuscation
            }
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
    private boolean isVerifiedAiCrawler(String realIp, String explicitUserAgent) {
        try {
            String userAgent = explicitUserAgent;
            if (userAgent == null || userAgent.isBlank()) {
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes == null || attributes.getRequest() == null) {
                    return false;
                }
                HttpServletRequest request = attributes.getRequest();
                userAgent = request.getHeader("User-Agent");
            }

            if (userAgent == null || userAgent.isBlank()) {
                return false;
            }

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
