package com.treishvaam.financeapi;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Main entry point and configuration class for the Treishvaam Finance API Spring Boot
 * application.
 *
 * <p>Scope: - Bootstraps the application, configures base package scanning, and defines core beans
 * (e.g., ObjectMapper). - Enforces strict repository boundaries between JPA, Elasticsearch, and
 * Redis to prevent context initialization crashes.
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
 * reactive and unintended Redis auto-configurations.
 *
 * <p>Future AI Guidance: - If adding a new data store (e.g., MongoDB), you MUST add its
 * respective @EnableXRepositories annotation and explicitly map its package to avoid ambiguous bean
 * collisions. If adding ACTUAL Redis repositories in the future, remove the exclusion and
 * explicitly map the Redis repository packages.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added explicit `@EnableJpaRepositories`
 * and `@EnableElasticsearchRepositories`. • Added `@SpringBootApplication(exclude =
 * {ReactiveElasticsearchRepositoriesAutoConfiguration.class})`. • Why: The application crashed on
 * startup because Spring Boot's auto-configuration attempted to bind JPA entities to Reactive
 * Elasticsearch and threw ambiguity errors. • Date: 2026-05-20 Phase 6 Observability/Stability Fix
 * * - EDITED (Phase 6 - Repository Boundary Hardening): • Added
 * `RedisRepositoriesAutoConfiguration.class` to the `@SpringBootApplication(exclude = {...})` list.
 * • Why: Spring Data Redis auto-configuration was aggressively attempting to assign JPA and
 * Elasticsearch repository interfaces as Redis Repositories because the Redis caching dependency is
 * present. Explicitly excluding the repository auto-config resolves the "Could not safely identify
 * store assignment" initialization warnings without disabling `@EnableCaching` features.
 *
 * <p>- EDITED (Phase 1 / Incident 107 - Spring Context Restore): • Added `@EntityScan(basePackages
 * = "com.treishvaam.financeapi")` to permanently immunize the app against localized entity scan
 * overrides. • Appended `"com.treishvaam.financeapi.userpreferences.repository"` to the
 * `@EnableJpaRepositories` array. • Why: A previously deployed localized config for user
 * preferences blinded Hibernate to the legacy entities (`AudienceVisit`), causing `Not a managed
 * type` crashes. Centralizing the scanning boundaries restores boot integrity.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        exclude = {
            ReactiveElasticsearchRepositoriesAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
        })
@EnableScheduling
@EnableCaching
@EntityScan(basePackages = "com.treishvaam.financeapi")
@EnableJpaRepositories(
        basePackages = {
            "com.treishvaam.financeapi.repository",
            "com.treishvaam.financeapi.analytics",
            "com.treishvaam.financeapi.apistatus",
            "com.treishvaam.financeapi.common",
            "com.treishvaam.financeapi.marketdata",
            "com.treishvaam.financeapi.newshighlight",
            "com.treishvaam.financeapi.userpreferences.repository"
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
