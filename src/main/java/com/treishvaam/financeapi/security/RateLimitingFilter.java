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

    String clientIp = request.getRemoteAddr();

    try {
      // Logic: Try to get bucket. If Redis fails, we catch exception and ALLOW request.
      // This ensures stability over strict rate limiting.
      Bucket bucket = resolveBucket(clientIp);

      if (bucket.tryConsume(1)) {
        filterChain.doFilter(request, response);
      } else {
        response.setStatus(429);
        response.getWriter().write("Too Many Requests");
      }
    } catch (Exception e) {
      // CRITICAL RESILIENCE: If Redis or Bucket4j fails, Log it but ALLOW the request.
      // Do NOT crash the application.
      logger.error("Rate Limiting Service Failed (Failing Open): {}", e.getMessage());
      filterChain.doFilter(request, response);
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
