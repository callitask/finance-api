package com.treishvaam.financeapi.analytics;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - JPA Entity representing a summarized, unique session visit for the Audience
 * Analytics Dashboard.
 *
 * <p>Scope: - Persists rolled-up telemetry data extracted from raw AnalyticsEvent logs.
 *
 * <p>Critical Dependencies: - Backend: Populated synchronously by AnalyticsService roll-up bridge.
 *
 * <p>Security Constraints: - Raw IPs must never be persisted here. Device clustering uses SHA3-256
 * deviceFingerprint.
 *
 * <p>Non-Negotiables: - Entity fields must map precisely to Liquibase V47 schema expectations.
 *
 * <p>Change Intent: - Expand entity to support YAUAA extraction fidelity and Zero-Trust device
 * clustering.
 *
 * <p>Future AI Guidance: - Do not remove the deviceFingerprint mapping; it is critical for
 * ZKP-gated dashboard grouping.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Added deviceFingerprint, deviceBrand, and
 * deviceClass fields mapped to V47 Liquibase schema. Added idx_audience_fingerprint_jpa index. •
 * 2026-08-05 / Incident 41
 *
 * <p>- STRATEGIC PIVOTS & FAILED ATTEMPTS (CRITICAL FOR FUTURE AI): • Strategy Attempted: N/A •
 * Failure Mode: N/A • Date/Phase: 2026-08-05 • Future AI Warning: N/A
 */
@Entity
@Table(
        name = "audience_visits",
        indexes = {@Index(name = "idx_audience_fingerprint_jpa", columnList = "deviceFingerprint")})
public class AudienceVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "client_id")
    private String clientId;

    private String country;
    private String region;
    private String city;

    @Column(name = "device_category")
    private String deviceCategory;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "operating_system")
    private String operatingSystem;

    @Column(name = "os_version")
    private String osVersion;

    @Column(name = "screen_resolution")
    private String screenResolution;

    @Column(name = "device_fingerprint", length = 64)
    private String deviceFingerprint;

    @Column(name = "device_brand", length = 100)
    private String deviceBrand;

    @Column(name = "device_class", length = 100)
    private String deviceClass;

    @Column(name = "session_duration_seconds")
    private Long sessionDurationSeconds;

    @Column(name = "session_source")
    private String sessionSource;

    @Column(name = "landing_page", columnDefinition = "TEXT")
    private String landingPage;

    private Integer views;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // --- Standard Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDeviceCategory() {
        return deviceCategory;
    }

    public void setDeviceCategory(String deviceCategory) {
        this.deviceCategory = deviceCategory;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(String operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getScreenResolution() {
        return screenResolution;
    }

    public void setScreenResolution(String screenResolution) {
        this.screenResolution = screenResolution;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getDeviceBrand() {
        return deviceBrand;
    }

    public void setDeviceBrand(String deviceBrand) {
        this.deviceBrand = deviceBrand;
    }

    public String getDeviceClass() {
        return deviceClass;
    }

    public void setDeviceClass(String deviceClass) {
        this.deviceClass = deviceClass;
    }

    public Long getSessionDurationSeconds() {
        return sessionDurationSeconds;
    }

    public void setSessionDurationSeconds(Long sessionDurationSeconds) {
        this.sessionDurationSeconds = sessionDurationSeconds;
    }

    public String getSessionSource() {
        return sessionSource;
    }

    public void setSessionSource(String sessionSource) {
        this.sessionSource = sessionSource;
    }

    public String getLandingPage() {
        return landingPage;
    }

    public void setLandingPage(String landingPage) {
        this.landingPage = landingPage;
    }

    public Integer getViews() {
        return views;
    }

    public void setViews(Integer views) {
        this.views = views;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
