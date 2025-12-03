package com.treishvaam.financeapi.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;       // e.g., "DELETE_POST", "LOGIN_ATTEMPT"
    private String performedBy;  // Username or "ANONYMOUS"
    private String targetEntity; // e.g., "Post ID: 123"
    
    @Column(columnDefinition = "TEXT")
    private String details;      // Full details/error message
    
    private String ipAddress;
    private String status;       // "SUCCESS", "FAILURE"
    private LocalDateTime timestamp;

    public AuditLog() {}

    public AuditLog(String action, String performedBy, String targetEntity, String details, String ipAddress, String status) {
        this.action = action;
        this.performedBy = performedBy;
        this.targetEntity = targetEntity;
        this.details = details;
        this.ipAddress = ipAddress;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}