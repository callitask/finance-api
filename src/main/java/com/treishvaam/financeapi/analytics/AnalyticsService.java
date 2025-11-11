package com.treishvaam.financeapi.analytics;

import com.google.analytics.data.v1beta.BetaAnalyticsDataClient;
import com.google.analytics.data.v1beta.Dimension;
import com.google.analytics.data.v1beta.DateRange;
import com.google.analytics.data.v1beta.Metric;
import com.google.analytics.data.v1beta.RunReportRequest;
import com.google.analytics.data.v1beta.RunReportResponse;
import com.google.analytics.data.v1beta.BetaAnalyticsDataSettings;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);
    private static final DateTimeFormatter GA_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${ga4.property-id}")
    private String propertyId;

    @Value("${ga4.credentials-path}")
    private String credentialsPath;

    // Define the initial start date for the very first historical fetch (e.g., 6 months back)
    @Value("${ga4.initial-fetch-start-date:2024-01-01}") 
    private String initialFetchStartDate;

    private BetaAnalyticsDataClient analyticsDataClient;
    private final AudienceVisitRepository audienceVisitRepository;

    // Constructor updated to use the new Repository location
    public AnalyticsService(AudienceVisitRepository audienceVisitRepository) {
        this.audienceVisitRepository = audienceVisitRepository;
    }

    // Initialize the GA4 client on application startup
    @PostConstruct
    public void init() {
        // Skip initialization if running tests or client is null
        if (credentialsPath == null || credentialsPath.isEmpty()) {
             logger.warn("GA4 Credentials path is empty. Skipping client initialization. Check application-prod.properties and environment variables.");
             this.analyticsDataClient = null;
             return;
        }
        
        try {
            // FIX: Use FileInputStream to explicitly load the file from the absolute file system path
            File credentialsFile = new File(credentialsPath);
            if (!credentialsFile.exists()) {
                // Log and throw clear error for missing file
                logger.error("GA4 Credentials file not found at: {}. Please verify the GA4_SERVICE_ACCOUNT_CREDENTIALS_PATH environment variable on your VM.", credentialsPath);
                throw new IOException("GA4 Credentials file not found at: " + credentialsPath);
            }
            
            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsFile))
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/analytics.readonly"));

            // FIX: Use BetaAnalyticsDataSettings builder pattern to ensure compatibility.
            BetaAnalyticsDataSettings settings = BetaAnalyticsDataSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            this.analyticsDataClient = BetaAnalyticsDataClient.create(settings);
            
            logger.info("Google Analytics Data Client initialized successfully for Property: {}", propertyId);
            
            // Trigger initial historical fetch after client is ready
            initialHistoricalFetch();
            
        } catch (Exception e) {
            // Error log explains why the file was not found, addressing user's confusion.
            logger.error("Failed to initialize Google Analytics Data Client (GA4). Check GA4_SERVICE_ACCOUNT_CREDENTIALS_PATH (must be C:/path/to/file.json, NO leading /) and file access permissions.", e);
            this.analyticsDataClient = null;
        }
    }

    // Runs once after startup to ensure historical data exists
    private void initialHistoricalFetch() {
        if (analyticsDataClient == null) {
            return;
        }

        Optional<LocalDate> maxDateOpt = audienceVisitRepository.findMaxSessionDate();
        
        // If data exists, scheduling will handle incremental updates.
        if (maxDateOpt.isPresent()) {
            logger.info("Historical audience data already exists. Max date: {}. Skipping full historical fetch.", maxDateOpt.get());
            return;
        }

        logger.info("Starting initial historical fetch. This may take a moment...");
        LocalDate startDate = LocalDate.parse(initialFetchStartDate, GA_DATE_FORMATTER);
        LocalDate endDate = LocalDate.now().minusDays(1); // Fetch up to yesterday
        
        fetchAndSaveGAData(startDate, endDate);
    }
    
    /**
     * Scheduled task to run daily and append new data incrementally.
     * Runs at 2 AM every day.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyIncrementalFetch() {
        if (analyticsDataClient == null) {
            logger.warn("Analytics client is not initialized. Skipping scheduled fetch.");
            return;
        }
        
        Optional<LocalDate> maxDateOpt = audienceVisitRepository.findMaxSessionDate();
        LocalDate startDate;

        if (maxDateOpt.isPresent()) {
            // Start from the day *after* the last recorded session.
            startDate = maxDateOpt.get().plusDays(1);
        } else {
            // Fallback: start from the configured initial date if no data exists.
            startDate = LocalDate.parse(initialFetchStartDate, GA_DATE_FORMATTER);
        }
        
        LocalDate endDate = LocalDate.now().minusDays(1); // Always fetch data up to yesterday

        if (startDate.isBefore(endDate) || startDate.isEqual(endDate)) {
            logger.info("Starting incremental GA data fetch from {} to {}", startDate, endDate);
            fetchAndSaveGAData(startDate, endDate);
        } else {
            logger.info("No new historical data available to fetch. Max date is already up to date.");
        }
    }

    /**
     * Executes the GA4 API call for historical data and persists results.
     */
    private void fetchAndSaveGAData(LocalDate startDate, LocalDate endDate) {
        if (analyticsDataClient == null) {
            logger.warn("Analytics client is not available. Cannot fetch data.");
            return;
        }

        List<Dimension> dimensions = List.of(
            Dimension.newBuilder().setName("date").build(),
            Dimension.newBuilder().setName("sessionSource").build(),
            Dimension.newBuilder().setName("country").build(),
            Dimension.newBuilder().setName("region").build(),
            Dimension.newBuilder().setName("city").build(),
            Dimension.newBuilder().setName("deviceCategory").build(),
            Dimension.newBuilder().setName("operatingSystem").build(),
            Dimension.newBuilder().setName("landingPage").build()
        );
        
        // FIX: Replaced "sessionDuration" with "averageSessionDuration" as suggested by GA4 API.
        List<Metric> metrics = List.of(
            Metric.newBuilder().setName("sessions").build(), 
            Metric.newBuilder().setName("averageSessionDuration").build()
        );

        RunReportRequest request = RunReportRequest.newBuilder()
                .setProperty("properties/" + propertyId)
                .addDateRanges(DateRange.newBuilder()
                        .setStartDate(startDate.format(GA_DATE_FORMATTER))
                        .setEndDate(endDate.format(GA_DATE_FORMATTER)))
                .addAllDimensions(dimensions)
                .addAllMetrics(metrics)
                .setLimit(100000) // Set a high limit to capture all data
                .build();

        try {
            RunReportResponse response = analyticsDataClient.runReport(request);
            List<AudienceVisit> visits = mapResponseToEntity(response);
            audienceVisitRepository.saveAll(visits);
            logger.info("Successfully fetched and saved {} historical audience records for period {} to {}", visits.size(), startDate, endDate);
        } catch (Exception e) {
            logger.error("Error fetching historical Analytics Data from GA4 API for period {} to {}", startDate, endDate, e);
            // Re-throw or log fatal if it's a permanent error that will crash the service on every startup/schedule
        }
    }

    /**
     * Maps the GA4 ReportResponse into a list of JPA Entities.
     */
    private List<AudienceVisit> mapResponseToEntity(RunReportResponse response) {
        List<AudienceVisit> visits = new ArrayList<>();
        
        for (com.google.analytics.data.v1beta.Row row : response.getRowsList()) {
            // The mapping is now based on the new dimension list (8 dimensions, index 0-7):
            // 0: date, 1: sessionSource, 2: country, 3: region, 4: city, 5: deviceCategory, 6: operatingSystem, 7: landingPage
            
            String date = row.getDimensionValues(0).getValue();
            String sessionSource = row.getDimensionValues(1).getValue();
            String country = row.getDimensionValues(2).getValue();
            String region = row.getDimensionValues(3).getValue();
            String city = row.getDimensionValues(4).getValue();
            String deviceCategory = row.getDimensionValues(5).getValue();
            String operatingSystem = row.getDimensionValues(6).getValue();
            String landingPage = row.getDimensionValues(7).getValue();
            // sessionId dimension is no longer available in the response

            // Metric values by index
            Long totalSessions = Long.valueOf(row.getMetricValues(0).getValue()); 
            // FIX: Reading the now-renamed metric: averageSessionDuration
            Long durationSeconds = Math.round(Double.parseDouble(row.getMetricValues(1).getValue()));

            AudienceVisit visit = new AudienceVisit();
            // We set the unavailable fields to a constant string
            visit.setSessionDate(LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd")));
            visit.setClientId("GA4 Restricted"); // UserPseudoId unavailable
            // FIX: Explicitly set sessionId to Restricted since the dimension was removed from the request
            visit.setSessionId("GA4 Restricted");
            visit.setSessionSource(sessionSource);
            visit.setCountry(country);
            visit.setRegion(region);
            visit.setCity(city);
            visit.setDeviceCategory(deviceCategory);
            visit.setDeviceModel("GA4 Restricted"); 
            visit.setOperatingSystem(operatingSystem);
            visit.setOsVersion("GA4 Restricted"); 
            visit.setScreenResolution("GA4 Restricted");
            visit.setLandingPage(landingPage);
            visit.setViews(totalSessions.intValue());
            // FIX: Now stores Average Session Duration in seconds
            visit.setSessionDurationSeconds(durationSeconds);
            visits.add(visit);
        }

        return visits;
    }

    /**
     * Fetches historical data from the local database for the given date range.
     */
    public List<AudienceDataDto> getHistoricalData(LocalDate startDate, LocalDate endDate) {
        // Ensure endDate includes the entire day
        List<AudienceVisit> visits = audienceVisitRepository.findBySessionDateBetweenOrderBySessionDateDesc(startDate, endDate);
        return visits.stream().map(this::mapEntityToDto).toList();
    }

    /**
     * Maps the JPA Entity to the DTO for presentation.
     */
    private AudienceDataDto mapEntityToDto(AudienceVisit entity) {
        // Lombok's builder method is now available
        return AudienceDataDto.builder()
            .id(entity.getId())
            .sessionDate(entity.getSessionDate())
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