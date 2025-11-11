package com.treishvaam.financeapi.analytics;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity to store historical, comprehensive Google Analytics audience data.
 * Moved from com.treishvaam.financeapi.model to consolidate analytics components.
 */
@Entity
@Table(name = "audience_visits")
@Data // FIX: Lombok will now generate getters/setters/equals/hashCode
public class AudienceVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // GA Dimension: date
    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    // GA Dimension: sessionId (used for session count, often aggregated)
    @Column(name = "session_id")
    private String sessionId;

    // GA Dimension: clientId (used as 'user' identifier)
    @Column(name = "client_id")
    private String clientId;

    // Location
    private String country;
    private String region;
    private String city;

    // Device / OS
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

    // Metrics / Traffic
    @Column(name = "session_duration_seconds")
    private Long sessionDurationSeconds;

    @Column(name = "session_source")
    private String sessionSource;

    @Column(name = "landing_page", columnDefinition = "TEXT")
    private String landingPage;

    // Views/Sessions (GA reports data aggregated by dimensions, this stores the count)
    private Integer views;

    @CreationTimestamp // Use Hibernate's annotation for automatic timestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}