/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Binds the `AegisQueryInterceptor` to the active Hibernate Session Factory. -
 * Activates AEGIS Part-K (K4) Query Signatures.
 *
 * <p>Scope: - Injects custom Hibernate properties during the Spring Boot JPA initialization phase.
 *
 * <p>Critical Dependencies: - Backend: `AegisQueryInterceptor`.
 *
 * <p>Security Constraints: - Must not disrupt database connection pooling.
 *
 * <p>Non-Negotiables: - Use `HibernatePropertiesCustomizer` for native Spring Boot 3.x
 * compatibility.
 *
 * <p>Change Intent: - Fixing an architectural gap: An interceptor without registration is dead
 * code. This enforces the registration.
 *
 * <p>Future AI Guidance: - Do not remove this bean if transitioning to a different SQL dialect; it
 * operates at the statement string level.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Batch 8): • Created `AegisHibernateConfig`
 * to bind the `AegisQueryInterceptor`. • Why: Finalizes the Zero-Trust Database driver-level
 * signature enforcement.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.AegisQueryInterceptor;
import java.util.Map;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AegisHibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernateCustomizer(
            AegisQueryInterceptor aegisQueryInterceptor) {
        return (Map<String, Object> hibernateProperties) -> {
            hibernateProperties.put(
                    "hibernate.session_factory.statement_inspector", aegisQueryInterceptor);
        };
    }
}
