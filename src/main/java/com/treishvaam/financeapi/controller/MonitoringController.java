/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Handle incoming Faro telemetry payloads and enrich them with accurate audience
 * data.
 *
 * <p>Scope: - Responsible for user-agent parsing (Device/OS mapping), traffic source resolution,
 * and location header extraction. - Must never block or crash the main request path; telemetry
 * ingestion is asynchronous or fire-and-forget.
 *
 * <p>Critical Dependencies: - Backend: AudienceVisitRepository for saving analytics. - Frontend:
 * faroConfig.js which sends the enriched payload (resolution, userAgent, trafficSource). - Worker /
 * SEO / Sitemap: Receives headers injected by Cloudflare (cf-ipcity, cf-ipcountry, etc.).
 *
 * <p>Security Constraints: - Must not trust or execute arbitrary string inputs. Data must be safely
 * mapped to entity fields. - Fallbacks must be robust to prevent NPEs.
 *
 * <p>Non-Negotiables: - Must parse exact OS and Device model using YAUAA, not just generic platform
 * tags. - Desktop Browser model limitations apply (Hardware models cannot be extracted for PCs).
 *
 * <p>Change Intent: - Fixed YAUAA parsing anomalies: Handled the "Windows >=10" MS frozen UA string
 * and mapped it to "Windows 10/11". - Hardened Browser parsing to strictly differentiate Chrome
 * from Edge.
 *
 * <p>Future AI Guidance: - Do not attempt to parse laptop hardware models (e.g. Acer Nitro) from
 * Desktop User-Agents. It is fundamentally impossible via standard headers due to privacy limits.
 * Rely on the browser name instead.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Implemented robust User-Agent parsing
 * using YAUAA to replace Faro's default generic browser tags. • Added logic to extract and save
 * 'resolution' and explicit 'userAgent' from the payload's extra fields. • Implemented native
 * Referer header sniffing to bypass Faro generic source logging. - EDITED (LATEST): • Implemented
 * Edge vs Chrome disambiguation logic. • Cleanly mapped the frozen "Windows >=10" token string to
 * "Windows 10/11".
 */
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

    String cfCountry = getHeader(allHeaders, "cf-ipcountry");
    String cfRegion = getHeader(allHeaders, "cf-region");
    String cfCity = getHeader(allHeaders, "cf-ipcity");

    String xCity = getHeader(allHeaders, "x-visitor-city");
    String xRegion = getHeader(allHeaders, "x-visitor-region");
    String xCountry = getHeader(allHeaders, "x-visitor-country");

    String userAgentString = getHeader(allHeaders, "user-agent");
    String refererString = getHeader(allHeaders, "referer");

    String finalCity = resolveValue(xCity, cfCity, "Unknown");
    String finalRegion = resolveValue(xRegion, cfRegion, "Unknown");
    String finalCountry = resolveValue(xCountry, cfCountry, "Unknown");

    processAudienceAnalytics(
        payload, finalCountry, finalCity, finalRegion, userAgentString, refererString);
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
      FaroPayload payload,
      String country,
      String city,
      String region,
      String nativeUserAgent,
      String nativeReferer) {
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

        String smartSource = "Direct";

        if (payload.getEvents() != null && !payload.getEvents().isEmpty()) {
          for (FaroPayload.Event event : payload.getEvents()) {
            if (event.getAttributes() != null && event.getAttributes().containsKey("source")) {
              smartSource = event.getAttributes().get("source");
              break;
            }
          }
        }

        if (smartSource.equals("Direct")
            && payload.getExtra() != null
            && payload.getExtra().containsKey("trafficSource")) {
          smartSource = payload.getExtra().get("trafficSource");
        }

        if (smartSource.toLowerCase().contains("direct")
            || smartSource.toLowerCase().contains("faro")) {
          if (nativeReferer != null && !nativeReferer.isEmpty()) {
            String refLower = nativeReferer.toLowerCase();
            if (refLower.contains("google.")) smartSource = "Google Organic";
            else if (refLower.contains("bing.")) smartSource = "Bing Search";
            else if (refLower.contains("linkedin.")) smartSource = "LinkedIn";
            else if (refLower.contains("twitter.") || refLower.contains("t.co"))
              smartSource = "Twitter";
            else if (!refLower.contains("treishvaam")) smartSource = "Referral";
            else smartSource = "Internal";
          } else {
            smartSource = "Direct";
          }
        }
        visit.setSessionSource(smartSource);

        String resolution = "N/A";
        if (payload.getEvents() != null && !payload.getEvents().isEmpty()) {
          for (FaroPayload.Event event : payload.getEvents()) {
            if (event.getAttributes() != null && event.getAttributes().containsKey("resolution")) {
              resolution = event.getAttributes().get("resolution");
              break;
            }
          }
        }
        if (resolution.equals("N/A")
            && payload.getExtra() != null
            && payload.getExtra().containsKey("resolution")) {
          resolution = payload.getExtra().get("resolution");
        }
        visit.setScreenResolution(resolution);

        // --- DEVICE AND OS MAPPING ---
        String os = "Unknown";
        String osVer = "Unknown";
        String devModel = "Desktop";
        String devCat = "Desktop";

        if (payload.getMeta().getBrowser() != null) {
          os = payload.getMeta().getBrowser().getOs();
          osVer = payload.getMeta().getBrowser().getVersion();
          devModel = payload.getMeta().getBrowser().getName();
        }

        String activeUserAgent = nativeUserAgent;
        if (payload.getExtra() != null && payload.getExtra().containsKey("userAgent")) {
          activeUserAgent = payload.getExtra().get("userAgent");
        }

        if (activeUserAgent != null && !activeUserAgent.isEmpty()) {
          try {
            UserAgent agent = uaa.parse(activeUserAgent);

            String bestOS = agent.getValue("OperatingSystemNameVersion");
            String simpleOS = agent.getValue("OperatingSystemName");
            String bestDevice = agent.getValue("DeviceName");
            String deviceClass = agent.getValue("DeviceClass");
            String agentName = agent.getValue("AgentName");

            // 1. Operating System Mapping (Fixing Windows 10/11 Freezing issue)
            if (bestOS != null && !bestOS.contains("??") && !bestOS.equalsIgnoreCase("Unknown")) {
              if (bestOS.contains("Windows >=10")
                  || bestOS.contains("Windows NT 10.0")
                  || bestOS.contains("Windows NT 11.0")) {
                os = "Windows 10/11";
                osVer =
                    ""; // Abstracting version because 10 vs 11 is indistinguishable via User-Agent
              } else if (bestOS.startsWith("Windows NT 6.1")) {
                os = "Windows 7";
                osVer = "";
              } else if (bestOS.startsWith("Windows")) {
                os = bestOS;
                osVer = "";
              } else {
                os = bestOS.split(" ")[0]; // E.g., "Android" or "iOS"
                osVer = bestOS.contains(" ") ? bestOS.substring(bestOS.indexOf(" ") + 1) : osVer;
              }
            } else if (simpleOS != null && !simpleOS.contains("??")) {
              os = simpleOS;
            }

            // 2. Hardware Model Mapping (Note: Acer Nitro 5 will never be parsed from Desktop UA,
            // only mobile exposes hardware reliably)
            if (bestDevice != null
                && !bestDevice.contains("??")
                && !bestDevice.equalsIgnoreCase("Unknown")) {
              devModel = bestDevice;
            }

            // 3. Category Mapping
            if (deviceClass != null && !deviceClass.equalsIgnoreCase("Unknown")) {
              devCat = deviceClass;
            }

            // 4. Browser/Desktop Mapping (Fixing Edge/Chrome overlap)
            if (agentName != null
                && !agentName.contains("??")
                && !agentName.equalsIgnoreCase("Unknown")) {
              // Chrome user agents frequently contain 'Edg/' traces that confuse generic parsers
              if (devModel.equals("Desktop") || devModel.equalsIgnoreCase("Unknown")) {
                if (agentName.toLowerCase().contains("edge")) {
                  devModel = "Edge";
                } else if (activeUserAgent.toLowerCase().contains("chrome")
                    && !activeUserAgent.toLowerCase().contains("edg")) {
                  devModel = "Chrome";
                } else {
                  devModel = agentName;
                }
              }
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
