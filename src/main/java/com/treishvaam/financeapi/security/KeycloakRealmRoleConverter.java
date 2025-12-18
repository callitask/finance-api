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
 * Robust Converter for Keycloak Roles. Checks 'realm_access' and 'resource_access' and Logs found
 * roles for debugging. // Spotless check fix
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final Logger logger = LoggerFactory.getLogger(KeycloakRealmRoleConverter.class);

  @Override
  @SuppressWarnings("unchecked")
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    List<String> combinedRoles = new ArrayList<>();

    // 1. Check Realm Access (Standard)
    Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");
    if (realmAccess != null && !realmAccess.isEmpty()) {
      Collection<String> realmRoles = (Collection<String>) realmAccess.get("roles");
      if (realmRoles != null) {
        combinedRoles.addAll(realmRoles);
      }
    }

    // 2. Check Resource Access (Client Roles - Fallback)
    Map<String, Object> resourceAccess =
        (Map<String, Object>) jwt.getClaims().get("resource_access");
    if (resourceAccess != null && !resourceAccess.isEmpty()) {
      // Iterate through all clients and grab their roles
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

    // 3. Debug Logging (Visible in Loki/Console)
    if (combinedRoles.isEmpty()) {
      logger.warn("Security Warning: No roles found in JWT for user: {}", jwt.getSubject());
      logger.debug("JWT Claims: {}", jwt.getClaims());
    } else {
      logger.info("User {} granted roles: {}", jwt.getSubject(), combinedRoles);
    }

    // 4. Convert to Spring Authorities (Always Uppercase + ROLE_ Prefix)
    return combinedRoles.stream()
        .map(roleName -> "ROLE_" + roleName.toUpperCase()) // e.g. "admin" -> "ROLE_ADMIN"
        .distinct()
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
  }
}
