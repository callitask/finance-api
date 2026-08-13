/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Implementation of business logic for user preferences.
 *
 * <p>Scope: - Orchestrates reads and updates of UI settings while extracting Keycloak UUIDs safely.
 *
 * <p>Critical Dependencies: - Context: Relies on Spring SecurityContextHolder for the user subject.
 * - Context: Relies on TenantContext for multi-tenant isolation.
 *
 * <p>Security Constraints: - Must never accept a userSub parameter directly from a controller
 * payload. The subject MUST be extracted securely from the active JWT context.
 *
 * <p>Non-Negotiables: - None.
 *
 * <p>Change Intent: - Execute the retrieval and updating of database settings.
 *
 * <p>Future AI Guidance: - None.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created UserPreferencesServiceImpl. •
 * Date/Phase: Phase 1 (Feature Packaging)
 */
package com.treishvaam.financeapi.userpreferences.service;

import com.treishvaam.financeapi.config.tenant.TenantContext;
import com.treishvaam.financeapi.userpreferences.model.UserPreferences;
import com.treishvaam.financeapi.userpreferences.repository.UserPreferencesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferencesServiceImpl implements UserPreferencesService {

    @Autowired private UserPreferencesRepository preferencesRepository;

    @Override
    public UserPreferences getPreferencesForCurrentUser() {
        String userSub = SecurityContextHolder.getContext().getAuthentication().getName();
        String tenantId = TenantContext.getTenantId();

        return preferencesRepository
                .findByUserSubAndTenantId(userSub, tenantId)
                .orElseGet(
                        () -> {
                            UserPreferences prefs = new UserPreferences();
                            prefs.setUserSub(userSub);
                            prefs.setTenantId(tenantId);
                            return preferencesRepository.save(prefs);
                        });
    }

    @Override
    @Transactional
    public UserPreferences updatePreferences(UserPreferences updated) {
        UserPreferences existing = getPreferencesForCurrentUser();
        existing.setRadarToolbarEnabled(updated.isRadarToolbarEnabled());
        existing.setDefaultHighlightColor(updated.getDefaultHighlightColor());
        return preferencesRepository.save(existing);
    }
}
