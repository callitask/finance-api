/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Service for fetching Google Analytics 4 (GA4) historical data and orchestrating
 * local Audience Dashboard queries.
 *
 * <p>Security Constraints: - The refreshGA4Data MUST use a @Transactional block and explicit
 * repository.flush() to ensure data is strictly dropped before the refetch occurs.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (LATEST): • Mapped Temporal classes to
 * String manually in `mapEntityToDto` to prevent JS Date errors. • Added explicit
 * `audienceVisitRepository.flush()` during manual GA4 sync to fix stale data reappearing.
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
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  @Value("${ga4.initial-fetch-start-date:2024-01-01}")
  private String initialFetchStartDate;

  private BetaAnalyticsDataClient analyticsDataClient;
  private final AudienceVisitRepository audienceVisitRepository;

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

  private void initialHistoricalFetch() {
    if (analyticsDataClient == null) return;
    if (audienceVisitRepository.findMaxSessionDate().isPresent()) return;

    logger.info("Starting initial historical fetch...");
    LocalDate startDate = LocalDate.parse(initialFetchStartDate, GA_DATE_FORMATTER);
    LocalDate endDate = LocalDate.now().minusDays(1);
    fetchAndSaveGAData(startDate, endDate);
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
      fetchAndSaveGAData(startDate, endDate);
    }
  }

  @Transactional
  public void refreshGA4Data(LocalDate startDate, LocalDate endDate) {
    if (analyticsDataClient == null) {
      throw new IllegalStateException(
          "Google Analytics API client is not configured on this server.");
    }
    logger.info("Manual GA4 Refresh Triggered: {} to {}", startDate, endDate);

    // 1. Wipe ONLY GA4 data for the date range (protect Faro RUM data)
    audienceVisitRepository.deleteGA4DataForDateRange(startDate, endDate);

    // Force JPA to flush the deletes to the database immediately to prevent stale cache reads
    audienceVisitRepository.flush();

    // 2. Fetch fresh data from Google
    fetchAndSaveGAData(startDate, endDate);
  }

  private void fetchAndSaveGAData(LocalDate startDate, LocalDate endDate) {
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
      List<AudienceVisit> visits = mapResponseToEntity(response);
      if (!visits.isEmpty()) {
        audienceVisitRepository.saveAll(visits);
      }
    } catch (Exception e) {
      logger.error("Error fetching GA4 data", e);
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
        .deviceModel(entity.getDeviceModel())
        .operatingSystem(entity.getOperatingSystem())
        .osVersion(entity.getOsVersion())
        .screenResolution(entity.getScreenResolution())
        .sessionSource(entity.getSessionSource())
        .landingPage(entity.getLandingPage())
        .views(entity.getViews())
        .timeOnSiteFormatted(AudienceDataDto.formatDuration(entity.getSessionDurationSeconds()))
        .rawSessionId(entity.getSessionId())
        .build();
  }
}
