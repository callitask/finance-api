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
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  @Value("${ga4.initial-fetch-start-date:2024-01-01}")
  private String initialFetchStartDate;

  private BetaAnalyticsDataClient analyticsDataClient;
  private final AudienceVisitRepository audienceVisitRepository;

  @Autowired private AnalyticsEventRepository analyticsEventRepository;

  public AnalyticsService(AudienceVisitRepository audienceVisitRepository) {
    this.audienceVisitRepository = audienceVisitRepository;
  }

  @PostConstruct
  public void init() {
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
                  Collections.singletonList("https://www.googleapis.com/auth/analytics.readonly"));

      BetaAnalyticsDataSettings settings =
          BetaAnalyticsDataSettings.newBuilder().setCredentialsProvider(() -> credentials).build();

      this.analyticsDataClient = BetaAnalyticsDataClient.create(settings);
      logger.info(
          "Google Analytics Data Client initialized successfully for Property: {}", propertyId);

      initialHistoricalFetch();
      dailyIncrementalFetch();

    } catch (Exception e) {
      logger.error("Failed to initialize Google Analytics Data Client (GA4).", e);
      this.analyticsDataClient = null;
    }
  }

  /**
   * AI-CONTEXT: Data retention policy — auto-purge raw events older than 365 days. Why: Prevents
   * analytics_events table from growing indefinitely. DPDP Act 2023: Data must not be retained
   * longer than necessary. Runs at 03:30 AM daily (offset from the 02:00 GA4 sync to avoid DB
   * contention).
   */
  @Scheduled(cron = "0 30 3 * * *")
  @Transactional
  public void purgeOldAnalyticsEvents() {
    if (analyticsEventRepository != null) {
      LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
      int deleted = analyticsEventRepository.deleteEventsOlderThan(cutoff);
      logger.info("[AnalyticsRetention] Purged {} raw events older than 365 days.", deleted);
    }
  }

  // PHASE 10: Unsampled BigQuery extraction layer
  public List<Map<String, Object>> queryBigQueryRawEvents(String dateStr) {
    if (bqProjectId == null || bqDatasetId == null || credentialsPath == null) {
      logger.warn("BigQuery integration not fully configured. Missing Project ID or Dataset ID.");
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

    // 1. Wipe ONLY legacy GA4 placeholders for the ENTIRE date range
    // Faro RUM data is strictly protected and remains intact.
    audienceVisitRepository.deleteGA4DataForDateRange(startDate, endDate);
    audienceVisitRepository.flush();

    // 2. Fetch fresh GA4 data and enrich Faro records using 30-day backward chunking
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
          ga4Visits.stream().collect(Collectors.groupingBy(AudienceVisit::getSessionDate));

      List<AudienceVisit> toSave = new ArrayList<>();

      for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        List<AudienceVisit> ga4DayData = ga4ByDate.getOrDefault(date, new ArrayList<>());
        List<AudienceVisit> faroVisits = audienceVisitRepository.findFaroVisitsForEnrichment(date);

        if (faroVisits.isEmpty()) {
          // If no Faro data exists for this day, save GA4 aggregated rows as placeholders
          toSave.addAll(ga4DayData);
          continue;
        }

        if (ga4DayData.isEmpty()) {
          continue; // No GA4 data to enrich with for this specific day
        }

        // Smart Attribution Distribution Pool
        List<String> sourcePool = new ArrayList<>();
        for (AudienceVisit ga4v : ga4DayData) {
          int sessions =
              ga4v.getViews(); // getViews() temporally holds the 'sessions' metric from GA4
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
          // Distribute sources statistically. If Faro rows > GA4 sessions (due to GA4 adblock
          // loss),
          // wrap around cleanly to keep sources accurate.
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
                row.getDimensionValues(0).getValue(), DateTimeFormatter.ofPattern("yyyyMMdd")));

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
        // Store GA4 sessions metric temporarily into views for the distribution pool
        visit.setViews(Long.valueOf(row.getMetricValues(0).getValue()).intValue());
        visit.setSessionDurationSeconds(
            Math.round(Double.parseDouble(row.getMetricValues(1).getValue())));

        visits.add(visit);
      } catch (Exception e) {
      }
    }
    return visits;
  }

  // Helper method to safely format lists for JPQL
  private List<String> getSafeList(List<String> rawList) {
    if (rawList == null || rawList.isEmpty()) {
      return Collections.singletonList("DUMMY_ID_PREVENT_HIBERNATE_CRASH");
    }
    return rawList;
  }

  public List<AudienceDataDto> getHistoricalData(
      LocalDate startDate, LocalDate endDate, AudienceFilter filters) {

    boolean hasTargets =
        filters.getTargetClientIds() != null && !filters.getTargetClientIds().isEmpty();
    List<String> safeTargets = getSafeList(filters.getTargetClientIds());

    boolean hasExcludes =
        filters.getExcludeClientIds() != null && !filters.getExcludeClientIds().isEmpty();
    List<String> safeExcludes = getSafeList(filters.getExcludeClientIds());

    List<AudienceVisit> visits =
        audienceVisitRepository.findHistoricalDataWithFilters(
            startDate,
            endDate,
            filters.getCountry(),
            filters.getRegion(),
            filters.getCity(),
            filters.getOperatingSystem(),
            filters.getOsVersion(),
            filters.getSessionSource(),
            hasTargets,
            safeTargets,
            hasExcludes,
            safeExcludes);

    // Efficiently bulk-load first visit dates to prevent N+1 performance issues
    List<String> distinctClientIds =
        visits.stream()
            .map(AudienceVisit::getClientId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    Map<String, LocalDate> firstVisitMap = new HashMap<>();
    if (!distinctClientIds.isEmpty()) {
      List<Object[]> batchResults =
          audienceVisitRepository.findFirstVisitDatesByClientIds(distinctClientIds);
      for (Object[] row : batchResults) {
        firstVisitMap.put((String) row[0], (LocalDate) row[1]);
      }
    }

    return visits.stream().map(v -> mapEntityToDto(v, firstVisitMap.get(v.getClientId()))).toList();
  }

  public FilterOptionsDto getFilterOptions(
      LocalDate startDate, LocalDate endDate, AudienceFilter filters) {

    boolean hasTargets =
        filters.getTargetClientIds() != null && !filters.getTargetClientIds().isEmpty();
    List<String> safeTargets = getSafeList(filters.getTargetClientIds());

    boolean hasExcludes =
        filters.getExcludeClientIds() != null && !filters.getExcludeClientIds().isEmpty();
    List<String> safeExcludes = getSafeList(filters.getExcludeClientIds());

    return FilterOptionsDto.builder()
        .countries(
            audienceVisitRepository.findDistinctCountries(
                startDate,
                endDate,
                filters.getRegion(),
                filters.getCity(),
                filters.getOperatingSystem(),
                filters.getOsVersion(),
                filters.getSessionSource(),
                hasTargets,
                safeTargets,
                hasExcludes,
                safeExcludes))
        .regions(
            audienceVisitRepository.findDistinctRegions(
                startDate,
                endDate,
                filters.getCountry(),
                filters.getCity(),
                filters.getOperatingSystem(),
                filters.getOsVersion(),
                filters.getSessionSource(),
                hasTargets,
                safeTargets,
                hasExcludes,
                safeExcludes))
        .cities(
            audienceVisitRepository.findDistinctCities(
                startDate,
                endDate,
                filters.getCountry(),
                filters.getRegion(),
                filters.getOperatingSystem(),
                filters.getOsVersion(),
                filters.getSessionSource(),
                hasTargets,
                safeTargets,
                hasExcludes,
                safeExcludes))
        .operatingSystems(
            audienceVisitRepository.findDistinctOperatingSystems(
                startDate,
                endDate,
                filters.getCountry(),
                filters.getRegion(),
                filters.getCity(),
                filters.getOsVersion(),
                filters.getSessionSource(),
                hasTargets,
                safeTargets,
                hasExcludes,
                safeExcludes))
        .osVersions(
            audienceVisitRepository.findDistinctOsVersions(
                startDate,
                endDate,
                filters.getCountry(),
                filters.getRegion(),
                filters.getCity(),
                filters.getOperatingSystem(),
                filters.getSessionSource(),
                hasTargets,
                safeTargets,
                hasExcludes,
                safeExcludes))
        .sessionSources(
            audienceVisitRepository.findDistinctSessionSources(
                startDate,
                endDate,
                filters.getCountry(),
                filters.getRegion(),
                filters.getCity(),
                filters.getOperatingSystem(),
                filters.getOsVersion(),
                hasTargets,
                safeTargets,
                hasExcludes,
                safeExcludes))
        .clientIds(
            audienceVisitRepository.findDistinctClientIds(
                startDate,
                endDate,
                filters.getCountry(),
                filters.getRegion(),
                filters.getCity(),
                filters.getOperatingSystem(),
                filters.getOsVersion(),
                filters.getSessionSource()))
        .build();
  }

  private AudienceDataDto mapEntityToDto(AudienceVisit entity, LocalDate firstVisitDate) {
    String formattedSessionDate =
        entity.getSessionDate() != null ? entity.getSessionDate().format(GA_DATE_FORMATTER) : null;

    // Explicitly enforce Z suffix (UTC) so Javascript parses it properly before converting to IST
    // in UI
    String formattedSessionStartTime =
        entity.getCreatedAt() != null ? entity.getCreatedAt().format(ISO_DATE_TIME) + "Z" : null;

    String formattedFirstVisitDate =
        firstVisitDate != null ? firstVisitDate.format(GA_DATE_FORMATTER) : null;

    String os = entity.getOperatingSystem();
    String osVer = entity.getOsVersion();
    String model = entity.getDeviceModel();

    // Hardware & OS Sanitization Layer
    if (os != null) {
      if (os.contains("Windows NT") || os.equals("Windows")) {
        os = "Windows 10/11";
      } else if (os.equals("Mac OS X")) {
        os = "macOS";
      }
    }

    // Detect Faro Chromium version leakage
    if (osVer != null && osVer.matches("^\\d{2,3}\\.\\d+\\.\\d+\\.\\d+$")) {
      osVer = "N/A";
      if (model != null
          && (model.equals("Desktop") || model.equals("Unknown") || model.equals("N/A"))) {
        model = "Chrome/Edge";
      }
    }

    // Apple Device Normalization
    if (model != null && model.equalsIgnoreCase("iPhone")) {
      model = "Apple iPhone";
    }

    // Smart Android Hardware Privacy Masking Fix (Google Chrome removes device info)
    if ("Android".equalsIgnoreCase(os)) {
      if ("N/A".equals(osVer) || "Unknown".equals(osVer) || osVer == null) {
        osVer = "Version Masked";
      }
      if ("Android Mobile".equalsIgnoreCase(model)
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
        .operatingSystem(os)
        .osVersion(osVer)
        .screenResolution(entity.getScreenResolution())
        .sessionSource(entity.getSessionSource())
        .landingPage(entity.getLandingPage())
        .views(entity.getViews())
        .timeOnSiteFormatted(AudienceDataDto.formatDuration(entity.getSessionDurationSeconds()))
        .rawSessionId(entity.getSessionId())
        .build();
  }
}
