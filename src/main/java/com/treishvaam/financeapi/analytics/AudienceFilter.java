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
 * <p>Change Intent: - Added `clientId` for explicit user inclusion filtering. - Added
 * `excludeClientIds` list for explicit user exclusion (hiding specific traffic).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added `clientId` and `excludeClientIds`
 * fields and updated builder to support targeted user tracking.
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
  private String clientId;
  private List<String> excludeClientIds;

  public AudienceFilter() {}

  public AudienceFilter(
      String country,
      String region,
      String city,
      String operatingSystem,
      String osVersion,
      String sessionSource,
      String clientId,
      List<String> excludeClientIds) {
    this.country = country;
    this.region = region;
    this.city = city;
    this.operatingSystem = operatingSystem;
    this.osVersion = osVersion;
    this.sessionSource = sessionSource;
    this.clientId = clientId;
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

  public String getClientId() {
    return clientId;
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
    private String clientId;
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

    public Builder clientId(String clientId) {
      this.clientId = clientId;
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
          clientId,
          excludeClientIds);
    }
  }
}
