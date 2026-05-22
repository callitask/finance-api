/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Transfers distinct available filter options to the frontend.
 *
 * <p>Scope: - Required for populating UI dropdowns based on current data state.
 *
 * <p>Critical Dependencies: - AnalyticsService and AudiencePage.js
 *
 * <p>Change Intent: - Add `clientIds` array so the frontend can display them in a multi-select
 * dropdown.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added `clientIds` to support dynamic
 * user exclusion and targeting.
 */
package com.treishvaam.financeapi.analytics;

import java.util.List;

public class FilterOptionsDto {
    private List<String> countries;
    private List<String> regions;
    private List<String> cities;
    private List<String> operatingSystems;
    private List<String> osVersions;
    private List<String> sessionSources;
    private List<String> clientIds;

    public FilterOptionsDto(
            List<String> countries,
            List<String> regions,
            List<String> cities,
            List<String> operatingSystems,
            List<String> osVersions,
            List<String> sessionSources,
            List<String> clientIds) {
        this.countries = countries;
        this.regions = regions;
        this.cities = cities;
        this.operatingSystems = operatingSystems;
        this.osVersions = osVersions;
        this.sessionSources = sessionSources;
        this.clientIds = clientIds;
    }

    // Getters
    public List<String> getCountries() {
        return countries;
    }

    public List<String> getRegions() {
        return regions;
    }

    public List<String> getCities() {
        return cities;
    }

    public List<String> getOperatingSystems() {
        return operatingSystems;
    }

    public List<String> getOsVersions() {
        return osVersions;
    }

    public List<String> getSessionSources() {
        return sessionSources;
    }

    public List<String> getClientIds() {
        return clientIds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> countries;
        private List<String> regions;
        private List<String> cities;
        private List<String> operatingSystems;
        private List<String> osVersions;
        private List<String> sessionSources;
        private List<String> clientIds;

        public Builder countries(List<String> countries) {
            this.countries = countries;
            return this;
        }

        public Builder regions(List<String> regions) {
            this.regions = regions;
            return this;
        }

        public Builder cities(List<String> cities) {
            this.cities = cities;
            return this;
        }

        public Builder operatingSystems(List<String> operatingSystems) {
            this.operatingSystems = operatingSystems;
            return this;
        }

        public Builder osVersions(List<String> osVersions) {
            this.osVersions = osVersions;
            return this;
        }

        public Builder sessionSources(List<String> sessionSources) {
            this.sessionSources = sessionSources;
            return this;
        }

        public Builder clientIds(List<String> clientIds) {
            this.clientIds = clientIds;
            return this;
        }

        public FilterOptionsDto build() {
            return new FilterOptionsDto(
                    countries,
                    regions,
                    cities,
                    operatingSystems,
                    osVersions,
                    sessionSources,
                    clientIds);
        }
    }
}
