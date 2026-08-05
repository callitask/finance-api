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
 * <p>Change Intent: - Added `targetClientIds` and `excludeClientIds` list mapping. - Added `POST
 * /refresh` endpoint to manually trigger a GA4 data sync override.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added `clientId` and `excludeClientIds`
 * to endpoints to support targeted tracking and hiding specific user telemetry. - EDITED (LATEST):
 * • Changed `clientId` to List `targetClientIds`. • Added `refreshGA4Data` endpoint. - EDITED
 * (Incident 33 - JPQL Empty String Sanitization): • Added `sanitizeParam` helper to convert empty
 * strings `""` to `null`. • Why: Spring MVC binds empty URL parameters (e.g., `&country=`) as `""`.
 * When passed to the repository, `"" IS NULL` evaluates to FALSE, causing the database to silently
 * drop 100% of the rows and return an empty `[]` payload to the frontend. Sanitizing to `null`
 * perfectly satisfies the JPQL filter logic. - EDITED (Incident 41 - Zero-Trust Device Clustering):
 * • Updated getHistoricalAudienceData to group flat AudienceDataDto into GroupedAudienceDataDto
 * based on deviceFingerprint. • Why: Aggregates incognito and standard sessions from the same
 * device into a single hardware profile without storing raw IPs. • Date: 2026-08-05
 */
package com.treishvaam.financeapi.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // Enterprise Parameter Sanitization: Prevents JPQL "IS NULL" filter drop
    private String sanitizeParam(String param) {
        return (param != null && !param.trim().isEmpty()) ? param.trim() : null;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<GroupedAudienceDataDto>> getHistoricalAudienceData(
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
            @RequestParam(required = false) List<String> targetClientIds,
            @RequestParam(required = false) List<String> excludeClientIds) {

        LocalDate finalStartDate = startDate != null ? startDate : LocalDate.now().minusDays(7);
        LocalDate finalEndDate = endDate != null ? endDate : LocalDate.now();

        AudienceFilter filters =
                AudienceFilter.builder()
                        .country(sanitizeParam(country))
                        .region(sanitizeParam(region))
                        .city(sanitizeParam(city))
                        .operatingSystem(sanitizeParam(operatingSystem))
                        .osVersion(sanitizeParam(osVersion))
                        .sessionSource(sanitizeParam(sessionSource))
                        .targetClientIds(targetClientIds)
                        .excludeClientIds(excludeClientIds)
                        .build();

        List<AudienceDataDto> data =
                analyticsService.getHistoricalData(finalStartDate, finalEndDate, filters);

        // Group sessions by deviceFingerprint
        Map<String, List<AudienceDataDto>> groupedMap =
                data.stream()
                        .collect(
                                Collectors.groupingBy(
                                        dto ->
                                                dto.getDeviceFingerprint() != null
                                                        ? dto.getDeviceFingerprint()
                                                        : "unknown-device"));

        List<GroupedAudienceDataDto> groupedData =
                groupedMap.entrySet().stream()
                        .map(
                                entry -> {
                                    String fingerprint = entry.getKey();
                                    List<AudienceDataDto> sessions = entry.getValue();

                                    // Sort sessions newest first
                                    sessions.sort(
                                            (a, b) -> {
                                                String timeA =
                                                        a.getSessionStartTime() != null
                                                                ? a.getSessionStartTime()
                                                                : "";
                                                String timeB =
                                                        b.getSessionStartTime() != null
                                                                ? b.getSessionStartTime()
                                                                : "";
                                                return timeB.compareTo(timeA);
                                            });

                                    AudienceDataDto latestSession = sessions.get(0);

                                    return new GroupedAudienceDataDto(
                                            fingerprint,
                                            latestSession.getDeviceBrand(),
                                            latestSession.getDeviceClass(),
                                            latestSession.getDeviceModel(),
                                            latestSession.getOperatingSystem(),
                                            latestSession.getOsVersion(),
                                            sessions.size(),
                                            latestSession.getSessionStartTime() != null
                                                    ? latestSession.getSessionStartTime()
                                                    : latestSession.getSessionDate(),
                                            sessions);
                                })
                        .sorted(
                                (a, b) -> {
                                    String timeA = a.getLastSeen() != null ? a.getLastSeen() : "";
                                    String timeB = b.getLastSeen() != null ? b.getLastSeen() : "";
                                    return timeB.compareTo(timeA);
                                })
                        .collect(Collectors.toList());

        return ResponseEntity.ok(groupedData);
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
            @RequestParam(required = false) List<String> targetClientIds,
            @RequestParam(required = false) List<String> excludeClientIds) {

        LocalDate finalStartDate = startDate != null ? startDate : LocalDate.now().minusDays(7);
        LocalDate finalEndDate = endDate != null ? endDate : LocalDate.now();

        AudienceFilter filters =
                AudienceFilter.builder()
                        .country(sanitizeParam(country))
                        .region(sanitizeParam(region))
                        .city(sanitizeParam(city))
                        .operatingSystem(sanitizeParam(operatingSystem))
                        .osVersion(sanitizeParam(osVersion))
                        .sessionSource(sanitizeParam(sessionSource))
                        .targetClientIds(targetClientIds)
                        .excludeClientIds(excludeClientIds)
                        .build();

        FilterOptionsDto options =
                analyticsService.getFilterOptions(finalStartDate, finalEndDate, filters);
        return ResponseEntity.ok(options);
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> refreshGA4Data(@RequestBody Map<String, String> payload) {
        try {
            LocalDate start = LocalDate.parse(payload.get("startDate"));
            LocalDate end = LocalDate.parse(payload.get("endDate"));

            analyticsService.refreshGA4Data(start, end);
            return ResponseEntity.ok()
                    .body(
                            Map.of(
                                    "message",
                                    "GA4 sync completed successfully for the selected dates. Faro native data was preserved."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
