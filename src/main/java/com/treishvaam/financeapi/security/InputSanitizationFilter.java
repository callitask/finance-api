package com.treishvaam.financeapi.security;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Global Servlet Filter to intercept all HTTP requests and sanitize parameters.
 *
 * <p>Scope: - Runs early in the Spring Security filter chain to drop malicious payloads before
 * authentication overhead.
 *
 * <p>Critical Dependencies: - QuerySanitizationService to analyze input payload.
 *
 * <p>Security Constraints: - Ensure it runs before RateLimitingFilter and Auth filters.
 *
 * <p>Non-Negotiables: - Must return an immediate 400 response without continuing the filter chain
 * if SQLi is detected.
 *
 * <p>Change Intent: - Wire SEC-10 implementation into the live HTTP processing cycle.
 *
 * <p>Future AI Guidance: - If specific endpoints require SQL-like strings (e.g. rich text editors),
 * they may need explicit exclusion logic here.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Phase 4 - SEC-10 Fix): • Created
 * InputSanitizationFilter. • Why it was added: Intercepts request parameters globally to apply
 * QuerySanitizationService validation. • Date: Phase 4 Implementation.
 */
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2) // Runs right after CORS filter
public class InputSanitizationFilter extends OncePerRequestFilter {

  @Autowired private QuerySanitizationService querySanitizationService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Prefer CF-Connecting-IP (Cloudflare real IP), fallback to RemoteAddr
    String clientIp =
        Optional.ofNullable(request.getHeader("CF-Connecting-IP"))
            .filter(ip -> !ip.isBlank())
            .orElse(request.getRemoteAddr());

    Enumeration<String> parameterNames = request.getParameterNames();
    while (parameterNames.hasMoreElements()) {
      String paramName = parameterNames.nextElement();
      String[] paramValues = request.getParameterValues(paramName);

      if (paramValues != null) {
        for (String value : paramValues) {
          if (querySanitizationService.isSuspicious(value, clientIp)) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid input detected. Request blocked.\"}");
            return; // Halt chain
          }
        }
      }
    }

    filterChain.doFilter(request, response);
  }
}
