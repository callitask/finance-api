/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Expose secure endpoints for retrieving processed Audience Analytics data.
 *
 * <p>Scope: - Translates HTTP query parameters into typed AudienceFilters.
 *
 * <p>Critical Dependencies: - Backend: AnalyticsService.
 *
 * <p>Security Constraints: - Must be strictly protected
 * via @PreAuthorize("hasAuthority('ROLE_ADMIN')").
 *
 * <p>Non-Negotiables: - Date fallbacks must remain defensive (e.g., last 7 days) to prevent massive
 * unindexed table scans on default load.
 *
 * <p>Change Intent: - Expand parameters to include `clientId` and `excludeClientIds`.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added `clientId` and `excludeClientIds`
 * to endpoints to support targeted tracking and hiding specific user telemetry.
 */
package com.treishvaam.financeapi.analytics;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<List<AudienceDataDto>> getHistoricalAudienceData(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String operatingSystem,
      @RequestParam(required = false) String osVersion,
      @RequestParam(required = false) String sessionSource,
      @RequestParam(required = false) String clientId,
      @RequestParam(required = false) List<String> excludeClientIds) {

    LocalDate finalStartDate = startDate != null ? startDate : LocalDate.now().minusDays(7);
    LocalDate finalEndDate = endDate != null ? endDate : LocalDate.now();

    AudienceFilter filters =
        AudienceFilter.builder()
            .country(country)
            .region(region)
            .city(city)
            .operatingSystem(operatingSystem)
            .osVersion(osVersion)
            .sessionSource(sessionSource)
            .clientId(clientId)
            .excludeClientIds(excludeClientIds)
            .build();

    List<AudienceDataDto> data =
        analyticsService.getHistoricalData(finalStartDate, finalEndDate, filters);
    return ResponseEntity.ok(data);
  }

  @GetMapping("/filters")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<FilterOptionsDto> getFilterOptions(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String operatingSystem,
      @RequestParam(required = false) String osVersion,
      @RequestParam(required = false) String sessionSource,
      @RequestParam(required = false) String clientId,
      @RequestParam(required = false) List<String> excludeClientIds) {

    LocalDate finalStartDate = startDate != null ? startDate : LocalDate.now().minusDays(7);
    LocalDate finalEndDate = endDate != null ? endDate : LocalDate.now();

    AudienceFilter filters =
        AudienceFilter.builder()
            .country(country)
            .region(region)
            .city(city)
            .operatingSystem(operatingSystem)
            .osVersion(osVersion)
            .sessionSource(sessionSource)
            .clientId(clientId)
            .excludeClientIds(excludeClientIds)
            .build();

    FilterOptionsDto options =
        analyticsService.getFilterOptions(finalStartDate, finalEndDate, filters);
    return ResponseEntity.ok(options);
  }
}
