package com.treishvaam.financeapi.security;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Injects X-Request-ID for distributed tracing.
 *
 * <p>Scope: - Highest precedence filter to ensure logs contain request tracing early.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Phase 5 - ARCH-04): • Created
 * RequestIdFilter to propagate X-Request-ID.
 */
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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestIdFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String requestId =
        Optional.ofNullable(req.getHeader("X-Request-ID")).orElse(UUID.randomUUID().toString());

    MDC.put("requestId", requestId);
    res.setHeader("X-Request-ID", requestId);

    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove("requestId");
    }
  }
}
