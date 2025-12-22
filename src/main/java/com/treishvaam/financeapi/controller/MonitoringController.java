package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.analytics.AudienceVisit;
import com.treishvaam.financeapi.analytics.AudienceVisitRepository;
import com.treishvaam.financeapi.dto.FaroPayload;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
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

  // Enterprise User Agent Analyzer
  private UserAgentAnalyzer uaa;

  public MonitoringController(
      AudienceVisitRepository audienceVisitRepository, RestTemplateBuilder builder) {
    this.audienceVisitRepository = audienceVisitRepository;
    this.restTemplate = builder.build();
  }

  @PostConstruct
  public void init() {
    // Initialize Yauaa - this performs heavy caching on startup
    logger.info("Initializing UserAgentAnalyzer...");
    this.uaa = UserAgentAnalyzer.newBuilder().hideMatcherLoadStats().withCache(10000).build();
    logger.info("UserAgentAnalyzer initialized.");
  }

  @PostMapping("/ingest")
  public ResponseEntity<Void> ingest(
      @RequestBody FaroPayload payload,
      @RequestHeader Map<String, String> allHeaders) { // Capture ALL headers for debugging

    // --- DEBUG: Print Raw Headers & Payload ---
    // This will show us exactly what Nginx is passing to Java
    logger.info("=== MONITORING INGEST DEBUG ===");
    logger.info(
        "Headers received: CF-City={}, X-City={}, UA={}",
        allHeaders.get("cf-ipcity"),
        allHeaders.get("x-visitor-city"),
        allHeaders.get("user-agent"));

    // Extract headers manually from the map (Spring usually lowercases keys in this map)
    String cfCountry = allHeaders.getOrDefault("cf-ipcountry", "Unknown");
    String cfCity = allHeaders.getOrDefault("cf-ipcity", "Unknown");
    String cfRegion = allHeaders.getOrDefault("cf-region", "Unknown");

    String xCity = allHeaders.getOrDefault("x-visitor-city", "Unknown");
    String xRegion = allHeaders.getOrDefault("x-visitor-region", "Unknown");
    String xCountry = allHeaders.getOrDefault("x-visitor-country", "Unknown");
    String userAgentString = allHeaders.getOrDefault("user-agent", "");

    // Resolve Best Location Data
    String finalCity =
        (xCity != null && !xCity.equals("Unknown")) ? xCity : (cfCity != null ? cfCity : "Unknown");
    String finalRegion =
        (xRegion != null && !xRegion.equals("Unknown"))
            ? xRegion
            : (cfRegion != null ? cfRegion : "Unknown");
    String finalCountry =
        (xCountry != null && !xCountry.equals("Unknown"))
            ? xCountry
            : (cfCountry != null ? cfCountry : "Unknown");

    // 1. Process Custom Analytics (Database)
    processAudienceAnalytics(payload, finalCountry, finalCity, finalRegion, userAgentString);

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
      FaroPayload payload, String country, String city, String region, String userAgentString) {
    try {
      if (payload.getMeta() == null || payload.getMeta().getSession() == null) {
        return;
      }

      String sessionId = payload.getMeta().getSession().getId();
      LocalDate today = LocalDate.now();

      // FIX: Handle potential duplicate records (race condition) gracefully
      List<AudienceVisit> existingVisits =
          audienceVisitRepository.findBySessionIdAndDate(sessionId, today);

      AudienceVisit visit;
      if (!existingVisits.isEmpty()) {
        // Pick the first one if multiple exist
        visit = existingVisits.get(0);

        // Update Stats
        int newEvents = payload.getEvents() != null ? payload.getEvents().size() : 0;
        visit.setViews(visit.getViews() + (newEvents > 0 ? 1 : 0));
        visit.setSessionDurationSeconds(visit.getSessionDurationSeconds() + 10);

        // Update User Identity if available (e.g., user logged in mid-session)
        if (payload.getMeta().getUser() != null && payload.getMeta().getUser().getEmail() != null) {
          visit.setClientId(payload.getMeta().getUser().getEmail());
        }

      } else {
        visit = new AudienceVisit();
        visit.setSessionDate(today);
        visit.setSessionId(sessionId);

        // Identity: Use Email if logged in, otherwise VisitorID from Phase 2, otherwise SessionID
        if (payload.getMeta().getUser() != null && payload.getMeta().getUser().getEmail() != null) {
          visit.setClientId(payload.getMeta().getUser().getEmail());
        } else if (payload.getExtra() != null && payload.getExtra().get("visitorId") != null) {
          visit.setClientId(payload.getExtra().get("visitorId"));
        } else {
          visit.setClientId(sessionId);
        }

        // Location
        visit.setCountry(country);
        visit.setCity(city);
        visit.setRegion(region);

        // Intelligent Source Tracking (Phase 2)
        String smartSource = "Direct/Faro";
        if (payload.getExtra() != null && payload.getExtra().containsKey("trafficSource")) {
          smartSource = payload.getExtra().get("trafficSource");
        }
        visit.setSessionSource(smartSource);

        // Deep Device Parsing (Phase 3)
        // 1. Fallback to Faro Data
        String os = "Unknown";
        String osVer = "Unknown";
        String devModel = "Desktop";

        if (payload.getMeta().getBrowser() != null) {
          os = payload.getMeta().getBrowser().getOs();
          osVer = payload.getMeta().getBrowser().getVersion();
          devModel = payload.getMeta().getBrowser().getName();
        }

        // 2. Override with Enterprise Parser if User-Agent exists
        if (userAgentString != null && !userAgentString.isEmpty()) {
          try {
            UserAgent agent = uaa.parse(userAgentString);

            String parsedDeviceName = agent.getValue(UserAgent.DEVICE_NAME); // e.g., "Galaxy S21"
            String parsedDeviceClass = agent.getValue(UserAgent.DEVICE_CLASS); // e.g., "Phone"
            String parsedOsName =
                agent.getValue(UserAgent.OPERATING_SYSTEM_NAME); // e.g., "Android"
            String parsedOsVer = agent.getValue(UserAgent.OPERATING_SYSTEM_VERSION); // e.g., "11"

            if (parsedDeviceName != null && !parsedDeviceName.equals("Unknown")) {
              devModel = parsedDeviceName;
            }
            if (parsedOsName != null && !parsedOsName.equals("Unknown")) {
              os = parsedOsName;
            }
            if (parsedOsVer != null && !parsedOsVer.equals("Unknown")) {
              osVer = parsedOsVer;
            }
            if (parsedDeviceClass != null) {
              visit.setDeviceCategory(parsedDeviceClass);
            }
          } catch (Exception e) {
            logger.warn("UA Parsing failed, falling back to basic info");
          }
        }

        visit.setOperatingSystem(os);
        visit.setOsVersion(osVer);
        visit.setDeviceModel(devModel);

        // Page Data
        if (payload.getMeta().getPage() != null) {
          visit.setLandingPage(payload.getMeta().getPage().getUrl());
        }

        visit.setViews(1);
        visit.setSessionDurationSeconds(0L);
      }

      audienceVisitRepository.save(visit);

    } catch (Exception e) {
      logger.error("Error saving audience analytics", e);
    }
  }
}
