package com.treishvaam.financeapi.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO to transport available (distinct) filter options to the frontend.
 * These lists are dynamically generated based on other active filters.
 */
@Data
@Builder
public class FilterOptionsDto {
    private List<String> countries;
    private List<String> regions;
    private List<String> cities;
    private List<String> deviceCategories;
    private List<String> operatingSystems;
    private List<String> osVersions;
    private List<String> sessionSources;
    // We can add landingPage or others here if needed in the future
}