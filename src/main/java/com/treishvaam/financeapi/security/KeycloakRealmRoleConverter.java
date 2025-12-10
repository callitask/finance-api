package com.treishvaam.financeapi.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts Keycloak Realm Roles (e.g., "admin", "editor") into Spring Security Authorities (e.g.,
 * "ROLE_ADMIN", "ROLE_EDITOR").
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  @Override
  @SuppressWarnings("unchecked")
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");

    if (realmAccess == null || realmAccess.isEmpty()) {
      return Collections.emptyList();
    }

    Collection<String> roles = (Collection<String>) realmAccess.get("roles");
    if (roles == null || roles.isEmpty()) {
      return Collections.emptyList();
    }

    return roles.stream()
        .map(roleName -> "ROLE_" + roleName.toUpperCase()) // Map "admin" -> "ROLE_ADMIN"
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
  }
}
