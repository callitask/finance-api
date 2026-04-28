/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Data Transfer Object for Audience Analytics Dashboard.
 *
 * <p>Scope: - Responsible for transmitting formatted session, source, device, and temporal data to
 * the frontend.
 *
 * <p>Critical Dependencies: - Backend: AnalyticsService maps entity to this DTO.
 *
 * <p>Security Constraints: - Exposes rawSessionId for internal admin tracking; ensure endpoints
 * returning this are restricted.
 *
 * <p>Non-Negotiables: - Temporal fields MUST be sent as Strings (ISO-8601) to prevent Jackson from
 * serializing them as integer arrays (e.g., [2026,4,28]) which causes "Invalid Date" in JS.
 *
 * <p>Change Intent: - Changed LocalDate and LocalDateTime to String to guarantee safe frontend
 * parsing.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added sessionStartTime (LocalDateTime)
 * and firstVisitDate (LocalDate) fields to provide temporal tracking resolution to the frontend. -
 * EDITED (LATEST): • Converted temporal fields to Strings to fix JS Date parsing crashes.
 */
package com.treishvaam.financeapi.analytics;

public class AudienceDataDto {
  private Long id;
  private String sessionDate;
  private String sessionStartTime;
  private String firstVisitDate;
  private String userIdentifier;
  private String country;
  private String region;
  private String city;
  private String deviceCategory;
  private String deviceModel;
  private String operatingSystem;
  private String osVersion;
  private String screenResolution;
  private String sessionSource;
  private String landingPage;
  private String timeOnSiteFormatted;
  private Integer views;
  private String rawSessionId;

  public AudienceDataDto(
      Long id,
      String sessionDate,
      String sessionStartTime,
      String firstVisitDate,
      String userIdentifier,
      String country,
      String region,
      String city,
      String deviceCategory,
      String deviceModel,
      String operatingSystem,
      String osVersion,
      String screenResolution,
      String sessionSource,
      String landingPage,
      String timeOnSiteFormatted,
      Integer views,
      String rawSessionId) {
    this.id = id;
    this.sessionDate = sessionDate;
    this.sessionStartTime = sessionStartTime;
    this.firstVisitDate = firstVisitDate;
    this.userIdentifier = userIdentifier;
    this.country = country;
    this.region = region;
    this.city = city;
    this.deviceCategory = deviceCategory;
    this.deviceModel = deviceModel;
    this.operatingSystem = operatingSystem;
    this.osVersion = osVersion;
    this.screenResolution = screenResolution;
    this.sessionSource = sessionSource;
    this.landingPage = landingPage;
    this.timeOnSiteFormatted = timeOnSiteFormatted;
    this.views = views;
    this.rawSessionId = rawSessionId;
  }

  public static String formatDuration(Long totalSeconds) {
    if (totalSeconds == null || totalSeconds < 0) return "0s";
    long seconds = totalSeconds % 60;
    long minutes = (totalSeconds / 60) % 60;
    long hours = totalSeconds / 3600;
    if (hours > 0) return String.format("%dhr %dmin %ds", hours, minutes, seconds);
    else if (minutes > 0) return String.format("%dmin %ds", minutes, seconds);
    else return String.format("%ds", seconds);
  }

  // Getters
  public Long getId() {
    return id;
  }

  public String getSessionDate() {
    return sessionDate;
  }

  public String getSessionStartTime() {
    return sessionStartTime;
  }

  public String getFirstVisitDate() {
    return firstVisitDate;
  }

  public String getUserIdentifier() {
    return userIdentifier;
  }

  public String getCountry() {
    return country;
  }

  public String getRegion() {
    return region;
  }

  public String getCity() {
    return city;
  }

  public String getDeviceCategory() {
    return deviceCategory;
  }

  public String getDeviceModel() {
    return deviceModel;
  }

  public String getOperatingSystem() {
    return operatingSystem;
  }

  public String getOsVersion() {
    return osVersion;
  }

  public String getScreenResolution() {
    return screenResolution;
  }

  public String getSessionSource() {
    return sessionSource;
  }

  public String getLandingPage() {
    return landingPage;
  }

  public String getTimeOnSiteFormatted() {
    return timeOnSiteFormatted;
  }

  public Integer getViews() {
    return views;
  }

  public String getRawSessionId() {
    return rawSessionId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Long id;
    private String sessionDate;
    private String sessionStartTime;
    private String firstVisitDate;
    private String userIdentifier;
    private String country;
    private String region;
    private String city;
    private String deviceCategory;
    private String deviceModel;
    private String operatingSystem;
    private String osVersion;
    private String screenResolution;
    private String sessionSource;
    private String landingPage;
    private String timeOnSiteFormatted;
    private Integer views;
    private String rawSessionId;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder sessionDate(String sessionDate) {
      this.sessionDate = sessionDate;
      return this;
    }

    public Builder sessionStartTime(String sessionStartTime) {
      this.sessionStartTime = sessionStartTime;
      return this;
    }

    public Builder firstVisitDate(String firstVisitDate) {
      this.firstVisitDate = firstVisitDate;
      return this;
    }

    public Builder userIdentifier(String userIdentifier) {
      this.userIdentifier = userIdentifier;
      return this;
    }

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

    public Builder deviceCategory(String deviceCategory) {
      this.deviceCategory = deviceCategory;
      return this;
    }

    public Builder deviceModel(String deviceModel) {
      this.deviceModel = deviceModel;
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

    public Builder screenResolution(String screenResolution) {
      this.screenResolution = screenResolution;
      return this;
    }

    public Builder sessionSource(String sessionSource) {
      this.sessionSource = sessionSource;
      return this;
    }

    public Builder landingPage(String landingPage) {
      this.landingPage = landingPage;
      return this;
    }

    public Builder timeOnSiteFormatted(String timeOnSiteFormatted) {
      this.timeOnSiteFormatted = timeOnSiteFormatted;
      return this;
    }

    public Builder views(Integer views) {
      this.views = views;
      return this;
    }

    public Builder rawSessionId(String rawSessionId) {
      this.rawSessionId = rawSessionId;
      return this;
    }

    public AudienceDataDto build() {
      return new AudienceDataDto(
          id,
          sessionDate,
          sessionStartTime,
          firstVisitDate,
          userIdentifier,
          country,
          region,
          city,
          deviceCategory,
          deviceModel,
          operatingSystem,
          osVersion,
          screenResolution,
          sessionSource,
          landingPage,
          timeOnSiteFormatted,
          views,
          rawSessionId);
    }
  }
}
