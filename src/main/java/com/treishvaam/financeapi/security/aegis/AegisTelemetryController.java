package com.treishvaam.financeapi.security.aegis;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - REST Controller endpoint to securely absorb AEGIS L5-BIE biometric telemetry
 * beacons from the frontend.
 *
 * <p>Scope: - Provides a valid 202 Accepted routing target to prevent 404/500 errors when the
 * Next.js frontend transmits telemetry.
 *
 * <p>Critical Dependencies: - AegisMainFilter & AegisBehavioralEngine (which actually process the
 * headers before the request reaches here). - Frontend AegisTelemetry.tsx component.
 *
 * <p>Security Constraints: - Must remain public (permitAll) so anonymous sessions can be
 * fingerprinted.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial creation. • Why: The frontend was
 * POSTing telemetry data, but no explicit Controller mapping existed, causing Spring Boot to throw
 * a NoResourceFoundException which cascaded into a 500 error on the frontend.
 */
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/aegis")
public class AegisTelemetryController {

    private static final Logger log = LoggerFactory.getLogger(AegisTelemetryController.class);

    @PostMapping("/telemetry")
    public ResponseEntity<Void> receiveTelemetry(
            @RequestBody(required = false) Map<String, Object> payload) {
        // The actual biometric hash extraction and Shannon entropy validation occurs in the
        // AegisMainFilter and L5-BIE engine upstream in the security chain.
        // This endpoint simply provides a valid target for the frontend beacon to prevent routing
        // errors.
        log.debug("AEGIS L5-BIE: Telemetry beacon successfully absorbed from frontend.");

        return ResponseEntity.accepted().build();
    }
}
