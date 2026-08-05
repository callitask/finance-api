package com.treishvaam.financeapi.config;

import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Global configuration for the YAUAA (Yet Another UserAgent Analyzer) engine.
 *
 * <p>Scope: - Enforces a Singleton memory allocation for YAUAA across the entire Spring Boot
 * application.
 *
 * <p>Critical Dependencies: - Consumed by AnalyticsService and MonitoringController.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 *
 * <p>- ADDED (Incident 57/58/59 - JVM OOM Prevention): • Created centralized configuration to
 * prevent Duplicate YAUAA instantiations. • Reduced LRU cache from 10,000 to 2,500. • Why: Two
 * massive Regex Tries exhausted the 256MB JVM Heap causing an OOM loop. The reduced cache
 * guarantees long-term Garbage Collection stability within the VirtualBox cgroup limits.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
@Configuration
public class YauaaConfig {

    @Bean
    public UserAgentAnalyzer userAgentAnalyzer() {
        return UserAgentAnalyzer.newBuilder().hideMatcherLoadStats().withCache(2500).build();
    }
}
