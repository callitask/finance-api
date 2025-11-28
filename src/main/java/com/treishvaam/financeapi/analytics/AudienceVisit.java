package com.treishvaam.financeapi.analytics;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "audience_visits")
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getSessionDate() { return sessionDate; }
    public void setSessionDate(LocalDate sessionDate) { this.sessionDate = sessionDate; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDeviceCategory() { return deviceCategory; }
    public void setDeviceCategory(String deviceCategory) { this.deviceCategory = deviceCategory; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }

    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }

    public String getScreenResolution() { return screenResolution; }
    public void setScreenResolution(String screenResolution) { this.screenResolution = screenResolution; }

    public Long getSessionDurationSeconds() { return sessionDurationSeconds; }
    public void setSessionDurationSeconds(Long sessionDurationSeconds) { this.sessionDurationSeconds = sessionDurationSeconds; }

    public String getSessionSource() { return sessionSource; }
    public void setSessionSource(String sessionSource) { this.sessionSource = sessionSource; }

    public String getLandingPage() { return landingPage; }
    public void setLandingPage(String landingPage) { this.landingPage = landingPage; }

    public Integer getViews() { return views; }
    public void setViews(Integer views) { this.views = views; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}