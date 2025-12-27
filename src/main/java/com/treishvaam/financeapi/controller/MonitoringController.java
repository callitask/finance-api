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
  private static final String ALLOY_URL = "http://alloy:12347/collect";

  private final AudienceVisitRepository audienceVisitRepository;
  private final RestTemplate restTemplate;
  private UserAgentAnalyzer uaa;

  public MonitoringController(
      AudienceVisitRepository audienceVisitRepository, RestTemplateBuilder builder) {
    this.audienceVisitRepository = audienceVisitRepository;
    this.restTemplate = builder.build();
  }

  @PostConstruct
  public void init() {
    logger.info("Initializing UserAgentAnalyzer...");
    this.uaa = UserAgentAnalyzer.newBuilder().hideMatcherLoadStats().withCache(10000).build();
    logger.info("UserAgentAnalyzer initialized.");
  }

  @PostMapping("/ingest")
  public ResponseEntity<Void> ingest(
      @RequestBody FaroPayload payload, @RequestHeader Map<String, String> allHeaders) {

    // --- DEBUG LOGGING ---
    logger.info(
        "=== MONITORING INGEST DEBUG === | City: {} | Source: {}",
        allHeaders.get("x-visitor-city") != null
            ? allHeaders.get("x-visitor-city")
            : allHeaders.get("cf-ipcity"),
        payload.getExtra() != null ? payload.getExtra().get("trafficSource") : "NULL");

    String cfCountry = getHeader(allHeaders, "cf-ipcountry");
    String cfRegion = getHeader(allHeaders, "cf-region");
    String cfCity = getHeader(allHeaders, "cf-ipcity");

    String xCity = getHeader(allHeaders, "x-visitor-city");
    String xRegion = getHeader(allHeaders, "x-visitor-region");
    String xCountry = getHeader(allHeaders, "x-visitor-country");
    String userAgentString = getHeader(allHeaders, "user-agent");

    // Resolve Location
    String finalCity = resolveValue(xCity, cfCity, "Unknown");
    String finalRegion = resolveValue(xRegion, cfRegion, "Unknown");
    String finalCountry = resolveValue(xCountry, cfCountry, "Unknown");

    processAudienceAnalytics(payload, finalCountry, finalCity, finalRegion, userAgentString);
    forwardToAlloy(payload);

    return ResponseEntity.accepted().build();
  }

  private String getHeader(Map<String, String> headers, String key) {
    if (headers == null) return null;
    if (headers.containsKey(key)) return headers.get(key);
    for (String k : headers.keySet()) {
      if (k.equalsIgnoreCase(key)) return headers.get(k);
    }
    return null;
  }

  private String resolveValue(String primary, String secondary, String defaultValue) {
    if (primary != null && !primary.isEmpty() && !"Unknown".equalsIgnoreCase(primary))
      return primary;
    if (secondary != null && !secondary.isEmpty() && !"Unknown".equalsIgnoreCase(secondary))
      return secondary;
    return defaultValue;
  }

  @Async
  private void forwardToAlloy(FaroPayload payload) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<FaroPayload> request = new HttpEntity<>(payload, headers);
      restTemplate.postForLocation(ALLOY_URL, request);
    } catch (Exception e) {
      logger.debug("Alloy forward failed: {}", e.getMessage());
    }
  }

  private void processAudienceAnalytics(
      FaroPayload payload, String country, String city, String region, String userAgentString) {
    try {
      if (payload.getMeta() == null || payload.getMeta().getSession() == null) return;

      String sessionId = payload.getMeta().getSession().getId();
      LocalDate today = LocalDate.now();

      List<AudienceVisit> existingVisits =
          audienceVisitRepository.findBySessionIdAndDate(sessionId, today);

      AudienceVisit visit;
      if (!existingVisits.isEmpty()) {
        visit = existingVisits.get(0);
        int newEvents = payload.getEvents() != null ? payload.getEvents().size() : 0;
        visit.setViews(visit.getViews() + (newEvents > 0 ? 1 : 0));
        visit.setSessionDurationSeconds(visit.getSessionDurationSeconds() + 10);

        if (payload.getMeta().getUser() != null && payload.getMeta().getUser().getEmail() != null) {
          visit.setClientId(payload.getMeta().getUser().getEmail());
        }
      } else {
        visit = new AudienceVisit();
        visit.setSessionDate(today);
        visit.setSessionId(sessionId);

        // Identity Logic
        if (payload.getMeta().getUser() != null && payload.getMeta().getUser().getEmail() != null) {
          visit.setClientId(payload.getMeta().getUser().getEmail());
        } else if (payload.getExtra() != null && payload.getExtra().get("visitorId") != null) {
          visit.setClientId(payload.getExtra().get("visitorId"));
        } else {
          visit.setClientId(sessionId);
        }

        visit.setCountry(country);
        visit.setCity(city);
        visit.setRegion(region);

        String smartSource = "Direct/Faro";
        if (payload.getExtra() != null && payload.getExtra().containsKey("trafficSource")) {
          smartSource = payload.getExtra().get("trafficSource");
        }
        visit.setSessionSource(smartSource);

        String os = "Unknown";
        String osVer = "Unknown";
        String devModel = "Desktop";
        String devCat = "Desktop";

        if (payload.getMeta().getBrowser() != null) {
          os = payload.getMeta().getBrowser().getOs();
          osVer = payload.getMeta().getBrowser().getVersion();
          devModel = payload.getMeta().getBrowser().getName();
        }

        if (userAgentString != null && !userAgentString.isEmpty()) {
          try {
            UserAgent agent = uaa.parse(userAgentString);

            // --- MANUAL FIX FOR WINDOWS & ANDROID PARSING ---
            String bestOS = agent.getValue("OperatingSystemNameVersion");
            String simpleOS = agent.getValue("OperatingSystemName");
            String bestDevice = agent.getValue("DeviceName");
            String deviceClass = agent.getValue("DeviceClass");

            // Logic: Prefer NameVersion (e.g. "Android 12"), fallback to Name (e.g. "Android")
            if (bestOS != null && !bestOS.contains("??") && !bestOS.equalsIgnoreCase("Unknown")) {
              // Normalize "Windows NT 10.0" -> "Windows 10"
              if (bestOS.startsWith("Windows NT 10")) {
                os = "Windows 10";
                osVer = "";
              } else if (bestOS.startsWith("Windows")) {
                os = bestOS;
                osVer = "";
              } else {
                os = bestOS.split(" ")[0];
                osVer = bestOS.contains(" ") ? bestOS.substring(bestOS.indexOf(" ") + 1) : osVer;
              }
            } else if (simpleOS != null && !simpleOS.contains("??")) {
              os = simpleOS; // Fallback to just "Android" instead of "Android ??"
            }

            if (bestDevice != null
                && !bestDevice.contains("??")
                && !bestDevice.equalsIgnoreCase("Unknown")) {
              devModel = bestDevice;
            }

            if (deviceClass != null && !deviceClass.equalsIgnoreCase("Unknown")) {
              devCat = deviceClass;
            }
          } catch (Exception e) {
            logger.warn("UA Parsing issue: {}", e.getMessage());
          }
        }

        visit.setOperatingSystem(os);
        visit.setOsVersion(osVer);
        visit.setDeviceModel(devModel);
        visit.setDeviceCategory(devCat);

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
