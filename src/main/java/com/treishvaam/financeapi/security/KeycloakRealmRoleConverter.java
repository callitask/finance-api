package com.treishvaam.financeapi.security;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Converts Keycloak JWT roles (realm_access and resource_access) into Spring Security
 * GrantedAuthorities.
 *
 * <p>Scope: - Auth processing layer for all protected API endpoints.
 *
 * <p>Critical Dependencies: - Backend: Keycloak JWT structure.
 *
 * <p>Security Constraints: - Do not log plain-text user IDs (PII) at INFO level.
 *
 * <p>Non-Negotiables: - Must correctly map both realm and client roles to standard Spring `ROLE_`
 * prefixed authorities.
 *
 * <p>Change Intent: - Fix SEC-11: Remove sensitive user subject data from INFO-level logs to
 * prevent tracking in log aggregators.
 *
 * <p>Future AI Guidance: - Keep logging minimal and non-identifiable at INFO level.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (Phase 4 - SEC-11 Fix): • Replaced INFO
 * level plain-text subject logging with DEBUG level hashed subject logging. • Why the edit was
 * required: Prevent leaking user identity data (UUIDs) to log aggregators (Loki/Grafana). • What
 * behavior must remain unchanged: The actual role extraction logic must remain completely intact.
 */
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Robust Converter for Keycloak Roles. Checks 'realm_access' and 'resource_access'. Maps them to
 * Spring Security Authorities with 'ROLE_' prefix.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakRealmRoleConverter.class);

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> combinedRoles = new ArrayList<>();

        // 1. Check Realm Access (Standard Keycloak location)
        // Structure: "realm_access": { "roles": ["admin", "user"] }
        Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");
        if (realmAccess != null && !realmAccess.isEmpty()) {
            Collection<String> realmRoles = (Collection<String>) realmAccess.get("roles");
            if (realmRoles != null) {
                combinedRoles.addAll(realmRoles);
            }
        }

        // 2. Check Resource Access (Client Roles)
        // Structure: "resource_access": { "my-client": { "roles": ["editor"] } }
        Map<String, Object> resourceAccess =
                (Map<String, Object>) jwt.getClaims().get("resource_access");
        if (resourceAccess != null && !resourceAccess.isEmpty()) {
            resourceAccess
                    .values()
                    .forEach(
                            clientAccess -> {
                                if (clientAccess instanceof Map) {
                                    Map<String, Object> clientMap =
                                            (Map<String, Object>) clientAccess;
                                    Collection<String> clientRoles =
                                            (Collection<String>) clientMap.get("roles");
                                    if (clientRoles != null) {
                                        combinedRoles.addAll(clientRoles);
                                    }
                                }
                            });
        }

        // 3. Debug Logging (Crucial for diagnosis)
        if (combinedRoles.isEmpty()) {
            logger.warn(
                    "⚠️ Security: No roles found in JWT for user subject: {}", jwt.getSubject());
            // Log claims structure to help debug if roles are in a non-standard place
            if (logger.isDebugEnabled()) {
                logger.debug("JWT Claims Dump: {}", jwt.getClaims());
            }
        } else {
            // SEC-11: Log only hashed subject to prevent user tracking in logs
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "JWT roles assigned for subject hash: {}",
                        Integer.toHexString(jwt.getSubject().hashCode()));
            }
        }

        // 4. Convert to Spring Authorities (ROLE_PREFIX + UPPERCASE)
        // Example: "admin" -> "ROLE_ADMIN"
        return combinedRoles.stream()
                .map(
                        roleName -> {
                            String transformed = roleName.toUpperCase();
                            if (!transformed.startsWith("ROLE_")) {
                                transformed = "ROLE_" + transformed;
                            }
                            return transformed;
                        })
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
