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
     * Requires ROLE_ADMIN. Accepts a date range filter.
     * @param startDate Optional start date for filtering (format: YYYY-MM-DD)
     * @param endDate Optional end date for filtering (format: YYYY-MM-DD)
     * @return List of AudienceDataDto
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<AudienceDataDto>> getHistoricalAudienceData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Default the dates if not provided
        LocalDate finalStartDate = startDate != null ? startDate : LocalDate.now().minusDays(7);
        LocalDate finalEndDate = endDate != null ? endDate : LocalDate.now();

        List<AudienceDataDto> data = analyticsService.getHistoricalData(finalStartDate, finalEndDate);
        return ResponseEntity.ok(data);
    }
}