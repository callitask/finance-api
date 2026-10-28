package com.treishvaam.financeapi.security;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Authenticates internal automated service requests targeting POST /api/v1/posts via
 * X-Internal-Secret.
 *
 * <p>Scope: - Runs exclusively on POST /api/v1/posts; skips all other endpoints.
 *
 * <p>Critical Dependencies: - Backend: SecurityConfig filter chain, INTERNAL_API_SECRET_KEY env
 * var.
 *
 * <p>Security Constraints: - Secret comparison MUST use constant-time MessageDigest.isEqual() to
 * prevent CWE-208 timing side-channel attacks. - Must never authenticate if expectedSecret is null
 * or blank.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (Security Remediation - Constant-Time
 * Secret Validation - 2026-10-27): Replaced `headerSecret.equals(expectedSecret)` with
 * `MessageDigest.isEqual()` over UTF-8 byte arrays and added an explicit non-blank guard on
 * `expectedSecret`. Why: Eliminates CWE-208 timing side-channel recovery of
 * `INTERNAL_API_SECRET_KEY` and prevents empty-secret authentication bypasses.
 *
 * <p>- DO-NOT-DELETE RULE (ABSOLUTE): This IMMUTABLE CHANGE HISTORY section acts as the
 * institutional memory for future AI sessions. It must never be deleted, truncated, rewritten, or
 * regenerated. Future AI must append only.
 */
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE - 1)
public class InternalSecretFilter extends OncePerRequestFilter {

    @Value("${app.security.internal-secret:}")
    private String expectedSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // 1. Do NOT filter the public sitemap URL.
        if ("/api/sitemap.xml".equals(path)) {
            return true; // Tells the filter to skip this request.
        }

        // 2. ONLY apply this filter to its intended endpoint (V1)
        // The filter should ONLY RUN for POST requests to /api/v1/posts.
        boolean shouldFilter =
                "POST".equalsIgnoreCase(request.getMethod()) && path.equals("/api/v1/posts");

        return !shouldFilter; // Return true to skip filtering for all other requests.
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String headerSecret = request.getHeader("X-Internal-Secret");

        if (headerSecret != null
                && !headerSecret.isBlank()
                && expectedSecret != null
                && !expectedSecret.isBlank()
                && MessageDigest.isEqual(
                        headerSecret.getBytes(StandardCharsets.UTF_8),
                        expectedSecret.getBytes(StandardCharsets.UTF_8))) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            "INTERNAL_SERVICE",
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
