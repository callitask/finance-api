package com.treishvaam.financeapi.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * A simple helper DTO to pass around the set of active filters.
 */
@Data
@Builder
public class AudienceFilter {
    private String country;
    private String region;
    private String city;
    private String deviceCategory;
    private String operatingSystem;
    private String osVersion;
    private String sessionSource;
    // Add other fields here if they become filterable
}