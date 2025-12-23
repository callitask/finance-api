package com.treishvaam.financeapi.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  @GetMapping("/me")
  public ResponseEntity<?> getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
      return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
    }

    Jwt jwt = (Jwt) authentication.getPrincipal();
    String username = jwt.getClaimAsString("preferred_username");
    String email = jwt.getClaimAsString("email");

    // In Keycloak, roles are in realm_access, handled by the converter in SecurityConfig
    // We return basic profile info here if the frontend needs it beyond what's in the ID token
    return ResponseEntity.ok(Map.of("username", username, "email", email));
  }
}
