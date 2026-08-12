package com.treishvaam.financeapi.userpreferences.model;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Entity representing user-specific UX preferences (Radar Toolbar, Highlight Theme).
 *
 * <p>Scope: - Persists toolbar visibility and state across devices.
 *
 * <p>Security Constraints: - Enforces tenant isolation via tenantId field, audited by
 * AegisMainFilter. - Bound securely to Keycloak UUID (userSub).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created isolated package boundary for
 * user preferences to comply with enterprise architectural standards. • Date/Phase: Phase 1
 * (Feature Packaging)
 */
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userSub;

    @Column(nullable = false)
    private String tenantId;

    private boolean radarToolbarEnabled = true;
    private String defaultHighlightColor = "bg-yellow-200";

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserSub() {
        return userSub;
    }

    public void setUserSub(String userSub) {
        this.userSub = userSub;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isRadarToolbarEnabled() {
        return radarToolbarEnabled;
    }

    public void setRadarToolbarEnabled(boolean radarToolbarEnabled) {
        this.radarToolbarEnabled = radarToolbarEnabled;
    }

    public String getDefaultHighlightColor() {
        return defaultHighlightColor;
    }

    public void setDefaultHighlightColor(String defaultHighlightColor) {
        this.defaultHighlightColor = defaultHighlightColor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
