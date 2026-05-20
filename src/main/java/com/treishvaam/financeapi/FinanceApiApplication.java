package com.treishvaam.financeapi;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Main entry point and configuration class for the Treishvaam Finance API Spring Boot
 * application.
 *
 * <p>Scope: - Bootstraps the application, configures base package scanning, and defines core beans
 * (e.g., ObjectMapper). - Enforces strict repository boundaries between JPA and Elasticsearch to
 * prevent context initialization crashes.
 *
 * <p>Critical Dependencies: - Backend: Spring Boot, JPA, Elasticsearch, Redis (Caching) - Frontend:
 * N/A - Worker / SEO / Sitemap: N/A
 *
 * <p>Security Constraints: - Base packages must be tightly scoped to prevent accidental component
 * scanning of malicious/unintended classes.
 *
 * <p>Non-Negotiables: - Must maintain explicit @EnableJpaRepositories
 * and @EnableElasticsearchRepositories to resolve Spring Data ambiguity. - Must exclude
 * ReactiveElasticsearchRepositoriesAutoConfiguration as the stack uses blocking I/O.
 *
 * <p>Change Intent: - Resolved "Could not safely identify store assignment" crash by explicitly
 * routing repository packages to their respective data stores (JPA vs Elasticsearch) and disabling
 * reactive auto-configuration.
 *
 * <p>Future AI Guidance: - If adding a new data store (e.g., MongoDB, Redis Repositories), you MUST
 * add its respective @EnableXRepositories annotation and explicitly map its package to avoid
 * ambiguous bean collisions.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added explicit `@EnableJpaRepositories`
 * and `@EnableElasticsearchRepositories`. • Added `@SpringBootApplication(exclude =
 * {ReactiveElasticsearchRepositoriesAutoConfiguration.class})`. • Why: The application crashed on
 * startup because Spring Boot's auto-configuration attempted to bind JPA entities to Reactive
 * Elasticsearch and threw ambiguity errors. • Date: 2026-05-20 Phase 6 Observability/Stability Fix
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching; // ADDED: Import for caching
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {ReactiveElasticsearchRepositoriesAutoConfiguration.class})
@EnableScheduling
@EnableCaching // ADDED: Enables Spring's caching capabilities
@EnableJpaRepositories(
    basePackages = {
      "com.treishvaam.financeapi.repository",
      "com.treishvaam.financeapi.analytics",
      "com.treishvaam.financeapi.apistatus",
      "com.treishvaam.financeapi.common",
      "com.treishvaam.financeapi.marketdata",
      "com.treishvaam.financeapi.newshighlight"
    })
@EnableElasticsearchRepositories(basePackages = "com.treishvaam.financeapi.search")
@ComponentScan(
    basePackages = {"com.treishvaam.financeapi", "com.treishvaam.finance"},
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.treishvaam\\.finance\\.marketdata\\..*"))
public class FinanceApiApplication extends SpringBootServletInitializer {

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.sources(FinanceApiApplication.class);
  }

  public static void main(String[] args) {
    SpringApplication.run(FinanceApiApplication.class, args);
  }

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }
}

// Triggering CI/CD Pipeline Build Version v.0.0.0.0.000001
