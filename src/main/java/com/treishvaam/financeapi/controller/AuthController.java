package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.User;
import com.treishvaam.financeapi.repository.UserRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  @Autowired private UserRepository userRepository;

  @GetMapping("/me")
  public ResponseEntity<?> getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
      return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
    }

    Jwt jwt = (Jwt) authentication.getPrincipal();
    String username = jwt.getClaimAsString("preferred_username");
    String email = jwt.getClaimAsString("email");

    // PHASE 1: Fetch real User entity to get Display Name
    User user = userRepository.findByEmail(email).orElse(null);
    String displayName = (user != null) ? user.getDisplayName() : null;

    return ResponseEntity.ok(
        Map.of(
            "username", username,
            "email", email,
            "displayName", displayName != null ? displayName : username));
  }

  // PHASE 1: New Endpoint to Edit Profile Name (Humanize Author)
  @PutMapping("/profile")
  public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
      return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
    }

    Jwt jwt = (Jwt) authentication.getPrincipal();
    String email = jwt.getClaimAsString("email");

    User user =
        userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

    if (request.containsKey("displayName")) {
      user.setDisplayName(request.get("displayName"));
      userRepository.save(user);
    }

    return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
  }
}
