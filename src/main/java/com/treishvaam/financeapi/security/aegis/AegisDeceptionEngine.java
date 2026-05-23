/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Implements AEGIS Layer 4 (ADA) Deception Strategy Selector. - Routes attackers into
 * PLAUSIBLE_FAKE, SLOW_LEAK, or RECURSIVE_LOOP behaviors.
 *
 * <p>Scope: - The core brain of the deception honeypot.
 *
 * <p>Critical Dependencies: - Backend: PoisonCorpusGenerator, CanaryTokenService, TarpitManager.
 *
 * <p>Security Constraints: - MUST enforce strict Cache-Control: no-store headers so Cloudflare Edge
 * never caches fake payloads.
 *
 * <p>Change Intent: - Executing AEGIS Orchestrator Phase 3.2.
 *
 * <p>Future AI Guidance: - Logic here dictates how scanners are manipulated. Keep it lightweight
 * and non-blocking where possible.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation for Phase 3.2 ADA
 * Orchestration. • Integrated RECURSIVE_LOOP, PLAUSIBLE_FAKE, and SLOW_LEAK strategies. • Enforced
 * Cloudflare cache bypass headers.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Service;

@Service
public class AegisDeceptionEngine {

    private final PoisonCorpusGenerator corpusGenerator;
    private final CanaryTokenService canaryTokenService;
    private final TarpitManager tarpitManager;

    public AegisDeceptionEngine(
            PoisonCorpusGenerator corpusGenerator,
            CanaryTokenService canaryTokenService,
            TarpitManager tarpitManager) {
        this.corpusGenerator = corpusGenerator;
        this.canaryTokenService = canaryTokenService;
        this.tarpitManager = tarpitManager;
    }

    public void executeDeception(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = request.getRequestURI().toLowerCase();
        String attackerIp =
                request.getHeader("X-Real-IP") != null
                        ? request.getHeader("X-Real-IP")
                        : request.getRemoteAddr();
        String canary = canaryTokenService.generateCanaryToken(attackerIp, path);

        // ZERO-TRUST EDGE CACHE BYPASS: Ensure Cloudflare never caches deception data.
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        if (path.contains(".env") || path.contains("config.php")) {
            // PLAUSIBLE_FAKE: Feed credential scanners fake keys
            servePlausibleFake(response, corpusGenerator.generateFakeEnv(canary), "text/plain");
        } else if (path.contains("wp-admin")
                || path.contains("phpmyadmin")
                || path.contains("vendor")) {
            // RECURSIVE_LOOP: Trap directory traversal bots in an infinite redirect chain
            serveRecursiveLoop(request, response, canary);
        } else if (path.contains("api/")) {
            // PLAUSIBLE_FAKE: Feed API fuzzers fake JWTs
            servePlausibleFake(
                    response, corpusGenerator.generateFakeJwt(canary), "application/json");
        } else {
            // SLOW_LEAK: Default to Tarpit for unclassified malicious payloads
            tarpitManager.ensnare(response);
        }
    }

    private void servePlausibleFake(
            HttpServletResponse response, String payload, String contentType) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(contentType);
        response.getWriter().write(payload);
        response.getWriter().flush();
    }

    private void serveRecursiveLoop(
            HttpServletRequest request, HttpServletResponse response, String canary)
            throws IOException {
        String currentDepthStr = request.getParameter("d");
        int depth =
                (currentDepthStr != null && currentDepthStr.matches("\\d+"))
                        ? Integer.parseInt(currentDepthStr)
                        : 0;

        // Obfuscate the path and append the canary for tracking
        String nextUrl = request.getRequestURI() + "?d=" + (depth + 1) + "&t=" + canary;

        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY); // 301
        response.setHeader("Location", nextUrl);
    }
}
