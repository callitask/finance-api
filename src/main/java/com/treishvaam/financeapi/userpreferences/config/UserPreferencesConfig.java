/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Explicit Spring Boot configuration for the isolated User Preferences domain
 * package.
 *
 * <p>Scope: - Registers the entities and JPA repositories for this specific domain.
 *
 * <p>Critical Dependencies: - Backend: Spring Data JPA, Hibernate.
 *
 * <p>Security Constraints: - Prevents cross-domain pollution by explicitly targeting only the
 * userpreferences sub-packages.
 *
 * <p>Non-Negotiables: - Must exist to bypass strict explicit repository scan boundaries defined
 * elsewhere (e.g. AegisHibernateConfig).
 *
 * <p>Change Intent: - Resolves the UnsatisfiedDependencyException that crashed the Spring Context
 * during boot by forcefully registering the new repository.
 *
 * <p>Future AI Guidance: - Do not delete. If new sub-packages are added to userpreferences, ensure
 * they are scanned here.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created UserPreferencesConfig to
 * explicitly declare @EnableJpaRepositories and @EntityScan. • Reason: Spring Boot strict
 * repository mode ignored the new package due to explicit configuration lock-downs elsewhere,
 * causing the container restart loop. • Date/Phase: Phase 1 (Dependency Injection Fix)
 */
package com.treishvaam.financeapi.userpreferences.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.treishvaam.financeapi.userpreferences.repository")
@EntityScan(basePackages = "com.treishvaam.financeapi.userpreferences.model")
public class UserPreferencesConfig {}
