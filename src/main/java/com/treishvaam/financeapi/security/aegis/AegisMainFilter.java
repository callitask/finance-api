/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Phase 5 Filter Chain Overlay. The master interceptor for AEGIS.
 *
 * <p>Scope: - Sits before Spring Security's native filters. - Interrogates the L8-BCSM for a
 * decision. - Routes to L4-ADA (Deception) or L7-CMCS (Tarpit) based on consensus.
 *
 * <p>Critical Dependencies: - AegisBcsm, AegisTemporalPathManager.
 *
 * <p>Security Constraints: - Must catch all exceptions to prevent fail-open scenarios.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation for Phase 5 integration.
 * • Connected Temporal Path manager to catch obfuscation bypass attempts. - EDITED: • Phase 3.2 ADA
 * Update: Updated deceptionFilter.servePoisonedResponse() calls to pass the HttpServletRequest
 * object, ensuring the Deception Engine has full context for strategy selection. • What behavior
 * must remain unchanged: L2-PPO and L8-BCSM routing logic. - EDITED (Current Phase - AEGIS L2-PPO
 * Canonical Path Unwrapping Fix): ROOT CAUSE: Authenticated requests to `/api/v1/auth/me` and
 * `/api/v1/analytics` returned 500 Internal Server Error. Cloudflare Edge translated routes to
 * obfuscated temporal URIs, but `AegisMainFilter` didn't wrap the request to pass the resolved
 * canonical path downstream. FIX: Implemented `CanonicalPathRequestWrapper` so downstream Spring
 * MVC controllers and Spring Security filter chains receive the resolved canonical URI. - EDITED
 * (Session - MTD Spring 6 Path Caching Fix): ROOT CAUSE: Spring Boot 3 / Spring 6 caches the parsed
 * `RequestPath` early in the filter chain (`ServletRequestPathUtils.PATH`).
 * `CanonicalPathRequestWrapper` alone failed because `DispatcherServlet` read the cached temporal
 * path `/api/v1/node/<hex>` instead of calling `getRequestURI()` on the wrapper, causing
 * `NoResourceFoundException` (500 Internal Server Error). FIX: Evicted Spring's cached path
 * attributes (`removeAttribute`) and implemented internal Servlet Forward Dispatch
 * (`request.getRequestDispatcher(resolvedPath).forward()`) to force Spring MVC to cleanly
 * re-evaluate the canonical path.
 */
package com.treishvaam.financeapi.security.aegis;

import com.treishvaam.financeapi.security.aegis.bcsm.AegisBcsm;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.mtd.AegisTemporalPathManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AegisMainFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AegisMainFilter.class);

    private final AegisBcsm bcsm;
    private final AegisTemporalPathManager temporalPathManager;
    private final AegisDeceptionFilter
            deceptionFilter; // Re-using Phase 2's Deception Filter for payload generation
    private final TarpitManager tarpitManager;

    public AegisMainFilter(
            AegisBcsm bcsm,
            AegisTemporalPathManager temporalPathManager,
            AegisDeceptionFilter deceptionFilter,
            TarpitManager tarpitManager) {
        this.bcsm = bcsm;
        this.temporalPathManager = temporalPathManager;
        this.deceptionFilter = deceptionFilter;
        this.tarpitManager = tarpitManager;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. Temporal Path Validation (L2-PPO)
            String rawUri = request.getRequestURI();
            String resolvedPath = temporalPathManager.resolveCanonicalPath(rawUri);

            if ("DECEPTION".equals(resolvedPath)) {
                // Attacker tried to access a hidden canonical path directly
                deceptionFilter.servePoisonedResponse(request, response);
                return;
            }

            // 2. Byzantine Consensus Evaluation (L8-BCSM)
            String sessionId = request.getSession().getId(); // Or extract from custom header
            SecurityDecision decision = bcsm.evaluate(request, sessionId);

            switch (decision) {
                case ALLOW:
                case WARN: // Warn just logs, still allows traffic
                    request.setAttribute("AEGIS_DECISION", decision.name());

                    // ZERO-TRUST MTD UNWRAPPING FOR SPRING BOOT 3 / SPRING 6:
                    // If the incoming URI was an MTD temporal route (/api/v1/node/<hex>),
                    // we must evict Spring's cached RequestPath attributes and use Forward Dispatch
                    // so DispatcherServlet cleanly maps to the canonical controller
                    // (/api/v1/analytics).
                    if (resolvedPath != null && !rawUri.equals(resolvedPath)) {
                        // Evict Spring 6 / Spring Boot 3 path pattern caches
                        request.removeAttribute(
                                "org.springframework.web.util.ServletRequestPathUtils.PATH");
                        request.removeAttribute(
                                "org.springframework.web.servlet.HandlerMapping.lookupPath");

                        // Wrap request to expose canonical URI attributes to downstream filters
                        HttpServletRequest wrappedRequest =
                                new CanonicalPathRequestWrapper(request, resolvedPath);

                        // Internal Servlet Forward guarantees Spring MVC routes to canonical
                        // endpoint
                        request.getRequestDispatcher(resolvedPath)
                                .forward(wrappedRequest, response);
                        return;
                    }

                    filterChain.doFilter(request, response);
                    return;

                case BLOCK:
                    response.setStatus(429); // Return 429 to obscure intent
                    response.getWriter().write("{\"error\":\"Request rejected\"}");
                    return;

                case DECEPTION:
                    deceptionFilter.servePoisonedResponse(request, response);
                    return;

                case TARPIT:
                case EMERGENCY:
                    tarpitManager.holdConnectionInTarpit(request, response);
                    return;
            }

        } catch (Exception e) {
            log.error("AEGIS MAIN FILTER CRITICAL FAILURE. Failsafe activated -> BLOCK", e);
            response.setStatus(500);
        }
    }

    /**
     * Preserves AEGIS MTD obfuscation at the network/edge layer while exposing the canonical URI to
     * internal Spring MVC controllers and Spring Security authorization rules.
     */
    private static class CanonicalPathRequestWrapper extends HttpServletRequestWrapper {
        private final String canonicalUri;

        public CanonicalPathRequestWrapper(HttpServletRequest request, String canonicalUri) {
            super(request);
            this.canonicalUri = canonicalUri;
        }

        @Override
        public String getRequestURI() {
            return this.canonicalUri;
        }

        @Override
        public String getServletPath() {
            return this.canonicalUri;
        }

        @Override
        public String getPathInfo() {
            return null; // Force Spring to evaluate full ServletPath
        }
    }
}
