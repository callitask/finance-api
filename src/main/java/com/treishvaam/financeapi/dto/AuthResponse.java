package com.treishvaam.financeapi.dto;

import java.util.List;

public class AuthResponse {
  private String token;
  private String type = "Bearer";
  private Long id;
  private String username;
  private String email;
  private List<String> roles;
  private boolean linkedinConnected;

  public AuthResponse(
      String accessToken,
      Long id,
      String username,
      String email,
      List<String> roles,
      boolean linkedinConnected) {
    this.token = accessToken;
    this.id = id;
    this.username = username;
    this.email = email;
    this.roles = roles;
    this.linkedinConnected = linkedinConnected;
  }

  public boolean isLinkedinConnected() {
    return linkedinConnected;
  }

  public void setLinkedinConnected(boolean linkedinConnected) {
    this.linkedinConnected = linkedinConnected;
  }

  // Getters and Setters
  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
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

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }
}
