package com.treishvaam.financeapi.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TARGET: Resilience4j Configuration PURPOSE: Configures the "Circuit Breaker" pattern. GOAL: If
 * external APIs (FMP, NewsData) fail, these settings stop our server from hanging.
 */
@Configuration
public class ResilienceConfig {

  /**
   * Defines the default behavior for all circuit breakers in the system. - Opens circuit (stops
   * requests) if 50% of requests fail. - Waits 30 seconds before testing if the external service is
   * back up. - Uses a sliding window of the last 10 requests to calculate the error rate.
   */
  @Bean
  public CircuitBreakerConfig defaultCircuitBreakerConfig() {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(50) // 50% failure rate opens the circuit
        .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s in "Open" state
        .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 test calls when "Half-Open"
        .slidingWindowSize(10) // Track the last 10 calls
        .build();
  }

  /** Registers the config in the application context. */
  @Bean
  public CircuitBreakerRegistry circuitBreakerRegistry(
      CircuitBreakerConfig defaultCircuitBreakerConfig) {
    return CircuitBreakerRegistry.of(defaultCircuitBreakerConfig);
  }
}
