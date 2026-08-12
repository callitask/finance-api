package com.treishvaam.financeapi.userpreferences.service;

import com.treishvaam.financeapi.userpreferences.model.UserPreferences;

public interface UserPreferencesService {
    UserPreferences getPreferencesForCurrentUser();

    UserPreferences updatePreferences(UserPreferences preferences);
}
