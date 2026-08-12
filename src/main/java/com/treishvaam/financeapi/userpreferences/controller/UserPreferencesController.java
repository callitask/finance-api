package com.treishvaam.financeapi.userpreferences.controller;

import com.treishvaam.financeapi.userpreferences.model.UserPreferences;
import com.treishvaam.financeapi.userpreferences.service.UserPreferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user-preferences")
@PreAuthorize("isAuthenticated()")
public class UserPreferencesController {

    @Autowired private UserPreferencesService userPreferencesService;

    @GetMapping
    public ResponseEntity<UserPreferences> getPreferences() {
        return ResponseEntity.ok(userPreferencesService.getPreferencesForCurrentUser());
    }

    @PutMapping
    public ResponseEntity<UserPreferences> updatePreferences(
            @RequestBody UserPreferences preferences) {
        return ResponseEntity.ok(userPreferencesService.updatePreferences(preferences));
    }
}
