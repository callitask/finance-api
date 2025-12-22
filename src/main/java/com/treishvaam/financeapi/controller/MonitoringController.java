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

    // --- DEBUG LOGGING (Check Docker Logs for this line) ---
    logger.info(
        "=== MONITORING INGEST DEBUG === | City: {} | UA: {}",
        allHeaders.get("x-visitor-city") != null
            ? allHeaders.get("x-visitor-city")
            : allHeaders.get("cf-ipcity"),
        allHeaders.get("user-agent"));

    // Extract Headers Case-Insensitively
    String cfCountry = getHeader(allHeaders, "cf-ipcountry");
    String cfRegion = getHeader(allHeaders, "cf-region");
    String cfCity = getHeader(allHeaders, "cf-ipcity");

    String xCity = getHeader(allHeaders, "x-visitor-city");
    String xRegion = getHeader(allHeaders, "x-visitor-region");
    String xCountry = getHeader(allHeaders, "x-visitor-country");
    String userAgentString = getHeader(allHeaders, "user-agent");

    // Resolve Location (Worker > Cloudflare > Unknown)
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

        // --- ENTERPRISE DEVICE PARSING (IMPROVED) ---
        String os = "Unknown";
        String osVer = "Unknown";
        String devModel = "Desktop";
        String devCat = "Desktop";

        // 1. Fallback to Faro
        if (payload.getMeta().getBrowser() != null) {
          os = payload.getMeta().getBrowser().getOs();
          osVer = payload.getMeta().getBrowser().getVersion();
          devModel = payload.getMeta().getBrowser().getName();
        }

        // 2. Yauaa Overrides
        if (userAgentString != null && !userAgentString.isEmpty()) {
          try {
            UserAgent agent = uaa.parse(userAgentString);

            // FIX: Use "OperatingSystemNameVersion" to get "Windows 10" instead of "Windows NT"
            String bestOS = agent.getValue("OperatingSystemNameVersion");
            String bestDevice = agent.getValue("DeviceName");
            String deviceClass = agent.getValue("DeviceClass");

            if (bestOS != null && !bestOS.contains("??") && !bestOS.equalsIgnoreCase("Unknown")) {
              // Split "Windows 10" -> OS="Windows 10", Ver="" (redundant)
              if (bestOS.startsWith("Windows")) {
                os = bestOS;
                osVer = "";
              } else {
                os = bestOS.split(" ")[0];
                osVer = bestOS.contains(" ") ? bestOS.substring(bestOS.indexOf(" ") + 1) : osVer;
              }
            } else {
              // Fallback for Android if NameVersion is weird
              String simpleOS = agent.getValue("OperatingSystemName");
              if (simpleOS != null && !simpleOS.contains("??")) os = simpleOS;
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
