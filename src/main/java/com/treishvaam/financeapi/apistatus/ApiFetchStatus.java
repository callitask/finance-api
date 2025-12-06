package com.treishvaam.financeapi.apistatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp; // Import this annotation

@Entity
public class ApiFetchStatus {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String apiName;

  @CreationTimestamp // This annotation tells the DB to set the time on creation
  @Column(nullable = false, updatable = false) // Ensures it's only set once
  private LocalDateTime lastFetchTime;

  @Column(nullable = false)
  private String status; // "SUCCESS" or "FAILURE"

  @Column(nullable = false)
  private String triggerSource; // "AUTOMATIC" or "MANUAL"

  @Column(length = 1024)
  private String details; // To store error messages

  public ApiFetchStatus() {}

  public ApiFetchStatus(String apiName, String status, String triggerSource, String details) {
    this.apiName = apiName;
    // We no longer set the time here. The database will handle it.
    this.status = status;
    this.triggerSource = triggerSource;
    this.details = details;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getApiName() {
    return apiName;
  }

  public void setApiName(String apiName) {
    this.apiName = apiName;
  }

  public LocalDateTime getLastFetchTime() {
    return lastFetchTime;
  }

  public void setLastFetchTime(LocalDateTime lastFetchTime) {
    this.lastFetchTime = lastFetchTime;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getTriggerSource() {
    return triggerSource;
  }

  public void setTriggerSource(String triggerSource) {
    this.triggerSource = triggerSource;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }
}
