/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Implements AEGIS Layer 4 (ADA) Canary Token Generation. - Generates unique,
 * traceable tokens (pseudo-AWS keys, JWT signatures) to embed in poisoned responses.
 *
 * <p>Scope: - Tracks exfiltration attempts. If an attacker uses these credentials, the system
 * immediately flags their IP.
 *
 * <p>Critical Dependencies: - Backend: AegisDeceptionEngine.
 *
 * <p>Security Constraints: - Must never generate real or colliding credentials. - Must execute in
 * sub-millisecond time.
 *
 * <p>Change Intent: - Executing AEGIS Orchestrator Phase 3.2 (Deception & Polymorphism).
 *
 * <p>Future AI Guidance: - Can be expanded to call the actual thinkst/canarytokens Docker container
 * API via WebClient.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation for Phase 3.2 ADA
 * Orchestration. • Implemented UUID-based fail-safe canary generation.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CanaryTokenService {

    private static final Logger log = LoggerFactory.getLogger(CanaryTokenService.class);

    /**
     * Generates a traceable token to embed in fake responses. * @param attackerIp The IP of the
     * scanner.
     *
     * @param context The honeypot path they accessed.
     * @return A realistic looking fake credential hash.
     */
    public String generateCanaryToken(String attackerIp, String context) {
        // Fallback robust generation (Can be swapped with a real HTTP call to aegis-canary-server)
        String canary =
                "AKIA"
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 16)
                                .toUpperCase();
        log.info(
                "AEGIS L4-ADA: Generated Canary Token [{}] for IP [{}] in context [{}]",
                canary,
                attackerIp,
                context);
        return canary;
    }
}
