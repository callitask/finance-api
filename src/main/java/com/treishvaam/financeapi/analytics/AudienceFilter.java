/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Filter encapsulation class for passing audience dashboard querying criteria to the
 * repository.
 *
 * <p>Scope: - Responsible for holding standard dimensions and newly added user identity filters.
 *
 * <p>Critical Dependencies: - AnalyticsController / AnalyticsService.
 *
 * <p>Change Intent: - Upgraded `clientId` (String) to `targetClientIds` (List) for multi-select
 * dropdown support.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added `clientId` and `excludeClientIds`
 * fields and updated builder to support targeted user tracking. - EDITED (LATEST): • Changed
 * `clientId` to a List (`targetClientIds`) to support multi-select inclusional filtering.
 */
package com.treishvaam.financeapi.analytics;

import java.util.List;

public class AudienceFilter {
    private String country;
    private String region;
    private String city;
    private String operatingSystem;
    private String osVersion;
    private String sessionSource;
    private List<String> targetClientIds;
    private List<String> excludeClientIds;

    public AudienceFilter() {}

    public AudienceFilter(
            String country,
            String region,
            String city,
            String operatingSystem,
            String osVersion,
            String sessionSource,
            List<String> targetClientIds,
            List<String> excludeClientIds) {
        this.country = country;
        this.region = region;
        this.city = city;
        this.operatingSystem = operatingSystem;
        this.osVersion = osVersion;
        this.sessionSource = sessionSource;
        this.targetClientIds = targetClientIds;
        this.excludeClientIds = excludeClientIds;
    }

    // Getters
    public String getCountry() {
        return country;
    }

    public String getRegion() {
        return region;
    }

    public String getCity() {
        return city;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getSessionSource() {
        return sessionSource;
    }

    public List<String> getTargetClientIds() {
        return targetClientIds;
    }

    public List<String> getExcludeClientIds() {
        return excludeClientIds;
    }

    // Builder Implementation
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String country;
        private String region;
        private String city;
        private String operatingSystem;
        private String osVersion;
        private String sessionSource;
        private List<String> targetClientIds;
        private List<String> excludeClientIds;

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder city(String city) {
            this.city = city;
            return this;
        }

        public Builder operatingSystem(String operatingSystem) {
            this.operatingSystem = operatingSystem;
            return this;
        }

        public Builder osVersion(String osVersion) {
            this.osVersion = osVersion;
            return this;
        }

        public Builder sessionSource(String sessionSource) {
            this.sessionSource = sessionSource;
            return this;
        }

        public Builder targetClientIds(List<String> targetClientIds) {
            this.targetClientIds = targetClientIds;
            return this;
        }

        public Builder excludeClientIds(List<String> excludeClientIds) {
            this.excludeClientIds = excludeClientIds;
            return this;
        }

        public AudienceFilter build() {
            return new AudienceFilter(
                    country,
                    region,
                    city,
                    operatingSystem,
                    osVersion,
                    sessionSource,
                    targetClientIds,
                    excludeClientIds);
        }
    }
}
