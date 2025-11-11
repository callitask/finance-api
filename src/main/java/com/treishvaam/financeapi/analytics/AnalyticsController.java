package com.treishvaam.financeapi.analytics;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Endpoint to fetch historical audience data from the local database.
     * Requires ROLE_ADMIN. Accepts a date range and optional multi-level filters.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<AudienceDataDto>> getHistoricalAudienceData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String city, // Added back
            // 'deviceCategory' removed
            @RequestParam(required = false) String operatingSystem,
            @RequestParam(required = false) String osVersion,
            @RequestParam(required = false) String sessionSource
            ) {

        // Default the dates if not provided
        LocalDate finalStartDate = startDate != null ? startDate : LocalDate.now().minusDays(7);
        LocalDate finalEndDate = endDate != null ? endDate : LocalDate.now();

        // Package filters into a single object for the service
        AudienceFilter filters = AudienceFilter.builder()
            .country(country)
            .region(region)
            .city(city) // Added back
            // 'deviceCategory' removed
            .operatingSystem(operatingSystem)
            .osVersion(osVersion)
            .sessionSource(sessionSource)
            .build();

        List<AudienceDataDto> data = analyticsService.getHistoricalData(
            finalStartDate, 
            finalEndDate,
            filters
        );
        return ResponseEntity.ok(data);
    }

    /**
     * Endpoint to fetch dynamic, distinct filter options based on the current context.
     * This powers the cascading dropdowns on the frontend.
     */
    @GetMapping("/filters")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<FilterOptionsDto> getFilterOptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String city, // Added back
            // 'deviceCategory' removed
            @RequestParam(required = false) String operatingSystem,
            @RequestParam(required = false) String osVersion,
            @RequestParam(required = false) String sessionSource
    ) {
        // Default the dates if not provided
        LocalDate finalStartDate = startDate != null ? startDate : LocalDate.now().minusDays(7);
        LocalDate finalEndDate = endDate != null ? endDate : LocalDate.now();

        // Package filters into a single object for the service
        AudienceFilter filters = AudienceFilter.builder()
            .country(country)
            .region(region)
            .city(city) // Added back
            // 'deviceCategory' removed
            .operatingSystem(operatingSystem)
            .osVersion(osVersion)
            .sessionSource(sessionSource)
            .build();

        FilterOptionsDto options = analyticsService.getFilterOptions(finalStartDate, finalEndDate, filters);
        return ResponseEntity.ok(options);
    }
}