/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Distributed rate limiting filter using Bucket4j to prevent abuse and brute force attacks.
 *
 * Scope:
 * - Intercepts all incoming API requests (except OPTIONS pre-flights) before authentication.
 *
 * Critical Dependencies:
 * - Depends on concurrent memory buckets (future: Redis backing for distributed environments).
 *
 * Security Constraints:
 * - Must fail-closed. If rate limiting logic throws an exception, the request must not proceed to downstream sensitive services.
 *
 * Non-Negotiables:
 * - Always skip OPTIONS requests to avoid breaking CORS.
 *
 * Change Intent:
 * - Fix CVE-003: Altered the catch block to return a 503 Service Unavailable instead of executing filterChain.doFilter(), closing the fail-open vulnerability.
 *
 * Future AI Guidance:
 * - When swapping `ConcurrentHashMap` for Redisson `ProxyManager` in P2, preserve the 503 fail-closed block.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - EDITED:
 * • Replaced `filterChain.doFilter(request, response)` in the exception catch block with a 503 status and JSON error response.
 * • Why the edit was required: Fix CVE-003 (Fail-open vulnerability leading to rate-limiter bypass under error conditions).
 * • What behavior must remain unchanged: OPTIONS pre-flight bypass.
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted,
 * truncated, rewritten, or regenerated.
 * Future AI must append only.
 */
package com.treishvaam.financeapi.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

  // In-memory fallback if Redis fails (Simple Map)
  private final Map<String, Bucket> localCache = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 1. SKIP OPTIONS (Pre-flight) requests
    // Rate limiting these causes CORS errors in browsers.
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    String clientIp = request.getRemoteAddr();

    try {
      // Logic: Try to get bucket.
      Bucket bucket = resolveBucket(clientIp);

      if (bucket.tryConsume(1)) {
        filterChain.doFilter(request, response);
      } else {
        response.setStatus(429);
        response.getWriter().write("Too Many Requests");
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

  private Bucket resolveBucket(String key) {
    return localCache.computeIfAbsent(key, k -> createNewBucket());
  }

  private Bucket createNewBucket() {
    // 100 requests per minute
    Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
    return Bucket.builder().addLimit(limit).build();
  }
}