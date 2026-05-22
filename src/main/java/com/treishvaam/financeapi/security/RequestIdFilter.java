package com.treishvaam.financeapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Request ID Tracing Across All Layers (ARCH-04).
 *
 * <p>Scope: - Intercepts all requests, extracts or generates an X-Request-ID, and populates MDC for
 * tracing.
 *
 * <p>Critical Dependencies: - Nginx: Must propagate X-Request-ID from the reverse proxy block.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created RequestIdFilter to propagate
 * X-Request-ID from Nginx through the JVM using MDC. • Why it was added: Architecture upgrade for
 * full distributed request traceability (ARCH-04).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String requestId =
                Optional.ofNullable(req.getHeader("X-Request-ID"))
                        .orElse(UUID.randomUUID().toString());

        MDC.put("requestId", requestId);
        res.setHeader("X-Request-ID", requestId);

        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("requestId");
        }
    }
}
