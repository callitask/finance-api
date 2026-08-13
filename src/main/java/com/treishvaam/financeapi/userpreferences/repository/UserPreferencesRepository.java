/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Data Access Object for UserPreferences.
 *
 * <p>Scope: - Maps user UX settings (toolbar, theme) to the database.
 *
 * <p>Critical Dependencies: - Database: MariaDB (user_preferences table).
 *
 * <p>Security Constraints: - Must strictly enforce tenantId to prevent cross-tenant preference
 * leakage.
 *
 * <p>Non-Negotiables: - All lookups must composite key on userSub AND tenantId.
 *
 * <p>Change Intent: - Initialized as part of the Enterprise Feature Packaging mandate.
 *
 * <p>Future AI Guidance: - None.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created UserPreferencesRepository. •
 * Date/Phase: Phase 1 (Feature Packaging)
 */
package com.treishvaam.financeapi.userpreferences.repository;

import com.treishvaam.financeapi.userpreferences.model.UserPreferences;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    Optional<UserPreferences> findByUserSubAndTenantId(String userSub, String tenantId);
}
