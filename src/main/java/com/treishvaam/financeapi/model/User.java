package com.treishvaam.financeapi.model;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Core User entity for authentication and authorization.
 *
 * <p>Change Intent: - Implement Phase 6 (ENC-02): Encrypt user emails at rest.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (Phase 6): • Removed
 * `@UniqueConstraint(columnNames = "email")` from the Table annotation. • Changed `email` column
 * definition to `TEXT` and added `@Convert(converter = EncryptedStringConverter.class)`. • Why:
 * Encrypted data produces highly randomized ciphertexts (due to random IVs). A database unique
 * constraint cannot validate uniqueness on AES-GCM ciphertexts. Column size expanded to TEXT to
 * prevent truncation of Base64 ciphertext.
 *
 * <p>- EDITED (Phase 6 Fix): • Replaced `EncryptedStringConverter.class` with
 * `UserEmailConverter.class` for the email field. • Why: Isolate encryption keys per domain
 * (ENC-User & ENC-Domain).
 */
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.treishvaam.financeapi.security.EncryptedStringConverter;
import com.treishvaam.financeapi.security.UserEmailConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames =
                            "username") // Email uniqueness is now enforced at the application layer
        })
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    // Phase 6: PII Encryption at Rest (Domain-Specific)
    @Column(nullable = false, columnDefinition = "TEXT")
    @Convert(converter = UserEmailConverter.class)
    private String email;

    // Phase 1: New Profile Name field for SEO
    @Column(name = "display_name")
    private String displayName;

    @Column(nullable = false)
    @JsonIgnore // Phase 1 Fix: Prevent Security Leak & Recursion
    private String password;

    @Column(nullable = false)
    private boolean enabled;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    // CVE-007 FIX: LinkedIn access token encrypted at rest using AES-256-GCM.
    @Column(name = "linkedin_access_token", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    @JsonIgnore // Phase 1 Fix: Prevent Token Leak
    private String linkedinAccessToken;

    @Column(name = "linkedin_token_expiry")
    private Instant linkedinTokenExpiry;

    @Column(name = "linkedin_urn")
    private String linkedinUrn;

    public User() {}

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public String getLinkedinAccessToken() {
        return linkedinAccessToken;
    }

    public void setLinkedinAccessToken(String linkedinAccessToken) {
        this.linkedinAccessToken = linkedinAccessToken;
    }

    public Instant getLinkedinTokenExpiry() {
        return linkedinTokenExpiry;
    }

    public void setLinkedinTokenExpiry(Instant linkedinTokenExpiry) {
        this.linkedinTokenExpiry = linkedinTokenExpiry;
    }

    public String getLinkedinUrn() {
        return linkedinUrn;
    }

    public void setLinkedinUrn(String linkedinUrn) {
        this.linkedinUrn = linkedinUrn;
    }
}
