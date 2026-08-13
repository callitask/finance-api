/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Exposes REST endpoints for client-side consumption of UI preferences.
 *
 * <p>Scope: - API routing layer strictly for the user preferences domain.
 *
 * <p>Critical Dependencies: - Security: Protected by AegisMainFilter and Spring Security.
 *
 * <p>Security Constraints: - Must be annotated with @PreAuthorize("isAuthenticated()") to prevent
 * anonymous access.
 *
 * <p>Non-Negotiables: - None.
 *
 * <p>Change Intent: - Provide secure access points for the frontend Cloudflare Radar toolbar
 * configuration.
 *
 * <p>Future AI Guidance: - None.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created UserPreferencesController. •
 * Date/Phase: Phase 1 (Feature Packaging)
 */
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
