/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Handle incoming Faro telemetry payloads and enrich them with accurate audience
 * data.
 *
 * <p>Scope: - Responsible for user-agent parsing (Device/OS mapping), traffic source resolution,
 * and location header extraction.
 *
 * <p>Security Constraints: - Must not trust or execute arbitrary string inputs. Data must be safely
 * mapped to entity fields.
 *
 * <p>Change Intent: - Fixed YAUAA parsing anomalies: Handled the "Windows >=10" MS frozen UA string
 * and mapped it to "Windows 10/11". - Hardened Browser parsing to strictly differentiate Chrome
 * from Edge and restored exact mobile hardware device names.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (LATEST): • Restored hardware priority
 * logic. If YAUAA detects a distinct device (not Desktop), it forcibly overrides any generic
 * browser name sent by Faro.
 *
 * <p>- EDITED (Incident 61/62 - GitOps Stale Code Resolution): • Removed local instantiation of
 * `UserAgentAnalyzer` in `@PostConstruct`. • Injected globally shared `UserAgentAnalyzer` bean via
 * constructor. • Why: A previous partial commit left the rogue instantiation on the server, causing
 * the JVM to OOM crash repeatedly by loading two massive YAUAA instances. This fully enforces the
 * singleton architecture.
 *
 * <p>- EDITED (Incident 71 - Hardware Granularity & OS Fallback Cleaning): • Standardized frozen
 * Windows 10/11 UA string resolution. • Removed the hardcoded "Desktop PC" string overwrite that
 * was permanently destroying Chrome/Edge tracking for all desktop users. • Cleaned up YAUAA `??`
 * artifacts.
 */
package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.analytics.AudienceVisit;
import com.treishvaam.financeapi.analytics.AudienceVisitRepository;
import com.treishvaam.financeapi.dto.FaroPayload;
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
    private final UserAgentAnalyzer uaa;

    public MonitoringController(
            AudienceVisitRepository audienceVisitRepository,
            RestTemplateBuilder builder,
            UserAgentAnalyzer uaa) {
        this.audienceVisitRepository = audienceVisitRepository;
        this.restTemplate = builder.build();
        this.uaa = uaa;
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

                if (payload.getMeta().getUser() != null
                        && payload.getMeta().getUser().getEmail() != null) {
                    visit.setClientId(payload.getMeta().getUser().getEmail());
                }
            } else {
                visit = new AudienceVisit();
                visit.setSessionDate(today);
                visit.setSessionId(sessionId);

                if (payload.getMeta().getUser() != null
                        && payload.getMeta().getUser().getEmail() != null) {
                    visit.setClientId(payload.getMeta().getUser().getEmail());
                } else if (payload.getExtra() != null
                        && payload.getExtra().get("visitorId") != null) {
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
                        if (event.getAttributes() != null
                                && event.getAttributes().containsKey("source")) {
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
                        if (event.getAttributes() != null
                                && event.getAttributes().containsKey("resolution")) {
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

                String os = "Unknown";
                String osVer = "Unknown";
                String devModel = "Desktop PC";
                String devCat = "Desktop";

                if (payload.getMeta().getBrowser() != null) {
                    os = payload.getMeta().getBrowser().getOs();
                    osVer = payload.getMeta().getBrowser().getVersion();
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
                        String agentVersion = agent.getValue("AgentVersion");

                        if (bestOS != null
                                && !bestOS.contains("??")
                                && !bestOS.equalsIgnoreCase("Unknown")) {
                            if (bestOS.contains("Windows >=10")
                                    || bestOS.contains("Windows NT 10.0")
                                    || bestOS.contains("Windows NT 11.0")) {
                                os = "Windows 10/11"; // YAUAA fallback for frozen UA string
                                osVer = "";
                            } else if (bestOS.startsWith("Windows NT 6.1")) {
                                os = "Windows 7";
                                osVer = "";
                            } else if (bestOS.startsWith("Windows")) {
                                os = bestOS;
                                osVer = "";
                            } else {
                                os = bestOS.split(" ")[0];
                                osVer =
                                        bestOS.contains(" ")
                                                ? bestOS.substring(bestOS.indexOf(" ") + 1)
                                                : osVer;
                            }
                        } else if (simpleOS != null && !simpleOS.contains("??")) {
                            os = simpleOS;
                        }

                        if ("Phone".equals(deviceClass)
                                || "Tablet".equals(deviceClass)
                                || "Mobile".equals(deviceClass)) {
                            devModel =
                                    (bestDevice != null
                                                    && !bestDevice.contains("??")
                                                    && !"Unknown".equalsIgnoreCase(bestDevice))
                                            ? bestDevice
                                            : deviceClass;
                        } else {
                            String browser =
                                    (agentName != null
                                                    && !agentName.contains("??")
                                                    && !"Unknown".equalsIgnoreCase(agentName))
                                            ? agentName
                                            : "Desktop PC";
                            if (browser.toLowerCase().contains("edge")) browser = "Edge";
                            if (browser.toLowerCase().contains("chrome")
                                    && !activeUserAgent.toLowerCase().contains("edg"))
                                browser = "Chrome";

                            String bVer =
                                    (agentVersion != null
                                                    && !agentVersion.contains("??")
                                                    && !"Unknown".equalsIgnoreCase(agentVersion))
                                            ? agentVersion.split("\\.")[0]
                                            : "";
                            devModel = (browser + " " + bVer).trim();
                        }

                        if (deviceClass != null
                                && !deviceClass.equalsIgnoreCase("Unknown")
                                && !deviceClass.contains("??")) {
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
