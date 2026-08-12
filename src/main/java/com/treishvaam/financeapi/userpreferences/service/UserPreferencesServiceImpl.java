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
