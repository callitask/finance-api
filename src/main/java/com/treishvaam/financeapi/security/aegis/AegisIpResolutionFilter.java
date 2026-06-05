package com.treishvaam.financeapi.security.aegis;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Centralizes true client IP resolution and virtualizes the header space for the
 * entire Spring context.
 *
 * <p>Scope: - Wraps the HttpServletRequest to override getRemoteAddr(), getRemoteHost(),
 * getHeader(), and getHeaders(). - Intercepts proxy headers (X-Real-IP, X-Forwarded-For,
 * CF-Connecting-IP) and forces them to return the cryptographically verified X-Aegis-Client-IP.
 *
 * <p>Critical Dependencies: - Backend: SecurityConfig (MUST be registered explicitly via
 * FilterRegistrationBean at HIGHEST_PRECEDENCE + 1). - Upstream: AegisEdgeValidationFilter (MUST
 * run first to mathematically prove X-Aegis-Client-IP).
 *
 * <p>Security Constraints: - MUST execute AFTER AegisEdgeValidationFilter. - Under no circumstances
 * should the backend trust X-Real-IP from Nginx directly, as it exposes the proxy infrastructure to
 * false-positive tarpitting.
 *
 * <p>Non-Negotiables: - The header interception must be absolute. Any downstream library requesting
 * a standard IP header must receive the Zero-Trust verified IP.
 *
 * <p>Change Intent: - Seals the Nginx "X-Real-IP" header leak. Previously, AegisDeceptionFilter
 * bypassed getRemoteAddr() by querying getHeader("X-Real-IP") directly, which retrieved the
 * Cloudflare Edge IP and locked the system in a 500 error crash loop. This virtualization sanitizes
 * the entire request context.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation. • Resolves Reverse
 * Proxy IP Masking where Cloudflare node IPs were causing 500 error cascades due to false-positive
 * tarpit tagging. • Date/Phase: 2026-06-04 / Phase 6.
 *
 * <p>- EDITED (Phase 6.8 - Zero-Trust Header Virtualization): • Expanded the
 * HttpServletRequestWrapper to explicitly override `getHeader()` and `getHeaders()`. • Why:
 * Downstream filters (like L4-ADA) were bypassing the `getRemoteAddr()` wrapper by querying
 * `X-Real-IP` natively. Nginx was injecting the Cloudflare node IP into this header. By
 * intercepting the header query itself, we enforce a single, cryptographically proven source of
 * truth (`X-Aegis-Client-IP`) across the entire Spring ecosystem, hardening the Zero-Trust
 * perimeter.
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
import java.util.Collections;
import java.util.Enumeration;
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

        // Extract the True Client IP. At this stage in the filter chain, the
        // AegisEdgeValidationFilter
        // has already executed. If this request is here, the HMAC-SHA-512 signature is
        // mathematically
        // valid, meaning this IP is proven authentic and untampered by Cloudflare.
        final String trueClientIp = request.getHeader("X-Aegis-Client-IP");

        HttpServletRequestWrapper requestWrapper =
                new HttpServletRequestWrapper(request) {

                    private String getAegisIpFallback() {
                        if (trueClientIp != null && !trueClientIp.trim().isEmpty()) {
                            return trueClientIp;
                        }
                        // Fallback ONLY if the strict crypto boundary was legitimately bypassed
                        // (e.g., internal health checks from 127.0.0.1).
                        return super.getRemoteAddr();
                    }

                    @Override
                    public String getRemoteAddr() {
                        return getAegisIpFallback();
                    }

                    @Override
                    public String getRemoteHost() {
                        return getAegisIpFallback();
                    }

                    @Override
                    public String getHeader(String name) {
                        // VIRTUALIZE HEADERS: Seal the Nginx leak. If any downstream Spring
                        // component,
                        // rate limiter, or L4-ADA filter asks for proxy headers, forcefully return
                        // the cryptographically proven True Client IP.
                        if ("X-Real-IP".equalsIgnoreCase(name)
                                || "X-Forwarded-For".equalsIgnoreCase(name)
                                || "CF-Connecting-IP".equalsIgnoreCase(name)) {
                            return getAegisIpFallback();
                        }
                        return super.getHeader(name);
                    }

                    @Override
                    public Enumeration<String> getHeaders(String name) {
                        if ("X-Real-IP".equalsIgnoreCase(name)
                                || "X-Forwarded-For".equalsIgnoreCase(name)
                                || "CF-Connecting-IP".equalsIgnoreCase(name)) {
                            return Collections.enumeration(
                                    Collections.singletonList(getAegisIpFallback()));
                        }
                        return super.getHeaders(name);
                    }
                };

        filterChain.doFilter(requestWrapper, response);
    }
}
