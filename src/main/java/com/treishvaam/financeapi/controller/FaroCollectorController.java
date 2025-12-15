package com.treishvaam.financeapi.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/faro-collector")
public class FaroCollectorController {

  private static final Logger logger = LoggerFactory.getLogger(FaroCollectorController.class);

  /**
   * Endpoint to receive Grafana Faro telemetry data. Accepts the payload and logs it (can be
   * extended to forward to Loki).
   */
  @PostMapping("/collect")
  public ResponseEntity<Void> collect(@RequestBody Map<String, Object> payload) {
    // Log reception to confirm connectivity.
    // Logging size avoids flooding logs with massive JSON dumps.
    logger.info(
        "Received Faro telemetry payload. Data points: {}", payload != null ? payload.size() : 0);

    return ResponseEntity.accepted().build();
  }
}
