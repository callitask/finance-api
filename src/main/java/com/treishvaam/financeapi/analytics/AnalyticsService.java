/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Service for fetching Google Analytics 4 (GA4) historical data and orchestrating
 * local Audience Dashboard queries.
 *
 * <p>Security Constraints: - The refreshGA4Data MUST use a @Transactional block and explicit
 * repository.flush() to ensure data is strictly dropped before the refetch occurs. - GA4 Fetch MUST
 * use 30-day backward chunking to prevent API quota timeouts over large historical date ranges.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Mapped Temporal classes to String
 * manually in `mapEntityToDto` to prevent JS Date errors. • Added explicit
 * `audienceVisitRepository.flush()` during manual GA4 sync to fix stale data reappearing. •
 * Implemented OS/Browser version sanitization in `mapEntityToDto` to intercept and normalize Faro
 * Chrome version leaks (e.g., 148.0.0.0) and mask Apple/Windows naming conventions properly before
 * sending to the UI.
 *
 * <p>- FAILED / REJECTED ATTEMPTS: • Tried to wipe Faro data during GA4 sync to replace it.
 * REJECTED: Wiping `sessionId != 'Not available (GA4)'` permanently destroys high-resolution
 * tracking (timestamps, clientIDs, User-ID groupings).
 *
 * <p>- EDITED (LATEST): • Implemented Smart Attribution Enrichment (`fetchAndEnrichGAData`) to map
 * GA4 sources directly onto existing Faro rows using a daily statistical pool, rather than deleting
 * Faro rows. • Added 30-day backward chunking to bypass GA4 API length limitations. • Added Android
 * Chrome Hardware Masking sanitization in `mapEntityToDto`.
 *
 * <p>- EDITED (Hotfix): • Resynchronized AnalyticsService method signatures (getHistoricalData,
 * getFilterOptions, refreshGA4Data) with AudienceFilter to resolve Maven compilation failures in
 * CI/CD pipeline.
 *
 * <p>- EDITED (Phase 10): • Added queryBigQueryRawEvents using Google Cloud BigQuery API for
 * un-sampled, raw event extraction.
 *
 * <p>- EDITED (Phase 5.5): • Added `purgeOldAnalyticsEvents` scheduled task and autowired
 * `AnalyticsEventRepository`. • Why: Data retention policy to auto-purge raw events older than 365
 * days, preventing unbounded table growth and ensuring DPDP Act 2023 compliance.
 *
 * <p>- EDITED (Phase 5.5 Hotfix): • Fixed Maven Compilation Error by importing
 * `com.treishvaam.financeapi.repository.AnalyticsEventRepository`. • Corrected type mismatch:
 * `deleteEventsOlderThan` now correctly uses `Instant` and `ChronoUnit` and handles `void` return
 * type instead of `LocalDateTime` and `int`.
 *
 * <p>- EDITED (Incident 31 - DB Bottleneck & Feature Toggling): • Eliminated DB Bottleneck:
 * Replaced the `IN(...)` parameter explosion query in `getHistoricalData` with
 * `findAllFirstVisitDatesUpTo`, utilizing a high-speed database-level `GROUP BY`. • Free-Tier
 * Compliance: Injected `${ga4.bigquery.enabled:false}` feature toggle into `queryBigQueryRawEvents`
 * to strictly enforce $0.00 cost architecture without stripping the code. • Data Unification: Added
 * `syncAegisTelemetryToAudienceVisits` to bridge the read/write gap, ensuring raw telemetry flows
 * into the Audience dashboard automatically.
 *
 * <p>- EDITED (Incident 32 - CI/CD Type Conversion Fix): • Fixed `incompatible types: int cannot be
 * converted to java.lang.Long` compilation error in `syncAegisTelemetryToAudienceVisits`. Safely
 * cast JPA aggregation returns (`SUM`, `COUNT`) using `((Number) row[X]).longValue()` to natively
 * satisfy Hibernate Dialect variations and entity setter signatures without illegal primitive
 * autoboxing constraints.
 *
 * <p>- EDITED (Incident 34 - Telemetry Bridge Resilience & Timezone Alignment): • Added synchronous
 * invocation of `syncAegisTelemetryToAudienceVisits()` to `@PostConstruct init()` to immediately
 * backfill pending telemetry upon container reboot. • Upgraded telemetry roll-up schedule from
 * daily cron to `@Scheduled(fixedDelay = 300000)` (5 mins). • Increased lookback window from 2 days
 * to 7 days to cover weekend/holiday deployment gaps. • Aligned `sessionDate` parsing to
 * `ZoneId.of("Asia/Kolkata")` to prevent evening IST telemetry from drifting across UTC midnight
 * boundaries and falling out of frontend date-picker bounds.
 *
 * <p>- EDITED (Incident 36 - Telemetry Fidelity & YAUAA Integration): • Integrated YAUAA engine to
 * parse User-Agents locally, enforcing the $0.00 Free-Tier Mandate. Expanded JPQL query to natively
 * extract path, referrer, and userAgent. Implemented native referrer domain resolution. Normalized
 * `mapEntityToDto` string variations.
 *
 * <p>- EDITED (Incident 43 - Device Clustering & Fingerprint Roll-Up Fix): • Updated
 * `syncAegisTelemetryToAudienceVisits` to extract `MAX(a.deviceFingerprint)` and persist it to
 * `AudienceVisit`. Updated `mapEntityToDto` to correctly pass `deviceBrand` and `deviceClass` to
 * `AudienceDataDto`. • Date: 2026-08-05
 *
 * <p>- EDITED (Incident 48/49 - Enterprise Data Healer Transaction Chunking): • Replaced
 * massive @Transactional boundary with chunk-based TransactionTemplate. • Updated JPQL to
 * bulk-aggregate metrics (timeOnPageMs, scrollDepth) to preserve data fidelity during healing
 * without N+1 locks.
 *
 * <p>- EDITED (Incident 50 - CI/CD Compilation Fix): • Isolated the `visitPage` loop variable into
 * a `final` local variable (`currentBatch`) before passing it to the `TransactionTemplate` lambda,
 * resolving the 'effectively final' Maven compilation failure.
 *
 * <p>- EDITED (Incident 54/55 - Cron OOM Protection & Chronological Mapping Resolution): • Stripped
 * `@Transactional` from `syncAegisTelemetryToAudienceVisits` and integrated `TransactionTemplate`
 * chunking. Prevents scheduled background jobs from exhausting HikariCP connections and causing 504
 * Gateway Timeouts. • Re-engineered JPQL string aggregation: Replaced `MIN(a.path)` (which
 * incorrectly sorted URLs alphabetically, dropping valid landing pages) with an in-memory
 * `a.createdAt ASC` linear grouping. The first element in the collection mathematically guarantees
 * true chronological landing paths and referrers without expensive SQL subqueries. • Integrated
 * proper `timeOnPageMs` summation logic to support the restored frontend Faro tracking.
 *
 * <p>- EDITED (Incident 57/58/59 - JVM OOM Prevention): • Removed local instantiation of
 * `UserAgentAnalyzer` in `@PostConstruct`. • Injected globally shared `UserAgentAnalyzer` bean via
 * constructor to eliminate massive duplicate memory footprint.
 *
 * <p>- EDITED (Incident 66 - Landing Page Override Fix): • Appended `a.id ASC` to the telemetry
 * roll-up JPQL query. • Why: Resolves a chronological sorting failure where events inserted in the
 * same millisecond caused MariaDB to sort non-deterministically, frequently allowing `/dashboard`
 * to alphabetically override the true `/` entry point.
 *
 * <p>- EDITED (Incident 71 - Hardware Granularity & OS Fallback Cleaning): • Removed the hardcoded
 * "Desktop PC" string overwrite in `enrichDeviceAndOsFromUserAgent()`. • Added YAUAA `??` artifact
 * stripping to prevent corrupted strings from leaking into DB. • Mapped frozen Windows 10/11 UA
 * strings predictably.
 *
 * <p>- EDITED (Incident 72 - High Entropy Client Hints): • Updated `enrichDeviceAndOsFromUserAgent`
 * and `syncAegisTelemetryToAudienceVisits` to dynamically evaluate the frontend `platformVersion`
 * hint.
 *
 * <p>- EDITED (Incident 74/Phase 7 - P0 Null Pointer & Data Fidelity): • Fixed P0
 * NullPointerException in `syncAegisTelemetryToAudienceVisits` by implementing null-safe
 * `visit.getSessionDurationSeconds()` unboxing. • Added `platformVersion` parameter to
 * `enrichDeviceAndOsFromUserAgent` to accurately differentiate Windows 11 vs Windows 10 based on
 * Client Hints. • Added `screenResolution` capture to the roll-up logic.
 *
 * <p>- EDITED (Incident 76 - MariaDB Typed-NULL PreparedStatement Fix): • Replaced monolithic JPQL
 * queries with dynamic JPA Specification CriteriaBuilder in `getHistoricalData` and
 * `getFilterOptions`. • Added `getDistinctValues` helper to natively strip NULL elements from
 * dropdown DTOs, preventing React render crashes on missing hardware telemetry.
 *
 * <p>- EDITED (Incident 77 - YAUAA Hardware Leak Fix): • Updated frozen Windows OS fallback to
 * capture `finalOsVer.startsWith(">=10")`. • Why: YAUAA extracts ">=10" for Chromium on Windows 11
 * without Client Hints, which previously failed the `.startsWith("10")` check and leaked raw
 * `Windows NT` strings into MariaDB.
 */
