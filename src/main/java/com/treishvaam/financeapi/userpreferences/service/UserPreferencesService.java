/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Contract for UserPreferences operations.
 *
 * <p>Scope: - Defines required business logic for user preference manipulation.
 *
 * <p>Critical Dependencies: - None.
 *
 * <p>Security Constraints: - None.
 *
 * <p>Non-Negotiables: - None.
 *
 * <p>Change Intent: - Interface abstraction for dependency injection.
 *
 * <p>Future AI Guidance: - None.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created UserPreferencesService. •
 * Date/Phase: Phase 1 (Feature Packaging)
 */
package com.treishvaam.financeapi.userpreferences.service;

import com.treishvaam.financeapi.userpreferences.model.UserPreferences;

public interface UserPreferencesService {
    UserPreferences getPreferencesForCurrentUser();

    UserPreferences updatePreferences(UserPreferences preferences);
}
