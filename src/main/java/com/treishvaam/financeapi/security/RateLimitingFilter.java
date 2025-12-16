package com.treishvaam.financeapi.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe; // ADDED: For detailed token info
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  // Define limits as constants for easier tuning
  private static final long CAPACITY = 20;
  private static final long REFILL_TOKENS = 20;
  private static final Duration REFILL_DURATION = Duration.ofMinutes(1);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String uri = request.getRequestURI();

    // Apply strict limiting only to Auth and Contact endpoints (Versioned)
    if (uri.startsWith("/api/v1/auth") || uri.startsWith("/api/v1/contact")) {

      String clientIp = getClientIp(request);
      Bucket bucket = buckets.computeIfAbsent(clientIp, this::createNewBucket);

      // Enterprise Upgrade: Use 'tryConsumeAndReturnRemaining' for better headers
      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

      if (probe.isConsumed()) {
        // SUCCESS: Add remaining tokens header
        response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        response.addHeader("X-RateLimit-Limit", String.valueOf(CAPACITY));
        filterChain.doFilter(request, response);
      } else {
        // FAILURE: Add Retry-After header (in seconds)
        long waitForRefillNanos = probe.getNanosToWaitForRefill();
        long waitForRefillSeconds = waitForRefillNanos / 1_000_000_000;

        response.addHeader("X-RateLimit-Retry-After", String.valueOf(waitForRefillSeconds));
        response.setStatus(429); // Too Many Requests
        response
            .getWriter()
            .write(
                "{\"error\": \"Too many requests. Please try again in "
                    + waitForRefillSeconds
                    + " seconds.\"}");
      }
    } else {
      // Allow all other traffic (posts, markets, etc.) without strict limits
      filterChain.doFilter(request, response);
    }
  }

  private Bucket createNewBucket(String key) {
    // Allow 20 requests per minute
    Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(REFILL_TOKENS, REFILL_DURATION));
    // Use the modern Builder API
    return Bucket.builder().addLimit(limit).build();
  }

  private String getClientIp(HttpServletRequest request) {
    String xfHeader = request.getHeader("X-Forwarded-For");
    if (xfHeader == null) {
      return request.getRemoteAddr();
    }
    // X-Forwarded-For can be a comma-separated list; take the first one (client IP)
    return xfHeader.split(",")[0].trim();
  }
}
