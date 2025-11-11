package com.treishvaam.financeapi.analytics;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

/**
 * DTO for transferring detailed historical audience data to the frontend.
 */
@Data
@Builder
public class AudienceDataDto {
    private Long id;
    private LocalDate sessionDate;
    
    private String userIdentifier; // clientId
    
    // Location
    private String country;
    private String region;
    private String city;
    
    // Device / OS
    private String deviceCategory;
    private String deviceModel;
    private String operatingSystem; 
    private String osVersion; 
    private String screenResolution;
    
    // Traffic / Metrics
    private String sessionSource;
    private String landingPage;
    private String timeOnSiteFormatted; // H:M:S format
    private Integer views;
    
    // Utility
    private String rawSessionId; 
    
    /**
     * Utility method to convert seconds to H:M:S format.
     */
    public static String formatDuration(Long totalSeconds) {
        if (totalSeconds == null || totalSeconds < 0) return "0s";
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;

        if (hours > 0) {
            return String.format("%dhr %dmin %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dmin %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}