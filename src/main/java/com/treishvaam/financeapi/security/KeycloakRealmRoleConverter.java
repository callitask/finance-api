package com.treishvaam.financeapi.security;

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
                  Map<String, Object> clientMap = (Map<String, Object>) clientAccess;
                  Collection<String> clientRoles = (Collection<String>) clientMap.get("roles");
                  if (clientRoles != null) {
                    combinedRoles.addAll(clientRoles);
                  }
                }
              });
    }

    // 3. Debug Logging (Crucial for diagnosis)
    if (combinedRoles.isEmpty()) {
      logger.warn("⚠️ Security: No roles found in JWT for user subject: {}", jwt.getSubject());
      // Log claims structure to help debug if roles are in a non-standard place
      if (logger.isDebugEnabled()) {
        logger.debug("JWT Claims Dump: {}", jwt.getClaims());
      }
    } else {
      // Info level allows us to see this in production logs to verify permissions
      logger.info("🔐 User {} roles found: {}", jwt.getSubject(), combinedRoles);
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
