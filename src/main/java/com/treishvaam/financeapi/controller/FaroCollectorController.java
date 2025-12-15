package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.analytics.AudienceVisit;
import com.treishvaam.financeapi.analytics.AudienceVisitRepository;
import com.treishvaam.financeapi.dto.FaroPayload;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/faro-collector")
public class FaroCollectorController {

  private static final Logger logger = LoggerFactory.getLogger(FaroCollectorController.class);
  private final AudienceVisitRepository audienceVisitRepository;

  public FaroCollectorController(AudienceVisitRepository audienceVisitRepository) {
    this.audienceVisitRepository = audienceVisitRepository;
  }

  @PostMapping("/collect")
  public ResponseEntity<Void> collect(
      @RequestBody FaroPayload payload,
      @RequestHeader(value = "CF-IPCountry", required = false) String cfCountry,
      @RequestHeader(value = "CF-IPCity", required = false) String cfCity,
      @RequestHeader(value = "CF-Region", required = false) String cfRegion) {

    try {
      if (payload.getMeta() == null || payload.getMeta().getSession() == null) {
        return ResponseEntity.accepted().build();
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
        // Simple logic: treat batches as activity, increment view slightly or by event count
        visit.setViews(visit.getViews() + (newEvents > 0 ? 1 : 0));
        // Extend duration slightly (naive implementation, better logic requires timestamps)
        visit.setSessionDurationSeconds(visit.getSessionDurationSeconds() + 10);
      } else {
        visit = new AudienceVisit();
        visit.setSessionDate(today);
        visit.setSessionId(sessionId);
        visit.setClientId(sessionId); // Use session ID as client ID for simplicity

        // Geo Data from Cloudflare Headers
        visit.setCountry(cfCountry != null ? cfCountry : "Unknown");
        visit.setCity(cfCity != null ? cfCity : "Unknown");
        visit.setRegion(cfRegion != null ? cfRegion : "Unknown");

        // Device/Browser Data from Faro
        if (payload.getMeta().getBrowser() != null) {
          visit.setOperatingSystem(payload.getMeta().getBrowser().getOs());
          visit.setOsVersion(payload.getMeta().getBrowser().getVersion());
          visit.setDeviceCategory("Desktop"); // Simplification, Faro gives this in headers usually
          visit.setDeviceModel(payload.getMeta().getBrowser().getName());
        }

        // Page Data
        if (payload.getMeta().getPage() != null) {
          visit.setLandingPage(payload.getMeta().getPage().getUrl());
        }

        visit.setSessionSource("Direct/Faro"); // Default source
        visit.setViews(1);
        visit.setSessionDurationSeconds(0L);
      }

      audienceVisitRepository.save(visit);
      logger.info("Processed Faro data for session: {}", sessionId);

    } catch (Exception e) {
      logger.error("Error processing Faro payload", e);
    }

    return ResponseEntity.accepted().build();
  }
}
