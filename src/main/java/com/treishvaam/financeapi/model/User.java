package com.treishvaam.financeapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = "username"), // --- ADDED CONSTRAINT ---
      @UniqueConstraint(columnNames = "email")
    })
@Data
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // --- NEW FIELD ADDED ---
  @Column(nullable = false)
  private String username;

  @Column(nullable = false)
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

  @Column(name = "linkedin_access_token", length = 1024)
  @JsonIgnore // Phase 1 Fix: Prevent Token Leak
  private String linkedinAccessToken;

  @Column(name = "linkedin_token_expiry")
  private Instant linkedinTokenExpiry;

  @Column(name = "linkedin_urn")
  private String linkedinUrn;

  public User() {}

  // --- CONSTRUCTOR UPDATED ---
  public User(String username, String email, String password) {
    this.username = username;
    this.email = email;
    this.password = password;
    this.enabled = true;
  }

  // Explicit Getters and Setters (Lombok's @Data also provides these)
  // No changes needed below this line, but included for completeness.
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
