/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Implements AEGIS Layer 4 (Active Deception Architecture).
 *
 * <p>Scope: - Intercepts HTTP requests at the very start of the Spring Security filter chain. -
 * Detects overt vulnerability scanning (e.g., /.env, /wp-admin, simple SQLi). - Routes attackers
 * into the TarpitManager to exhaust their resources.
 *
 * <p>Critical Dependencies: - AegisDeceptionEngine: Routes to appropriate deception strategy. -
 * RabbitMQAttackPublisher: Sends telemetry to Wazuh/Grafana.
 *
 * <p>Security Constraints: - Must operate efficiently to avoid bottlenecking legitimate traffic. -
 * Pattern matching must be pre-compiled and fail-fast.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (AEGIS Phase 2): • Initial creation of the
 * Chaos Mirror deception filter. • Designed to intercept botnet/scanner traffic and prevent it from
 * reaching actual controllers. - EDITED (AEGIS Phase 5 Fix): • Added
 * `servePoisonedResponse(HttpServletResponse)` method required by AegisMainFilter and L8-BCSM. •
 * Implements an active honeypot response generating a fake 200 OK with a dummy JWT token to trick
 * automated scanner logic and waste attacker cracking time. - EDITED: • Phase 3.2 ADA Update:
 * Refactored to delegate payload generation and strategy to AegisDeceptionEngine. • Changed
 * servePoisonedResponse signature to require HttpServletRequest for context-aware deception. • What
 * behavior must remain unchanged: Interception patterns and RabbitMQ event publishing must still
 * execute identically. - EDITED (Phase 5.4 - Cloudflare Anti-Cache Hardening): • Injected
 * aggressive `Cache-Control: no-store` and `Pragma: no-cache` headers into both entry points. •
 * Why: Neutralizes Gap G11. Guarantees that Cloudflare Edge KV and browser caches will NEVER
 * memorize a poisoned trap payload and inadvertently serve it to an organic user on a subsequent
 * request.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security.aegis;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-105) // Runs immediately after InputSanitizationFilter but before Auth
public class AegisDeceptionFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AegisDeceptionFilter.class);

    // Common automated scanner and exploitation paths
    private static final Pattern HONEYPOT_PATHS =
            Pattern.compile(
                    "(?i)^/(\\.env|wp-admin|phpmyadmin|config\\.php|\\.git|vendor/phpunit).*");

    // Basic SQLi/XSS signatures that bypass ModSecurity (as a fallback layer)
    private static final Pattern MALICIOUS_PAYLOADS =
            Pattern.compile("(?i)(UNION\\s+SELECT|script>|\\.\\./\\.\\./|base64_decode\\()");

    private final AegisDeceptionEngine deceptionEngine;
    private final RabbitMQAttackPublisher attackPublisher;

    public AegisDeceptionFilter(
            AegisDeceptionEngine deceptionEngine, RabbitMQAttackPublisher attackPublisher) {
        this.deceptionEngine = deceptionEngine;
        this.attackPublisher = attackPublisher;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String query = request.getQueryString() != null ? request.getQueryString() : "";

        if (HONEYPOT_PATHS.matcher(path).matches() || MALICIOUS_PAYLOADS.matcher(query).find()) {
            logger.warn(
                    "AEGIS L4-ADA: Malicious probe detected on path [{}]. Initiating Chaos Mirror via Engine.",
                    path);

            // Extract edge telemetry injected by OpenResty Lua (Phase 1)
            String realIp = request.getHeader("X-Real-IP");
            String ja3 = request.getHeader("X-JA3-Fingerprint");

            // Broadcast the attack to the central event bus
            attackPublisher.publishAttackEvent(realIp, ja3, path, "Deception Filter Intercept");

            // Phase 5.4 - Neutralize Cloudflare Edge Caching (G11)
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);

            // Detach and route to the intelligent engine
            deceptionEngine.executeDeception(request, response);
            return; // Terminate normal filter chain execution
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Called by the L8-BCSM Master Filter when an attack is detected via consensus. Serves a highly
     * deceptive, fake response to waste attacker analysis time.
     */
    public void servePoisonedResponse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        logger.info(
                "AEGIS L4-ADA: Serving poisoned response to deceive scanner via Master Filter Command.");

        // Phase 5.4 - Neutralize Cloudflare Edge Caching (G11)
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        deceptionEngine.executeDeception(request, response);
    }
}
