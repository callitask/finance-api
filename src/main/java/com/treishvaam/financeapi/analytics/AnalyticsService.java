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
                throw new IOException("GA4 Credentials file not found at: " + credentialsPath);
            }
            
            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsFile))
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/analytics.readonly"));

            BetaAnalyticsDataSettings settings = BetaAnalyticsDataSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            this.analyticsDataClient = BetaAnalyticsDataClient.create(settings);
            logger.info("Google Analytics Data Client initialized successfully for Property: {}", propertyId);
            initialHistoricalFetch();
            
        } catch (Exception e) {
            logger.error("Failed to initialize Google Analytics Data Client (GA4).", e);
            this.analyticsDataClient = null;
        }
    }

    private void initialHistoricalFetch() {
        if (analyticsDataClient == null) return;
        if (audienceVisitRepository.findMaxSessionDate().isPresent()) {
            logger.info("Historical audience data already exists. Skipping full historical fetch.");
            return;
        }
        logger.info("Starting initial historical fetch...");
        LocalDate startDate = LocalDate.parse(initialFetchStartDate, GA_DATE_FORMATTER);
        LocalDate endDate = LocalDate.now().minusDays(1); 
        fetchAndSaveGAData(startDate, endDate);
    }
    
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyIncrementalFetch() {
        if (analyticsDataClient == null) {
            logger.warn("Analytics client is not initialized. Skipping scheduled fetch.");
            return;
        }
        
        Optional<LocalDate> maxDateOpt = audienceVisitRepository.findMaxSessionDate();
        LocalDate startDate = maxDateOpt.map(date -> date.plusDays(1))
                                     .orElse(LocalDate.parse(initialFetchStartDate, GA_DATE_FORMATTER));
        
        LocalDate endDate = LocalDate.now().minusDays(1); 

        if (startDate.isBefore(endDate) || startDate.isEqual(endDate)) {
            logger.info("Starting incremental GA data fetch from {} to {}", startDate, endDate);
            fetchAndSaveGAData(startDate, endDate);
        } else {
            logger.info("No new historical data available to fetch. Max date is already up to date.");
        }
    }

    private void fetchAndSaveGAData(LocalDate startDate, LocalDate endDate) {
        if (analyticsDataClient == null) {
            logger.warn("Analytics client is not available. Cannot fetch data.");
            return;
        }

        List<Dimension> dimensions = List.of(
            Dimension.newBuilder().setName("date").build(),                 // 0
            Dimension.newBuilder().setName("sessionSource").build(),        // 1
            Dimension.newBuilder().setName("country").build(),              // 2
            Dimension.newBuilder().setName("region").build(),               // 3
            Dimension.newBuilder().setName("city").build(),                 // 4
            Dimension.newBuilder().setName("deviceCategory").build(),       // 5
            Dimension.newBuilder().setName("operatingSystem").build(),      // 6
            Dimension.newBuilder().setName("landingPage").build(),          // 7
            Dimension.newBuilder().setName("userPseudoId").build(),         // 8
            Dimension.newBuilder().setName("gaSessionId").build(),          // 9
            Dimension.newBuilder().setName("mobileDeviceModel").build(),    // 10
            Dimension.newBuilder().setName("operatingSystemVersion").build(),// 11
            Dimension.newBuilder().setName("screenResolution").build()      // 12
        );
        
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
                .setLimit(100000) 
                .build();

        try {
            RunReportResponse response = analyticsDataClient.runReport(request);
            List<AudienceVisit> visits = mapResponseToEntity(response);
            audienceVisitRepository.saveAll(visits);
            logger.info("Successfully fetched and saved {} historical audience records for period {} to {}", visits.size(), startDate, endDate);
        } catch (Exception e) {
            logger.error("Error fetching historical Analytics Data from GA4 API for period {} to {}", startDate, endDate, e);
        }
    }

    private List<AudienceVisit> mapResponseToEntity(RunReportResponse response) {
        List<AudienceVisit> visits = new ArrayList<>();
        
        for (com.google.analytics.data.v1beta.Row row : response.getRowsList()) {
            AudienceVisit visit = new AudienceVisit();
            visit.setSessionDate(LocalDate.parse(row.getDimensionValues(0).getValue(), DateTimeFormatter.ofPattern("yyyyMMdd")));
            visit.setSessionSource(row.getDimensionValues(1).getValue());
            visit.setCountry(row.getDimensionValues(2).getValue());
            visit.setRegion(row.getDimensionValues(3).getValue());
            visit.setCity(row.getDimensionValues(4).getValue());
            visit.setDeviceCategory(row.getDimensionValues(5).getValue());
            visit.setOperatingSystem(row.getDimensionValues(6).getValue());
            visit.setLandingPage(row.getDimensionValues(7).getValue());
            visit.setClientId(row.getDimensionValues(8).getValue()); 
            visit.setSessionId(row.getDimensionValues(9).getValue());
            visit.setDeviceModel(row.getDimensionValues(10).getValue()); 
            visit.setOsVersion(row.getDimensionValues(11).getValue()); 
            visit.setScreenResolution(row.getDimensionValues(12).getValue());
            
            visit.setViews(Long.valueOf(row.getMetricValues(0).getValue()).intValue()); 
            visit.setSessionDurationSeconds(Math.round(Double.parseDouble(row.getMetricValues(1).getValue())));
            visits.add(visit);
        }
        return visits;
    }

    /**
     * Fetches historical data from the local database based on date range and dynamic filters.
     */
    public List<AudienceDataDto> getHistoricalData(
            LocalDate startDate, 
            LocalDate endDate, 
            AudienceFilter filters) {

        List<AudienceVisit> visits = audienceVisitRepository.findHistoricalDataWithFilters(
            startDate, 
            endDate, 
            filters.getCountry(), 
            filters.getRegion(), 
            filters.getCity(),
            filters.getDeviceCategory(),
            filters.getOperatingSystem(),
            filters.getOsVersion(),
            filters.getSessionSource()
        );
        return visits.stream().map(this::mapEntityToDto).toList();
    }

    /**
     * Fetches distinct, dynamic filter options for the frontend dropdowns.
     */
    public FilterOptionsDto getFilterOptions(LocalDate startDate, LocalDate endDate, AudienceFilter filters) {
        // For each distinct query, we pass all *other* filters to narrow down the choices.
        // For example, when getting regions, we pass the country filter, but not the region filter.
        
        return FilterOptionsDto.builder()
            .countries(audienceVisitRepository.findDistinctCountries(
                startDate, endDate, null, filters.getRegion(), filters.getCity(), filters.getDeviceCategory(),
                filters.getOperatingSystem(), filters.getOsVersion(), filters.getSessionSource()))
            
            .regions(audienceVisitRepository.findDistinctRegions(
                startDate, endDate, filters.getCountry(), null, filters.getCity(), filters.getDeviceCategory(),
                filters.getOperatingSystem(), filters.getOsVersion(), filters.getSessionSource()))
            
            .cities(audienceVisitRepository.findDistinctCities(
                startDate, endDate, filters.getCountry(), filters.getRegion(), null, filters.getDeviceCategory(),
                filters.getOperatingSystem(), filters.getOsVersion(), filters.getSessionSource()))
            
            .deviceCategories(audienceVisitRepository.findDistinctDeviceCategories(
                startDate, endDate, filters.getCountry(), filters.getRegion(), filters.getCity(), null,
                filters.getOperatingSystem(), filters.getOsVersion(), filters.getSessionSource()))

            .operatingSystems(audienceVisitRepository.findDistinctOperatingSystems(
                startDate, endDate, filters.getCountry(), filters.getRegion(), filters.getCity(), filters.getDeviceCategory(),
                null, filters.getOsVersion(), filters.getSessionSource()))

            .osVersions(audienceVisitRepository.findDistinctOsVersions(
                startDate, endDate, filters.getCountry(), filters.getRegion(), filters.getCity(), filters.getDeviceCategory(),
                filters.getOperatingSystem(), null, filters.getSessionSource()))
                
            .sessionSources(audienceVisitRepository.findDistinctSessionSources(
                startDate, endDate, filters.getCountry(), filters.getRegion(), filters.getCity(), filters.getDeviceCategory(),
                filters.getOperatingSystem(), filters.getOsVersion(), null))
            
            .build();
    }

    /**
     * Maps the JPA Entity to the DTO for presentation.
     */
    private AudienceDataDto mapEntityToDto(AudienceVisit entity) {
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