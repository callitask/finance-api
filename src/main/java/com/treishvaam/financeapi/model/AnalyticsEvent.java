package com.treishvaam.financeapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

/**
 * AI-CONTEXT: Purpose: JPA Entity for First-Party Analytics tracking. Security Constraints: MUST
 * NEVER map or store raw IP addresses. IMMUTABLE CHANGE HISTORY: - ADDED (Phase 5): Entity creation
 * mapped to V46 schema. - EDITED (Phase 6 Fix): • Added @JsonIgnoreProperties(ignoreUnknown = true)
 * to class level. • Added @JsonProperty(access = JsonProperty.Access.READ_ONLY) to the primary key
 * 'id'. • Why: Resolves a critical 500 Internal Server Error. The frontend Web Vitals telemetry
 * payload sends an 'id' string (e.g., "v2-123") and custom metric fields. Jackson attempted to
 * deserialize this string into the database Long PK, and crashed on the unmapped properties. These
 * annotations enforce strict mass-assignment protection and allow dynamic telemetry ingestion. -
 * EDITED (Incident 41 - Zero-Trust Device Fingerprinting): • Added deviceFingerprint column and
 * accessors mapped to V47 Liquibase schema. Added idx_analytics_fingerprint_jpa index. • Date:
 * 2026-08-05 - EDITED (Phase 7 - Data Fidelity Fix): • Added `screenResolution` and
 * `platformVersion` fields to capture high-entropy hardware metrics. • Added `@Transient` map for
 * `extra` payload extraction to bypass Jackson's `ignoreUnknown`.
 */
@Entity
@Table(
        name = "analytics_events",
        indexes = {
            @Index(name = "idx_analytics_session_jpa", columnList = "sessionId"),
            @Index(name = "idx_analytics_type_jpa", columnList = "eventType"),
            @Index(name = "idx_analytics_timestamp_jpa", columnList = "createdAt"),
            @Index(name = "idx_analytics_fingerprint_jpa", columnList = "deviceFingerprint")
        })
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(length = 1000)
    private String url;

    @Column(length = 500)
    private String path;

    @Column(length = 1000)
    private String referrer;

    @Column(length = 10)
    private String countryCode;

    @Column(length = 255)
    private String city;

    @Column(length = 50)
    private String deviceType;

    @Column(length = 100)
    private String browser;

    @Column(length = 100)
    private String os;

    @Column(length = 1000)
    private String userAgent;

    @Column(name = "device_fingerprint", length = 64)
    private String deviceFingerprint;

    @Column(length = 50)
    private String screenResolution;

    @Column(length = 50)
    private String platformVersion;

    @Transient
    @JsonProperty("extra")
    private Map<String, Object> extra;

    private Integer scrollDepth;

    private Long timeOnPageMs;

    @Column(length = 50)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getScreenResolution() {
        return screenResolution;
    }

    public void setScreenResolution(String screenResolution) {
        this.screenResolution = screenResolution;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public void setPlatformVersion(String platformVersion) {
        this.platformVersion = platformVersion;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public Integer getScrollDepth() {
        return scrollDepth;
    }

    public void setScrollDepth(Integer scrollDepth) {
        this.scrollDepth = scrollDepth;
    }

    public Long getTimeOnPageMs() {
        return timeOnPageMs;
    }

    public void setTimeOnPageMs(Long timeOnPageMs) {
        this.timeOnPageMs = timeOnPageMs;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
