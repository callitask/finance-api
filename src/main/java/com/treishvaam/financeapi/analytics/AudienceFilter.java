package com.treishvaam.financeapi.analytics;

public class AudienceFilter {
    private String country;
    private String region;
    private String city;
    private String operatingSystem;
    private String osVersion;
    private String sessionSource;

    public AudienceFilter() {}

    public AudienceFilter(String country, String region, String city, String operatingSystem, String osVersion, String sessionSource) {
        this.country = country;
        this.region = region;
        this.city = city;
        this.operatingSystem = operatingSystem;
        this.osVersion = osVersion;
        this.sessionSource = sessionSource;
    }

    // Getters
    public String getCountry() { return country; }
    public String getRegion() { return region; }
    public String getCity() { return city; }
    public String getOperatingSystem() { return operatingSystem; }
    public String getOsVersion() { return osVersion; }
    public String getSessionSource() { return sessionSource; }

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

        public Builder country(String country) { this.country = country; return this; }
        public Builder region(String region) { this.region = region; return this; }
        public Builder city(String city) { this.city = city; return this; }
        public Builder operatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; return this; }
        public Builder osVersion(String osVersion) { this.osVersion = osVersion; return this; }
        public Builder sessionSource(String sessionSource) { this.sessionSource = sessionSource; return this; }

        public AudienceFilter build() {
            return new AudienceFilter(country, region, city, operatingSystem, osVersion, sessionSource);
        }
    }
}