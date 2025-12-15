package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.analytics.AudienceVisit;
import com.treishvaam.financeapi.analytics.AudienceVisitRepository;
import com.treishvaam.financeapi.dto.FaroPayload;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringController {

  private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);

  // Internal Docker URL for Grafana Alloy Faro Receiver (Standard Port 12347)
  private static final String ALLOY_URL = "http://alloy:12347/collect";

  private final AudienceVisitRepository audienceVisitRepository;
  private final RestTemplate restTemplate;

  public MonitoringController(
      AudienceVisitRepository audienceVisitRepository, RestTemplateBuilder builder) {
    this.audienceVisitRepository = audienceVisitRepository;
    this.restTemplate = builder.build();
  }

  @PostMapping("/ingest")
  public ResponseEntity<Void> ingest(
      @RequestBody FaroPayload payload,
      @RequestHeader(value = "CF-IPCountry", required = false) String cfCountry,
      @RequestHeader(value = "CF-IPCity", required = false) String cfCity,
      @RequestHeader(value = "CF-Region", required = false) String cfRegion) {

    // 1. Process Custom Analytics (Database)
    processAudienceAnalytics(payload, cfCountry, cfCity, cfRegion);

    // 2. Forward to Grafana Alloy (Observability)
    forwardToAlloy(payload);

    return ResponseEntity.accepted().build();
  }

  @Async
  private void forwardToAlloy(FaroPayload payload) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<FaroPayload> request = new HttpEntity<>(payload, headers);
      restTemplate.postForLocation(ALLOY_URL, request);

    } catch (Exception e) {
      // Log minimally to avoid noise; Faro delivery is best-effort
      logger.debug("Failed to forward payload to Alloy: {}", e.getMessage());
    }
  }

  private void processAudienceAnalytics(
      FaroPayload payload, String cfCountry, String cfCity, String cfRegion) {
    try {
      if (payload.getMeta() == null || payload.getMeta().getSession() == null) {
        return;
      }

      String sessionId = payload.getMeta().getSession().getId();
      LocalDate today = LocalDate.now();

      // Check if we already tracked this session today
      Optional<AudienceVisit> existingVisit =
          audienceVisitRepository.findBySessionIdAndDate(sessionId, today);

      AudienceVisit visit;
      if (existingVisit.isPresent()) {
        visit = existingVisit.get();
        // Increment views if new events came in
        int newEvents = payload.getEvents() != null ? payload.getEvents().size() : 0;
        visit.setViews(visit.getViews() + (newEvents > 0 ? 1 : 0));
        // Extend duration slightly
        visit.setSessionDurationSeconds(visit.getSessionDurationSeconds() + 10);
      } else {
        visit = new AudienceVisit();
        visit.setSessionDate(today);
        visit.setSessionId(sessionId);
        visit.setClientId(sessionId);

        // Geo Data from Cloudflare Headers
        visit.setCountry(cfCountry != null ? cfCountry : "Unknown");
        visit.setCity(cfCity != null ? cfCity : "Unknown");
        visit.setRegion(cfRegion != null ? cfRegion : "Unknown");

        // Device/Browser Data from Faro
        if (payload.getMeta().getBrowser() != null) {
          visit.setOperatingSystem(payload.getMeta().getBrowser().getOs());
          visit.setOsVersion(payload.getMeta().getBrowser().getVersion());
          visit.setDeviceCategory("Desktop");
          visit.setDeviceModel(payload.getMeta().getBrowser().getName());
        }

        // Page Data
        if (payload.getMeta().getPage() != null) {
          visit.setLandingPage(payload.getMeta().getPage().getUrl());
        }

        visit.setSessionSource("Direct/Faro");
        visit.setViews(1);
        visit.setSessionDurationSeconds(0L);
      }

      audienceVisitRepository.save(visit);

    } catch (Exception e) {
      logger.error("Error saving audience analytics", e);
    }
  }
}
