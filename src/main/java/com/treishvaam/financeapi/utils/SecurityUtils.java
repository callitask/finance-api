package com.treishvaam.financeapi.util;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Extracts human-readable username or display name from JwtAuthenticationToken
 * claims.
 *
 * <p>Scope: - Replaces Keycloak Subject UUID (sub claim) with preferred_username or name for
 * display fields.
 *
 * <p>Critical Dependencies: - Backend: Spring Security OAuth2
 *
 * <p>Security Constraints: - Fails safely to the default principal name if claims are unavailable.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created utility to resolve Keycloak UUID
 * bug where author names were stored as UUIDs. • Date/Phase: Phase 1 (Author UUID Resolution)
 */
public class SecurityUtils {

    public static String extractAuthorDisplayName(Authentication authentication) {
        if (authentication == null) {
            return "Anonymous";
        }

        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Map<String, Object> attributes = jwtAuth.getTokenAttributes();
            if (attributes.get("preferred_username") != null) {
                return (String) attributes.get("preferred_username");
            } else if (attributes.get("name") != null) {
                return (String) attributes.get("name");
            }
        }

        return authentication.getName();
    }
}
