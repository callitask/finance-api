/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Distributed rate limiting filter using Bucket4j to prevent abuse and brute force
 * attacks.
 *
 * <p>Scope: - Intercepts all incoming API requests (except OPTIONS pre-flights) before
 * authentication.
 *
 * <p>Critical Dependencies: - Depends on Redis backing for distributed environments.
 *
 * <p>Security Constraints: - Must fail-closed. If rate limiting logic throws an exception, the
 * request must not proceed to downstream sensitive services.
 *
 * <p>Non-Negotiables: - Always skip OPTIONS requests to avoid breaking CORS.
 *
 * <p>Change Intent: - Fix CVE-003: Altered the catch block to return a 503 Service Unavailable
 * instead of executing filterChain.doFilter(), closing the fail-open vulnerability.
 *
 * <p>Future AI Guidance: - Preserve the 503 fail-closed block.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Replaced `filterChain.doFilter(request,
 * response)` in the exception catch block with a 503 status and JSON error response. • Why the edit
 * was required: Fix CVE-003 (Fail-open vulnerability leading to rate-limiter bypass under error
 * conditions). • What behavior must remain unchanged: OPTIONS pre-flight bypass.
 *
 * <p>- EDITED: • Replaced in-memory `ConcurrentHashMap` with Redis-backed `ProxyManager<String>`
 * using Lettuce (SEC-02). • Applied granular endpoint limits: AUTH_ATTEMPT_RPM (10),
 * PUBLIC_READ_RPM (200), AUTH_WRITE_RPM (60). • Extracted CF-Connecting-IP logic to prioritize
 * actual client IP behind Cloudflare. • Why: Resolves architecture flaw where distributed instances
 * had independent counters, allowing bypasses via IP rotation.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

  @Autowired private ProxyManager<String> proxyManager;

  // Granular limits for different endpoint categories
  private static final int PUBLIC_READ_RPM = 200; // GET market/posts
  private static final int AUTH_WRITE_RPM = 60; // POST/PUT/DELETE with auth
  private static final int AUTH_ATTEMPT_RPM = 10; // /auth/ endpoints (brute force protection)

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 1. SKIP OPTIONS (Pre-flight) requests
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    // Prefer CF-Connecting-IP (Cloudflare real IP), fallback to RemoteAddr
    String clientIp =
        Optional.ofNullable(request.getHeader("CF-Connecting-IP"))
            .filter(ip -> !ip.isBlank())
            .orElse(request.getRemoteAddr());

    String path = request.getRequestURI();
    int limit = determineLimit(path, request.getMethod());
    String bucketKey = clientIp + ":" + getCategoryKey(path);

    try {
      BucketConfiguration configuration =
          BucketConfiguration.builder()
              .addLimit(Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofMinutes(1))))
              .build();

      Bucket bucket = proxyManager.builder().build(bucketKey, configuration);

      if (bucket.tryConsume(1)) {
        filterChain.doFilter(request, response);
      } else {
        logger.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again in 60 seconds.\"}");
      }
    } catch (Exception e) {
      // CRITICAL RESILIENCE: Fail CLOSED under rate limiter exception conditions (CVE-003)
      logger.error("Rate Limiting Service Failed (Failing Closed): {}", e.getMessage());
      response.setStatus(503);
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"Service temporarily unavailable\"}");
      return; // DO NOT call filterChain.doFilter()
    }
  }

  private int determineLimit(String path, String method) {
    if (path.contains("/auth/")) return AUTH_ATTEMPT_RPM;
    if ("GET".equals(method)) return PUBLIC_READ_RPM;
    return AUTH_WRITE_RPM;
  }

  private String getCategoryKey(String path) {
    if (path.contains("/auth/")) return "auth";
    if (path.contains("/market/")) return "market";
    if (path.contains("/posts/")) return "posts";
    return "general";
  }
}
