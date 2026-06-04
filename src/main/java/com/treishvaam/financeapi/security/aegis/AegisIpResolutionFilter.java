package com.treishvaam.financeapi.security.aegis;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Centralizes true client IP resolution for the entire Spring context.
 *
 * <p>Scope: - Wraps the HttpServletRequest to override getRemoteAddr() and getRemoteHost(). -
 * Prioritizes X-Aegis-Client-IP injected by the Cloudflare Edge Worker.
 *
 * <p>Critical Dependencies: - Backend: SecurityConfig (MUST be registered explicitly via
 * FilterRegistrationBean).
 *
 * <p>Security Constraints: - MUST execute AFTER AegisEdgeValidationFilter. If executed before, an
 * external attacker hitting Tomcat directly could spoof X-Aegis-Client-IP: 127.0.0.1 and bypass the
 * cryptographic edge signature check.
 *
 * <p>Non-Negotiables: - Fallback hierarchy must be maintained: X-Aegis-Client-IP ->
 * CF-Connecting-IP -> X-Real-IP
 *
 * <p>Change Intent: - Prevents Cloudflare Edge Server IPs from being incorrectly hostile-flagged by
 * downstream AegisDeceptionFilter and RateLimitingFilter implementations.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation. • Resolves Reverse
 * Proxy IP Masking where Cloudflare node IPs were causing 500 error cascades due to false-positive
 * tarpit tagging. • Date/Phase: 2026-06-04 / Phase 6.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section acts as the institutional memory
 * for future AI sessions. It must never be deleted, truncated, rewritten, or regenerated. Future AI
 * must append only.
 */
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AegisIpResolutionFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AegisIpResolutionFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpServletRequestWrapper requestWrapper =
                new HttpServletRequestWrapper(request) {
                    @Override
                    public String getRemoteAddr() {
                        String aegisClientIp = request.getHeader("X-Aegis-Client-IP");
                        if (aegisClientIp != null && !aegisClientIp.isEmpty()) {
                            return aegisClientIp;
                        }

                        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
                        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
                            return cfConnectingIp;
                        }

                        String xRealIp = request.getHeader("X-Real-IP");
                        if (xRealIp != null && !xRealIp.isEmpty()) {
                            return xRealIp;
                        }

                        return super.getRemoteAddr();
                    }

                    @Override
                    public String getRemoteHost() {
                        return getRemoteAddr();
                    }
                };

        filterChain.doFilter(requestWrapper, response);
    }
}
