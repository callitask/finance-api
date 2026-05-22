/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Layer 2 Protocol Obfuscation & Timing Normalization. - Modifies response headers
 * and introduces Gaussian timing jitter.
 *
 * <p>Scope: - Injects 'X-Powered-By' and 'Server' headers with randomized plausible fakes. - Adds a
 * 5ms to 40ms Gaussian delay to requests to flatten timing analysis.
 *
 * <p>Critical Dependencies: - AegisEntropyManager (for true random Gaussian calculations). - Must
 * be registered in the Spring Security Filter Chain.
 *
 * <p>Security Constraints: - Must NOT alter the actual JSON body response structure to prevent
 * breaking downstream Next.js strict typing.
 *
 * <p>Non-Negotiables: - Thread.sleep() is safe here because Tomcat is configured to use Java 21
 * Virtual Threads (`spring.threads.virtual.enabled=true`).
 *
 * <p>Change Intent: - Defeat AI timing correlation attacks and Nmap fingerprinting.
 *
 * <p>Future AI Guidance: - If performance under extreme load degrades, reduce the max jitter cap,
 * but never remove the Gaussian distribution completely.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Header mutation logic. • Gaussian timing
 * delay. • Phase 1/2 Orchestration Batch.
 */
package com.treishvaam.financeapi.security.aegis.mtd;

import com.treishvaam.financeapi.security.aegis.crypto.AegisEntropyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AegisResponseMutator extends OncePerRequestFilter {

  private final AegisEntropyManager entropyManager;
  private final String[] fakePoweredBy = {
    "Treishvaam-Engine/3.4",
    "TVGX-Runtime/2.1",
    "Nexus-API/1.8",
    "Cortex-Serve/4.0",
    "ASP.NET",
    "PHP/8.2.1"
  };

  public AegisResponseMutator(AegisEntropyManager entropyManager) {
    this.entropyManager = entropyManager;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    SecureRandom random = entropyManager.getSecureRandom();

    // 1. Timing Normalization (Gaussian Jitter)
    // Center around 20ms, standard dev of 10ms. Limits: 5ms to 45ms.
    int jitterMs = (int) (random.nextGaussian() * 10 + 20);
    jitterMs = Math.max(5, Math.min(jitterMs, 45));

    try {
      Thread.sleep(jitterMs); // Safe due to Virtual Threads
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // 2. Header Mutation
    String fakeHeader = fakePoweredBy[random.nextInt(fakePoweredBy.length)];
    response.setHeader("X-Powered-By", fakeHeader);
    response.setHeader("Server", "AEGIS-M");

    // Ensure Cache-Control is strict to prevent edge caching of mutated responses
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");

    filterChain.doFilter(request, response);
  }
}
