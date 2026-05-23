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
 * must remain unchanged: L2-PPO and L8-BCSM routing logic.
 */
package com.treishvaam.financeapi.security.aegis;

import com.treishvaam.financeapi.security.aegis.bcsm.AegisBcsm;
import com.treishvaam.financeapi.security.aegis.bcsm.SecurityDecision;
import com.treishvaam.financeapi.security.aegis.mtd.AegisTemporalPathManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
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
            String resolvedPath = temporalPathManager.resolveCanonicalPath(request.getRequestURI());
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
}
