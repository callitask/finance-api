/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Bootstraps core application data (roles, admin user) on startup.
 *
 * <p>Scope: - Ensures critical identity and access management entities exist in the database.
 *
 * <p>Critical Dependencies: - UserRepository, RoleRepository. - Liquibase (must run after schema
 * migration).
 *
 * <p>Security Constraints: - Admin credentials must be injected securely via properties, never
 * hardcoded. - Must strictly execute under the 'finance' tenant context to prevent data leakage or
 * crash.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Initial DataInitializer for seeding Roles
 * and Admin user.
 *
 * <p>- EDITED (Tenant Context Isolation & Crash Fix): • Wrapped repository calls in explicit
 * `TenantContext.setTenantId("finance")` and `try-catch` blocks. • Why: The application crashed
 * gracefully immediately after Tomcat started because `roleRepository.findByName()` threw an
 * unhandled `HibernateException`. As a `CommandLineRunner` executing on the main thread, it
 * bypassed the standard web `TenantInterceptor`. Enforcing the tenant context prevents the fatal
 * exception, while the `try-catch` ensures any seeding failure does not bring down the entire JVM.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.config.tenant.TenantContext;
import com.treishvaam.financeapi.model.ERole;
import com.treishvaam.financeapi.model.Role;
import com.treishvaam.financeapi.model.User;
import com.treishvaam.financeapi.repository.RoleRepository;
import com.treishvaam.financeapi.repository.UserRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@DependsOn("liquibase")
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    public DataInitializer(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting DataInitializer seeding...");

        // 1. STRICT TENANT ISOLATION: Initialize exclusively for Finance domain
        TenantContext.setTenantId("finance");

        try {
            if (roleRepository.findByName(ERole.ROLE_ADMIN).isEmpty()) {
                roleRepository.save(new Role(ERole.ROLE_ADMIN));
            }
            if (roleRepository.findByName(ERole.ROLE_USER).isEmpty()) {
                roleRepository.save(new Role(ERole.ROLE_USER));
            }

            if (userRepository.findByEmail(adminEmail).isEmpty()) {
                User adminUser =
                        new User(adminUsername, adminEmail, passwordEncoder.encode(adminPassword));

                Set<Role> roles = new HashSet<>();
                Role adminRole =
                        roleRepository
                                .findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(
                                        () ->
                                                new RuntimeException(
                                                        "Error: Admin role is not found."));
                roles.add(adminRole);

                adminUser.setRoles(roles);
                userRepository.save(adminUser);
                System.out.println("Admin user created successfully with ADMIN role!");
            }
        } catch (Exception e) {
            System.err.println("DataInitializer seeding failed: " + e.getMessage());
            // We catch the exception so that it does NOT bubble up to SpringApplication.run()
            // preventing the JVM from shutting down gracefully immediately after startup.
        } finally {
            TenantContext.clear(); // Prevent thread contamination
        }
    }
}
