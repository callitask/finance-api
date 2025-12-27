package com.treishvaam.financeapi.analytics;

import java.util.List;

public class FilterOptionsDto {
  private List<String> countries;
  private List<String> regions;
  private List<String> cities;
  private List<String> operatingSystems;
  private List<String> osVersions;
  private List<String> sessionSources;

  public FilterOptionsDto(
      List<String> countries,
      List<String> regions,
      List<String> cities,
      List<String> operatingSystems,
      List<String> osVersions,
      List<String> sessionSources) {
    this.countries = countries;
    this.regions = regions;
    this.cities = cities;
    this.operatingSystems = operatingSystems;
    this.osVersions = osVersions;
    this.sessionSources = sessionSources;
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

    public FilterOptionsDto build() {
      return new FilterOptionsDto(
          countries, regions, cities, operatingSystems, osVersions, sessionSources);
    }
  }
}
