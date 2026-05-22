package com.treishvaam.financeapi.security.aegis;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Implements AEGIS Layer 4 (Active Deception Architecture).
 *
 * <p>Scope: - Intercepts HTTP requests at the very start of the Spring Security filter chain. -
 * Detects overt vulnerability scanning (e.g., /.env, /wp-admin, simple SQLi). - Routes attackers
 * into the TarpitManager to exhaust their resources.
 *
 * <p>Critical Dependencies: - TarpitManager: Holds the TCP connection open indefinitely. -
 * RabbitMQAttackPublisher: Sends telemetry to Wazuh/Grafana.
 *
 * <p>Security Constraints: - Must operate efficiently to avoid bottlenecking legitimate traffic. -
 * Pattern matching must be pre-compiled and fail-fast.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (AEGIS Phase 2): • Initial creation of the
 * Chaos Mirror deception filter. • Designed to intercept botnet/scanner traffic and prevent it from
 * reaching actual controllers.
 */
@Component
@Order(-105) // Runs immediately after InputSanitizationFilter but before Auth
public class AegisDeceptionFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(AegisDeceptionFilter.class);

  // Common automated scanner and exploitation paths
  private static final Pattern HONEYPOT_PATHS =
      Pattern.compile("(?i)^/(\\.env|wp-admin|phpmyadmin|config\\.php|\\.git|vendor/phpunit).*");

  // Basic SQLi/XSS signatures that bypass ModSecurity (as a fallback layer)
  private static final Pattern MALICIOUS_PAYLOADS =
      Pattern.compile("(?i)(UNION\\s+SELECT|script>|\\.\\./\\.\\./|base64_decode\\()");

  private final TarpitManager tarpitManager;
  private final RabbitMQAttackPublisher attackPublisher;

  public AegisDeceptionFilter(
      TarpitManager tarpitManager, RabbitMQAttackPublisher attackPublisher) {
    this.tarpitManager = tarpitManager;
    this.attackPublisher = attackPublisher;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();
    String query = request.getQueryString() != null ? request.getQueryString() : "";

    if (HONEYPOT_PATHS.matcher(path).matches() || MALICIOUS_PAYLOADS.matcher(query).find()) {
      logger.warn(
          "AEGIS L4-ADA: Malicious probe detected on path [{}]. Initiating Chaos Mirror.", path);

      // Extract edge telemetry injected by OpenResty Lua (Phase 1)
      String realIp = request.getHeader("X-Real-IP");
      String ja3 = request.getHeader("X-JA3-Fingerprint");

      // Broadcast the attack to the central event bus
      attackPublisher.publishAttackEvent(realIp, ja3, path, "Deception Filter Intercept");

      // Detach and route to the tarpit
      tarpitManager.ensnare(response);
      return; // Terminate normal filter chain execution
    }

    filterChain.doFilter(request, response);
  }
}
