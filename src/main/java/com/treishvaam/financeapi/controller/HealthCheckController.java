/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Public endpoints for internal Kubernetes/Docker health checks and external uptime
 * monitors.
 *
 * <p>Scope: - Extremely lightweight checks to verify application life state without blocking.
 *
 * <p>IMMUTABLE CHANGE HISTORY: - ADDED (Phase 6): Added explicit `/api/v1/health/ping` endpoint
 * returning JSON for external monitoring services (Better Uptime, Uptime Robot) while preserving
 * legacy `/health` string endpoint to prevent breaking existing ALB checks.
 */
package com.treishvaam.financeapi.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

  // Legacy internal check
  @GetMapping("/health")
  public ResponseEntity<String> checkHealth() {
    return ResponseEntity.ok("OK");
  }

  // Phase 6 - External monitoring service check
  @GetMapping("/api/v1/health/ping")
  public ResponseEntity<Map<String, String>> ping() {
    return ResponseEntity.ok(Map.of("status", "ok", "service", "treishvaam-finance-api"));
  }
}