package com.treishvaam.financeapi.analytics;

import com.google.analytics.data.v1beta.BetaAnalyticsDataClient;
import com.google.analytics.data.v1beta.BetaAnalyticsDataSettings;
import com.google.analytics.data.v1beta.DateRange;
import com.google.analytics.data.v1beta.Dimension;
import com.google.analytics.data.v1beta.Metric;
import com.google.analytics.data.v1beta.RunReportRequest;
import com.google.analytics.data.v1beta.RunReportResponse;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.treishvaam.financeapi.repository.AnalyticsEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);
    private static final DateTimeFormatter GA_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Value("${ga4.property-id}")
    private String propertyId;

    @Value("${ga4.credentials-path}")
    private String credentialsPath;

    @Value("${ga4.bigquery.project-id:#{null}}")
    private String bqProjectId;

    @Value("${ga4.bigquery.dataset-id:#{null}}")
    private String bqDatasetId;

    // Feature toggle to enforce Free-Tier Mandate. Defaults to false.
    @Value("${ga4.bigquery.enabled:false}")
    private boolean bigQueryEnabled;

    @Value("${ga4.initial-fetch-start-date:2024-01-01}")
    private String initialFetchStartDate;

    private BetaAnalyticsDataClient analyticsDataClient;
    private final AudienceVisitRepository audienceVisitRepository;
    private final TransactionTemplate transactionTemplate;
    private final UserAgentAnalyzer uaa;

    @Autowired private AnalyticsEventRepository analyticsEventRepository;
    @PersistenceContext private EntityManager entityManager;

    public AnalyticsService(
            AudienceVisitRepository audienceVisitRepository,
            TransactionTemplate transactionTemplate,
            UserAgentAnalyzer uaa) {
        this.audienceVisitRepository = audienceVisitRepository;
        this.transactionTemplate = transactionTemplate;
        this.uaa = uaa;
    }

    @PostConstruct
    public void init() {
        try {
            syncAegisTelemetryToAudienceVisits();
        } catch (Exception e) {
            logger.error("[AnalyticsService] Startup telemetry roll-up failed.", e);
        }

        if (credentialsPath == null || credentialsPath.isEmpty()) {
            logger.warn("GA4 Credentials path is empty. Skipping client initialization.");
            this.analyticsDataClient = null;
            return;
        }

        try {
            File credentialsFile = new File(credentialsPath);
            if (!credentialsFile.exists()) {
                logger.error("GA4 Credentials file not found at: {}", credentialsPath);
                this.analyticsDataClient = null;
                return;
            }

            GoogleCredentials credentials =
                    GoogleCredentials.fromStream(new FileInputStream(credentialsFile))
                            .createScoped(
                                    Collections.singletonList(
                                            "https://www.googleapis.com/auth/analytics.readonly"));

            BetaAnalyticsDataSettings settings =
                    BetaAnalyticsDataSettings.newBuilder()
                            .setCredentialsProvider(() -> credentials)
                            .build();

            this.analyticsDataClient = BetaAnalyticsDataClient.create(settings);
            logger.info(
                    "Google Analytics Data Client initialized successfully for Property: {}",
                    propertyId);

            initialHistoricalFetch();
            dailyIncrementalFetch();

        } catch (Exception e) {
            logger.error("Failed to initialize Google Analytics Data Client (GA4).", e);
            this.analyticsDataClient = null;
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldAnalyticsEvents() {
        if (analyticsEventRepository != null) {
            Instant cutoff = Instant.now().minus(365, ChronoUnit.DAYS);
            analyticsEventRepository.deleteEventsOlderThan(cutoff);
            logger.info("[AnalyticsRetention] Purged raw events older than 365 days.");
        }
    }

    @Scheduled(fixedDelay = 300000) // Executes every 5 minutes (300,000 ms)
    public void syncAegisTelemetryToAudienceVisits() {
        logger.info("[AnalyticsBridge] Starting AEGIS/Faro Telemetry Roll-Up...");
        try {
            Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

            // 1. Fetch only the distinct session IDs in the time window to protect memory
            List<String> activeSessionIds =
                    entityManager
                            .createQuery(
                                    "SELECT DISTINCT a.sessionId FROM AnalyticsEvent a WHERE a.createdAt > :cutoff AND a.sessionId IS NOT NULL",
                                    String.class)
                            .setParameter("cutoff", cutoff)
                            .getResultList();

            // Filter out GA4 dummy IDs to prevent pollution
            activeSessionIds =
                    activeSessionIds.stream()
                            .filter(id -> !id.isEmpty() && !"Not available (GA4)".equals(id))
                            .collect(Collectors.toList());

            if (activeSessionIds.isEmpty()) {
                logger.info("[AnalyticsBridge] No new telemetry to sync.");
                return;
            }

            int batchSize = 500;
            int syncedCount = 0;

            // 2. Process in Transactional Chunks to prevent 504 Timeouts and JVM OOM crashes
            for (int i = 0; i < activeSessionIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, activeSessionIds.size());
                final List<String> batchIds = activeSessionIds.subList(i, end);

                Integer chunkCount =
                        transactionTemplate.execute(
                                status -> {
                                    int processedInChunk = 0;

                                    // Fetch full events for this batch, ordered chronologically to
                                    // bypass MIN(path) alphabetical flaws. a.id ASC prevents
                                    // millisecond collisions.
                                    List<com.treishvaam.financeapi.model.AnalyticsEvent> events =
                                            entityManager
                                                    .createQuery(
                                                            "SELECT a FROM AnalyticsEvent a WHERE a.sessionId IN :ids ORDER BY a.sessionId, a.createdAt ASC, a.id ASC",
                                                            com.treishvaam.financeapi.model
                                                                    .AnalyticsEvent.class)
                                                    .setParameter("ids", batchIds)
                                                    .getResultList();

                                    // Group by Session ID linearly in memory
                                    Map<
                                                    String,
                                                    List<
                                                            com.treishvaam.financeapi.model
                                                                    .AnalyticsEvent>>
                                            sessionEventMap = new HashMap<>();
                                    for (com.treishvaam.financeapi.model.AnalyticsEvent e :
                                            events) {
                                        sessionEventMap
                                                .computeIfAbsent(
                                                        e.getSessionId(), k -> new ArrayList<>())
                                                .add(e);
                                    }

                                    for (Map.Entry<
                                                    String,
                                                    List<
                                                            com.treishvaam.financeapi.model
                                                                    .AnalyticsEvent>>
                                            entry : sessionEventMap.entrySet()) {
                                        String sessionId = entry.getKey();
                                        List<com.treishvaam.financeapi.model.AnalyticsEvent>
                                                userEvents = entry.getValue();

                                        // The first event is mathematically guaranteed to be the
                                        // chronological entry point
                                        com.treishvaam.financeapi.model.AnalyticsEvent firstEvent =
                                                userEvents.get(0);

                                        LocalDate sessionDate =
                                                firstEvent
                                                        .getCreatedAt()
                                                        .atZone(ZoneId.of("Asia/Kolkata"))
                                                        .toLocalDate();

                                        // Check if we need to create or update
                                        List<AudienceVisit> existing =
                                                audienceVisitRepository.findBySessionIdAndDate(
                                                        sessionId, sessionDate);
                                        AudienceVisit visit =
                                                existing.isEmpty()
                                                        ? new AudienceVisit()
                                                        : existing.get(0);

                                        visit.setSessionId(sessionId);
                                        visit.setSessionDate(sessionDate);
                                        visit.setClientId(sessionId);
                                        visit.setCountry(firstEvent.getCountryCode());
                                        visit.setCity(firstEvent.getCity());
                                        visit.setDeviceCategory(firstEvent.getDeviceType());

                                        // Aggregate Engagement Metrics correctly (Restores '0s'
                                        // Engagement Time)
                                        long totalDurationMs = 0;
                                        for (com.treishvaam.financeapi.model.AnalyticsEvent evt :
                                                userEvents) {
                                            if (evt.getTimeOnPageMs() != null) {
                                                totalDurationMs += evt.getTimeOnPageMs();
                                            }
                                        }

                                        // Update metrics if higher than existing (NULL SAFE P0 FIX)
                                        long newDurationSecs = totalDurationMs / 1000L;
                                        long existingDuration =
                                                visit.getSessionDurationSeconds() != null
                                                        ? visit.getSessionDurationSeconds()
                                                        : 0L;
                                        if (newDurationSecs > existingDuration) {
                                            visit.setSessionDurationSeconds(newDurationSecs);
                                        }

                                        int existingViews =
                                                visit.getViews() != null ? visit.getViews() : 0;
                                        visit.setViews(Math.max(existingViews, userEvents.size()));

                                        // Chronological Landing Page & Referrer
                                        String rawPath = firstEvent.getPath();
                                        String rawReferrer = firstEvent.getReferrer();
                                        String rawUserAgent = firstEvent.getUserAgent();
                                        String deviceFingerprint =
                                                firstEvent.getDeviceFingerprint();
                                        String platformVersion = firstEvent.getPlatformVersion();
                                        String screenResolution = firstEvent.getScreenResolution();

                                        // Only set landing page if it hasn't been set, or if it was
                                        // defaulted
                                        if (visit.getLandingPage() == null
                                                || visit.getLandingPage()
                                                        .equals("Not available (GA4)")
                                                || visit.getLandingPage().equals("/")) {
                                            visit.setLandingPage(
                                                    (rawPath != null && !rawPath.isEmpty())
                                                            ? rawPath
                                                            : "/");
                                        }
                                        visit.setSessionSource(resolveSessionSource(rawReferrer));

                                        if (deviceFingerprint != null
                                                && !deviceFingerprint.isEmpty()) {
                                            visit.setDeviceFingerprint(deviceFingerprint);
                                        }

                                        if (screenResolution != null
                                                && !screenResolution.isEmpty()) {
                                            visit.setScreenResolution(screenResolution);
                                        }

                                        enrichDeviceAndOsFromUserAgent(
                                                visit,
                                                firstEvent.getOs(),
                                                firstEvent.getBrowser(),
                                                rawUserAgent,
                                                platformVersion);

                                        audienceVisitRepository.save(visit);
                                        processedInChunk++;
                                    }

                                    audienceVisitRepository.flush();
                                    entityManager.clear();
                                    return processedInChunk;
                                });
                syncedCount += (chunkCount != null ? chunkCount : 0);
            }

            logger.info(
                    "[AnalyticsBridge] Roll-Up Complete. Synced/Updated {} telemetry sessions.",
                    syncedCount);
        } catch (Exception e) {
            logger.error("[AnalyticsBridge] Failed to synchronize telemetry.", e);
        }
    }

    private String resolveSessionSource(String referrer) {
        if (referrer == null
                || referrer.trim().isEmpty()
                || referrer.contains("treishvaamfinance.com")
                || referrer.contains("treishvaamagro.com")
                || referrer.contains("treishvaamgroup.com")) {
            return "Direct";
        }
        String lower = referrer.toLowerCase();
        if (lower.contains("google.com") || lower.contains("google.co")) return "Google Search";
        if (lower.contains("linkedin.com")) return "LinkedIn";
        if (lower.contains("github.com")) return "GitHub";
        if (lower.contains("t.co") || lower.contains("twitter.com") || lower.contains("x.com"))
            return "X (Twitter)";
        try {
            URI uri = new URI(referrer);
            String host = uri.getHost();
            return (host != null) ? host.replaceFirst("^www\\.", "") : "External Referral";
        } catch (Exception e) {
            return "External Referral";
        }
    }

    private void enrichDeviceAndOsFromUserAgent(
            AudienceVisit visit,
            String rawOs,
            String rawBrowser,
            String userAgentStr,
            String platformVersion) {
        if (userAgentStr != null && !userAgentStr.isEmpty() && uaa != null) {
            UserAgent agent = uaa.parse(userAgentStr);
            String osName = agent.getValue("OperatingSystemName");
            String osVersion = agent.getValue("OperatingSystemVersion");
            String deviceClass = agent.getValue("DeviceClass");
            String deviceName = agent.getValue("DeviceName");
            String deviceBrand = agent.getValue("DeviceBrand");
            String agentName = agent.getValue("AgentName");
            String agentVersion = agent.getValue("AgentVersion");

            // Strip `??` YAUAA artifacts
            String finalOs =
                    (!"Unknown".equals(osName) && osName != null && !osName.contains("??"))
                            ? osName
                            : rawOs;
            String finalOsVer =
                    (!"Unknown".equals(osVersion) && osVersion != null && !osVersion.contains("??"))
                            ? osVersion
                            : "N/A";

            // Standardize Windows Frozen UA string issue
            if (platformVersion != null && !platformVersion.equals("Unknown")) {
                try {
                    int majorVer = Integer.parseInt(platformVersion.split("\\.")[0]);
                    if (majorVer >= 13 && finalOs != null && finalOs.contains("Windows")) {
                        finalOs = "Windows 11";
                        finalOsVer = platformVersion;
                    } else if (finalOs != null && finalOs.contains("Windows")) {
                        finalOs = "Windows 10";
                        finalOsVer = platformVersion;
                    }
                } catch (NumberFormatException ignored) {
                }
            } else {
                if (finalOs != null && finalOs.contains("Windows >=10")) {
                    finalOs = "Windows 10/11";
                    finalOsVer = "";
                } else if (finalOs != null
                        && finalOs.equals("Windows NT")
                        && (finalOsVer.startsWith("10")
                                || finalOsVer.startsWith(
                                        ">=10"))) { // FIX: Catch Chromium >=10 frozen variants
                    finalOs = "Windows 10/11";
                    finalOsVer = "";
                } else if (finalOs != null && finalOs.startsWith("Windows NT 6.1")) {
                    finalOs = "Windows 7";
                    finalOsVer = "";
                }
            }

            visit.setOperatingSystem(finalOs != null ? finalOs : "Unknown OS");
            visit.setOsVersion(finalOsVer);
            visit.setDeviceClass(
                    !"Unknown".equals(deviceClass)
                                    && deviceClass != null
                                    && !deviceClass.contains("??")
                            ? deviceClass
                            : "Unknown");
            visit.setDeviceBrand(
                    !"Unknown".equals(deviceBrand)
                                    && deviceBrand != null
                                    && !deviceBrand.contains("??")
                            ? deviceBrand
                            : "Unknown");

            // Preserve Browser for Desktops, Use Device Name for Mobile
            if ("Phone".equals(deviceClass)
                    || "Tablet".equals(deviceClass)
                    || "Mobile".equals(deviceClass)) {
                visit.setDeviceModel(
                        (!"Unknown".equals(deviceName)
                                        && deviceName != null
                                        && !deviceName.contains("??"))
                                ? deviceName
                                : deviceClass);
            } else {
                // It's a Desktop or Unknown. Preserve the Browser Name! Do NOT overwrite with
                // "Desktop PC".
                String browser =
                        (!"Unknown".equals(agentName)
                                        && agentName != null
                                        && !agentName.contains("??"))
                                ? agentName
                                : (rawBrowser != null ? rawBrowser : "Desktop PC");
                if (browser.toLowerCase().contains("edge")) browser = "Edge";
                if (browser.toLowerCase().contains("chrome")
                        && !userAgentStr.toLowerCase().contains("edg")) browser = "Chrome";

                String browserVersion =
                        (!"Unknown".equals(agentVersion)
                                        && agentVersion != null
                                        && !agentVersion.contains("??"))
                                ? agentVersion.split("\\.")[0]
                                : "";
                visit.setDeviceModel((browser + " " + browserVersion).trim());
            }
        } else {
            visit.setOperatingSystem(rawOs != null ? rawOs : "Unknown OS");
            visit.setOsVersion("10.0");
            visit.setDeviceModel(rawBrowser != null ? rawBrowser : "Desktop PC");
            visit.setDeviceClass("Unknown");
            visit.setDeviceBrand("Unknown");
        }
    }

    public List<Map<String, Object>> queryBigQueryRawEvents(String dateStr) {
        if (!bigQueryEnabled) {
            logger.info(
                    "[AnalyticsService] BigQuery integration is disabled via feature toggle to enforce Free-Tier Mandate.");
            return Collections.emptyList();
        }

        if (bqProjectId == null || bqDatasetId == null || credentialsPath == null) {
            logger.warn(
                    "BigQuery integration not fully configured. Missing Project ID or Dataset ID.");
            return Collections.emptyList();
        }

        try {
            File credentialsFile = new File(credentialsPath);
            GoogleCredentials credentials =
                    GoogleCredentials.fromStream(new FileInputStream(credentialsFile));

            BigQuery bigquery =
                    BigQueryOptions.newBuilder()
                            .setCredentials(credentials)
                            .setProjectId(bqProjectId)
                            .build()
                            .getService();

            String query =
                    String.format(
                            """
              SELECT
                  event_timestamp,
                  event_name,
                  user_pseudo_id,
                  geo.country,
                  device.category,
                  device.operating_system,
                  traffic_source.source,
                  traffic_source.medium,
                  traffic_source.name as campaign,
                  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'page_location') as page_url,
                  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'engagement_time_msec') as engagement_ms
              FROM `%s.%s.events_%s`
              LIMIT 10000
              """,
                            bqProjectId, bqDatasetId, dateStr.replace("-", ""));

            QueryJobConfiguration config = QueryJobConfiguration.newBuilder(query).build();
            TableResult result = bigquery.query(config);

            List<Map<String, Object>> rowMaps = new ArrayList<>();
            for (FieldValueList row : result.iterateAll()) {
                Map<String, Object> map = new HashMap<>();
                map.put("event_timestamp", row.get("event_timestamp").getValue());
                map.put("event_name", row.get("event_name").getStringValue());
                map.put("user_pseudo_id", row.get("user_pseudo_id").getStringValue());
                rowMaps.add(map);
            }
            return rowMaps;

        } catch (Exception e) {
            logger.error("Failed to query BigQuery Raw Events", e);
            return Collections.emptyList();
        }
    }

    private void initialHistoricalFetch() {
        if (analyticsDataClient == null) return;
        if (audienceVisitRepository.findMaxSessionDate().isPresent()) return;

        logger.info("Starting initial historical fetch...");
        LocalDate startDate = LocalDate.parse(initialFetchStartDate, GA_DATE_FORMATTER);
        LocalDate endDate = LocalDate.now().minusDays(1);
        chunkedFetchAndEnrich(startDate, endDate);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void dailyIncrementalFetch() {
        if (analyticsDataClient == null) return;

        Optional<LocalDate> maxDateOpt = audienceVisitRepository.findMaxSessionDate();
        LocalDate startDate =
                maxDateOpt
                        .map(date -> date.plusDays(1))
                        .orElse(LocalDate.parse(initialFetchStartDate, GA_DATE_FORMATTER));

        LocalDate endDate = LocalDate.now().minusDays(1);

        if (startDate.isBefore(endDate) || startDate.isEqual(endDate)) {
            chunkedFetchAndEnrich(startDate, endDate);
        }
    }

    @Transactional
    public void refreshGA4Data(LocalDate startDate, LocalDate endDate) {
        if (analyticsDataClient == null) {
            throw new IllegalStateException(
                    "Google Analytics API client is not configured on this server.");
        }
        logger.info("Manual GA4 Refresh Triggered: {} to {}", startDate, endDate);

        audienceVisitRepository.deleteGA4DataForDateRange(startDate, endDate);
        audienceVisitRepository.flush();

        chunkedFetchAndEnrich(startDate, endDate);
    }

    private void chunkedFetchAndEnrich(LocalDate startDate, LocalDate endDate) {
        LocalDate currentEnd = endDate;
        while (!currentEnd.isBefore(startDate)) {
            LocalDate currentStart = currentEnd.minusDays(29);
            if (currentStart.isBefore(startDate)) {
                currentStart = startDate;
            }

            logger.info("Processing GA4 Enrichment Chunk: {} to {}", currentStart, currentEnd);
            fetchAndEnrichGAData(currentStart, currentEnd);

            currentEnd = currentStart.minusDays(1);
        }
    }

    private void fetchAndEnrichGAData(LocalDate startDate, LocalDate endDate) {
        if (analyticsDataClient == null) return;

        List<Dimension> dimensions =
                List.of(
                        Dimension.newBuilder().setName("date").build(),
                        Dimension.newBuilder().setName("sessionSourceMedium").build(),
                        Dimension.newBuilder().setName("country").build(),
                        Dimension.newBuilder().setName("region").build(),
                        Dimension.newBuilder().setName("city").build(),
                        Dimension.newBuilder().setName("operatingSystemWithVersion").build(),
                        Dimension.newBuilder().setName("mobileDeviceModel").build(),
                        Dimension.newBuilder().setName("browser").build(),
                        Dimension.newBuilder().setName("screenResolution").build());

        List<Metric> metrics =
                List.of(
                        Metric.newBuilder().setName("sessions").build(),
                        Metric.newBuilder().setName("averageSessionDuration").build());

        RunReportRequest request =
                RunReportRequest.newBuilder()
                        .setProperty("properties/" + propertyId)
                        .addDateRanges(
                                DateRange.newBuilder()
                                        .setStartDate(startDate.format(GA_DATE_FORMATTER))
                                        .setEndDate(endDate.format(GA_DATE_FORMATTER)))
                        .addAllDimensions(dimensions)
                        .addAllMetrics(metrics)
                        .setLimit(100000)
                        .build();

        try {
            RunReportResponse response = analyticsDataClient.runReport(request);
            List<AudienceVisit> ga4Visits = mapResponseToEntity(response);

            if (ga4Visits.isEmpty()) return;

            Map<LocalDate, List<AudienceVisit>> ga4ByDate =
                    ga4Visits.stream()
                            .collect(Collectors.groupingBy(AudienceVisit::getSessionDate));

            List<AudienceVisit> toSave = new ArrayList<>();

            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                List<AudienceVisit> ga4DayData = ga4ByDate.getOrDefault(date, new ArrayList<>());
                List<AudienceVisit> faroVisits =
                        audienceVisitRepository.findFaroVisitsForEnrichment(date);

                if (faroVisits.isEmpty()) {
                    toSave.addAll(ga4DayData);
                    continue;
                }

                if (ga4DayData.isEmpty()) {
                    continue;
                }

                List<String> sourcePool = new ArrayList<>();
                for (AudienceVisit ga4v : ga4DayData) {
                    int sessions = ga4v.getViews() != null ? ga4v.getViews() : 0;
                    for (int i = 0; i < Math.max(1, sessions); i++) {
                        sourcePool.add(ga4v.getSessionSource());
                    }
                }

                Collections.shuffle(sourcePool);

                int poolIndex = 0;
                for (AudienceVisit faroVisit : faroVisits) {
                    if (sourcePool.isEmpty()) {
                        break;
                    }
                    faroVisit.setSessionSource(sourcePool.get(poolIndex % sourcePool.size()));
                    poolIndex++;
                    toSave.add(faroVisit);
                }
            }

            if (!toSave.isEmpty()) {
                audienceVisitRepository.saveAll(toSave);
            }

        } catch (Exception e) {
            logger.error("Error fetching and enriching GA4 data", e);
        }
    }

    private String cleanNotSet(String value, String fallback) {
        if (value == null
                || "(not set)".equalsIgnoreCase(value.trim())
                || "unknown".equalsIgnoreCase(value.trim())) return fallback;
        return value;
    }

    private String formatSourceMedium(String sourceMedium) {
        if (sourceMedium.equalsIgnoreCase("direct / (none)")
                || sourceMedium.equalsIgnoreCase("(direct) / (none)")) return "Direct";
        if (sourceMedium.contains(" / organic")) return sourceMedium.split(" /")[0] + " Organic";
        if (sourceMedium.contains(" / referral")) return sourceMedium.split(" /")[0] + " Referral";
        return sourceMedium;
    }

    private List<AudienceVisit> mapResponseToEntity(RunReportResponse response) {
        List<AudienceVisit> visits = new ArrayList<>();

        for (com.google.analytics.data.v1beta.Row row : response.getRowsList()) {
            AudienceVisit visit = new AudienceVisit();
            try {
                visit.setSessionDate(
                        LocalDate.parse(
                                row.getDimensionValues(0).getValue(),
                                DateTimeFormatter.ofPattern("yyyyMMdd")));

                String rawSource = cleanNotSet(row.getDimensionValues(1).getValue(), "Direct");
                visit.setSessionSource(formatSourceMedium(rawSource));

                visit.setCountry(cleanNotSet(row.getDimensionValues(2).getValue(), "Unknown"));
                visit.setRegion(cleanNotSet(row.getDimensionValues(3).getValue(), "Unknown"));
                visit.setCity(cleanNotSet(row.getDimensionValues(4).getValue(), "Unknown"));

                String osCompound = cleanNotSet(row.getDimensionValues(5).getValue(), "Unknown");
                if (osCompound.equals("Unknown")) {
                    visit.setOperatingSystem("Unknown");
                    visit.setOsVersion("Unknown");
                } else {
                    int spaceIdx = osCompound.indexOf(" ");
                    if (spaceIdx > 0) {
                        visit.setOperatingSystem(osCompound.substring(0, spaceIdx));
                        visit.setOsVersion(osCompound.substring(spaceIdx + 1));
                    } else {
                        visit.setOperatingSystem(osCompound);
                        visit.setOsVersion("");
                    }
                }

                String devModel = cleanNotSet(row.getDimensionValues(6).getValue(), "Desktop");
                String browser = cleanNotSet(row.getDimensionValues(7).getValue(), "Unknown");

                if ((devModel.equals("Desktop") || devModel.equals("Unknown"))
                        && !browser.equals("Unknown")) {
                    visit.setDeviceModel(browser);
                } else {
                    visit.setDeviceModel(devModel);
                }

                visit.setScreenResolution(cleanNotSet(row.getDimensionValues(8).getValue(), "N/A"));
                visit.setDeviceCategory("Unknown");
                visit.setLandingPage("Not available (GA4)");
                visit.setClientId("Not available (GA4)");
                visit.setSessionId("Not available (GA4)");
                visit.setViews(Long.valueOf(row.getMetricValues(0).getValue()).intValue());
                visit.setSessionDurationSeconds(
                        Math.round(Double.parseDouble(row.getMetricValues(1).getValue())));

                visits.add(visit);
            } catch (Exception e) {
            }
        }
        return visits;
    }

    // --- DYNAMIC SPECIFICATION BUILDER (INCIDENT 76) ---
    private Specification<AudienceVisit> createAudienceFilterSpec(
            LocalDate startDate, LocalDate endDate, AudienceFilter filters, String excludeField) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Unconditional Date Range filter
            predicates.add(builder.between(root.get("sessionDate"), startDate, endDate));

            if (!"country".equals(excludeField)
                    && filters.getCountry() != null
                    && !filters.getCountry().isEmpty()) {
                predicates.add(builder.equal(root.get("country"), filters.getCountry()));
            }
            if (!"region".equals(excludeField)
                    && filters.getRegion() != null
                    && !filters.getRegion().isEmpty()) {
                predicates.add(builder.equal(root.get("region"), filters.getRegion()));
            }
            if (!"city".equals(excludeField)
                    && filters.getCity() != null
                    && !filters.getCity().isEmpty()) {
                predicates.add(builder.equal(root.get("city"), filters.getCity()));
            }
            if (!"operatingSystem".equals(excludeField)
                    && filters.getOperatingSystem() != null
                    && !filters.getOperatingSystem().isEmpty()) {
                predicates.add(
                        builder.equal(root.get("operatingSystem"), filters.getOperatingSystem()));
            }
            if (!"osVersion".equals(excludeField)
                    && filters.getOsVersion() != null
                    && !filters.getOsVersion().isEmpty()) {
                predicates.add(builder.equal(root.get("osVersion"), filters.getOsVersion()));
            }
            if (!"sessionSource".equals(excludeField)
                    && filters.getSessionSource() != null
                    && !filters.getSessionSource().isEmpty()) {
                predicates.add(
                        builder.equal(root.get("sessionSource"), filters.getSessionSource()));
            }

            // Exclude clientId logic to preserve "target" and "exclude" matching mechanics
            if (!"clientId".equals(excludeField)) {
                if (filters.getTargetClientIds() != null
                        && !filters.getTargetClientIds().isEmpty()) {
                    predicates.add(root.get("clientId").in(filters.getTargetClientIds()));
                }
                if (filters.getExcludeClientIds() != null
                        && !filters.getExcludeClientIds().isEmpty()) {
                    predicates.add(
                            builder.not(root.get("clientId").in(filters.getExcludeClientIds())));
                }
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<String> getDistinctValues(String columnName, Specification<AudienceVisit> spec) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = builder.createQuery(String.class);
        Root<AudienceVisit> root = query.from(AudienceVisit.class);

        query.select(root.get(columnName)).distinct(true);

        Predicate predicate = spec.toPredicate(root, query, builder);
        List<Predicate> finalPredicates = new ArrayList<>();
        if (predicate != null) {
            finalPredicates.add(predicate);
        }

        // For clientId, mirror the old JPQL "clientId IS NOT NULL AND clientId != 'Not available
        // (GA4)'" logic
        if ("clientId".equals(columnName)) {
            finalPredicates.add(builder.isNotNull(root.get(columnName)));
            finalPredicates.add(builder.notEqual(root.get(columnName), "Not available (GA4)"));
        }

        if (!finalPredicates.isEmpty()) {
            query.where(builder.and(finalPredicates.toArray(new Predicate[0])));
        }

        return entityManager
                .createQuery(query)
                .getResultStream()
                .filter(
                        val ->
                                val != null
                                        && !val.trim()
                                                .isEmpty()) // Neutralize frontend null-pointer risk
                // natively
                .collect(Collectors.toList());
    }

    public List<AudienceDataDto> getHistoricalData(
            LocalDate startDate, LocalDate endDate, AudienceFilter filters) {

        Specification<AudienceVisit> spec =
                createAudienceFilterSpec(startDate, endDate, filters, null);
        Sort sort = Sort.by(Sort.Direction.DESC, "sessionDate", "createdAt");

        List<AudienceVisit> visits = audienceVisitRepository.findAll(spec, sort);

        Map<String, LocalDate> firstVisitMap = new HashMap<>();
        List<Object[]> batchResults = audienceVisitRepository.findAllFirstVisitDatesUpTo(endDate);
        for (Object[] row : batchResults) {
            String clientId = (String) row[0];
            LocalDate minDate = (LocalDate) row[1];
            if (clientId != null && minDate != null) {
                firstVisitMap.put(clientId, minDate);
            }
        }

        return visits.stream()
                .map(v -> mapEntityToDto(v, firstVisitMap.get(v.getClientId())))
                .toList();
    }

    public FilterOptionsDto getFilterOptions(
            LocalDate startDate, LocalDate endDate, AudienceFilter filters) {

        return FilterOptionsDto.builder()
                .countries(
                        getDistinctValues(
                                "country",
                                createAudienceFilterSpec(startDate, endDate, filters, "country")))
                .regions(
                        getDistinctValues(
                                "region",
                                createAudienceFilterSpec(startDate, endDate, filters, "region")))
                .cities(
                        getDistinctValues(
                                "city",
                                createAudienceFilterSpec(startDate, endDate, filters, "city")))
                .operatingSystems(
                        getDistinctValues(
                                "operatingSystem",
                                createAudienceFilterSpec(
                                        startDate, endDate, filters, "operatingSystem")))
                .osVersions(
                        getDistinctValues(
                                "osVersion",
                                createAudienceFilterSpec(startDate, endDate, filters, "osVersion")))
                .sessionSources(
                        getDistinctValues(
                                "sessionSource",
                                createAudienceFilterSpec(
                                        startDate, endDate, filters, "sessionSource")))
                .clientIds(
                        getDistinctValues(
                                "clientId",
                                createAudienceFilterSpec(startDate, endDate, filters, "clientId")))
                .build();
    }

    private AudienceDataDto mapEntityToDto(AudienceVisit entity, LocalDate firstVisitDate) {
        String formattedSessionDate =
                entity.getSessionDate() != null
                        ? entity.getSessionDate().format(GA_DATE_FORMATTER)
                        : null;

        String formattedSessionStartTime =
                entity.getCreatedAt() != null
                        ? entity.getCreatedAt().format(ISO_DATE_TIME) + "Z"
                        : null;

        String formattedFirstVisitDate =
                firstVisitDate != null ? firstVisitDate.format(GA_DATE_FORMATTER) : null;

        String os = entity.getOperatingSystem();
        String osVer = entity.getOsVersion();
        String model = entity.getDeviceModel();

        if (osVer != null && osVer.matches("^\\d{2,3}\\.\\d+\\.\\d+\\.\\d+$")) {
            osVer = "N/A";
            if (model != null
                    && (model.equals("Desktop")
                            || model.equals("Unknown")
                            || model.equals("N/A"))) {
                model = "Chrome/Edge";
            }
        }

        if (model != null
                && (model.equalsIgnoreCase("iPhone") || model.equalsIgnoreCase("Apple iPhone"))) {
            model = "Apple iPhone";
        } else if (model != null
                && (model.equalsIgnoreCase("iPad") || model.equalsIgnoreCase("Apple iPad"))) {
            model = "Apple iPad";
        }

        if ("Android".equalsIgnoreCase(os) || (os != null && os.contains("Android"))) {
            if ("N/A".equals(osVer) || "Unknown".equals(osVer) || osVer == null) {
                osVer = "Version Masked";
            }
            if (model == null
                    || "Android Mobile".equalsIgnoreCase(model)
                    || "Phone".equalsIgnoreCase(model)
                    || "N/A".equalsIgnoreCase(model)
                    || "Unknown".equalsIgnoreCase(model)) {
                model = "Android Phone (Model Masked by Chrome)";
            }
        }

        return AudienceDataDto.builder()
                .id(entity.getId())
                .sessionDate(formattedSessionDate)
                .sessionStartTime(formattedSessionStartTime)
                .firstVisitDate(formattedFirstVisitDate)
                .userIdentifier(entity.getClientId())
                .country(entity.getCountry())
                .region(entity.getRegion())
                .city(entity.getCity())
                .deviceCategory(entity.getDeviceCategory())
                .deviceModel(model)
                .deviceBrand(entity.getDeviceBrand())
                .deviceClass(entity.getDeviceClass())
                .deviceFingerprint(entity.getDeviceFingerprint())
                .operatingSystem(os)
                .osVersion(osVer)
                .screenResolution(entity.getScreenResolution())
                .sessionSource(entity.getSessionSource())
                .landingPage(entity.getLandingPage())
                .views(entity.getViews())
                .timeOnSiteFormatted(
                        AudienceDataDto.formatDuration(entity.getSessionDurationSeconds()))
                .rawSessionId(entity.getSessionId())
                .build();
    }

    @org.springframework.scheduling.annotation.Async
    public void healHistoricalDataFidelity() {
        logger.info("[Data Healer] Triggering Memory-Safe Retroactive Fidelity Restoration...");
        int page = 0;
        int batchSize = 500;
        long[] totalHealed = {0}; // Array required for lambda scope modification

        org.springframework.data.domain.Page<AudienceVisit> visitPage;
        do {
            visitPage =
                    audienceVisitRepository.findAll(
                            org.springframework.data.domain.PageRequest.of(page, batchSize));

            if (visitPage.isEmpty()) break;

            // Isolate the reassigned loop variable to safely pass it into the lambda closure
            final org.springframework.data.domain.Page<AudienceVisit> currentBatch = visitPage;

            transactionTemplate.execute(
                    status -> {
                        List<String> sessionIds =
                                currentBatch.getContent().stream()
                                        .map(AudienceVisit::getSessionId)
                                        .collect(Collectors.toList());

                        if (!sessionIds.isEmpty()) {
                            List<Object[]> aggregatedEvents =
                                    entityManager
                                            .createQuery(
                                                    "SELECT a.sessionId, MAX(a.deviceFingerprint), MIN(a.path), MAX(a.referrer), MAX(a.os), MAX(a.browser), MAX(a.userAgent), SUM(a.timeOnPageMs), MAX(a.scrollDepth) "
                                                            + "FROM AnalyticsEvent a WHERE a.sessionId IN :sessionIds GROUP BY a.sessionId",
                                                    Object[].class)
                                            .setParameter("sessionIds", sessionIds)
                                            .getResultList();

                            Map<String, Object[]> aggregatedMap = new HashMap<>();
                            for (Object[] row : aggregatedEvents) {
                                aggregatedMap.put((String) row[0], row);
                            }

                            for (AudienceVisit visit : currentBatch.getContent()) {
                                Object[] agg = aggregatedMap.get(visit.getSessionId());
                                if (agg != null) {
                                    String fingerprint = (String) agg[1];
                                    String path = (String) agg[2];
                                    String referrer = (String) agg[3];
                                    String os = (String) agg[4];
                                    String browser = (String) agg[5];
                                    String userAgent = (String) agg[6];
                                    Long timeOnPageMs = (Long) agg[7];

                                    if (fingerprint != null && !fingerprint.trim().isEmpty()) {
                                        visit.setDeviceFingerprint(fingerprint);
                                    }
                                    if (visit.getLandingPage() == null
                                            || visit.getLandingPage().equals("Not available (GA4)")
                                            || visit.getLandingPage().equals("/")) {
                                        visit.setLandingPage(
                                                (path != null && !path.trim().isEmpty())
                                                        ? path
                                                        : "/");
                                    }
                                    if (timeOnPageMs != null) {
                                        long existingDuration =
                                                visit.getSessionDurationSeconds() != null
                                                        ? visit.getSessionDurationSeconds()
                                                        : 0L;
                                        if (timeOnPageMs > (existingDuration * 1000L)) {
                                            visit.setSessionDurationSeconds(timeOnPageMs / 1000L);
                                        }
                                    }

                                    visit.setSessionSource(resolveSessionSource(referrer));
                                    enrichDeviceAndOsFromUserAgent(
                                            visit, os, browser, userAgent, null);

                                    audienceVisitRepository.save(visit);
                                    totalHealed[0]++;
                                }
                            }
                        }

                        audienceVisitRepository.flush();
                        entityManager.clear();
                        return null;
                    });

            logger.info(
                    "[Data Healer] Processed chunk {}. Healed records: {}", page, totalHealed[0]);
            page++;
        } while (visitPage.hasNext());

        logger.info(
                "[Data Healer] Reconciliation Complete. Historical records restored: {}",
                totalHealed[0]);
    }
}
