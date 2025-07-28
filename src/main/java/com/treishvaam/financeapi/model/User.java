package com.treishvaam.financeapi.model;

import jakarta.persistence.*;
import lombok.Data;
// import lombok.NoArgsConstructor; // Keeping this is fine, but the explicit constructor is safer.
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "email")
       })
@Data
// @NoArgsConstructor // This annotation is causing issues in the build, so we add a manual constructor.
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
               joinColumns = @JoinColumn(name = "user_id"),
               inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Column(name = "linkedin_access_token", length = 1024)
    private String linkedinAccessToken;

    @Column(name = "linkedin_token_expiry")
    private Instant linkedinTokenExpiry;

    @Column(name = "linkedin_urn")
    private String linkedinUrn;
    
    // --- FIX START: Added required no-argument constructor for JPA ---
    public User() {
    }
    // --- FIX END ---

    public User(String email, String password) {
        this.email = email;
        this.password = password;
        this.enabled = true;
    }

    // Explicit Getters and Setters (Lombok's @Data also provides these)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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