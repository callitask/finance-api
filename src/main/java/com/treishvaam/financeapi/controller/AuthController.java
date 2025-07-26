package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.dto.AuthResponse;
import com.treishvaam.financeapi.dto.LoginRequest;
import com.treishvaam.financeapi.model.User;
import com.treishvaam.financeapi.repository.UserRepository;
import com.treishvaam.financeapi.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.createToken(authentication);

        // Fetch the full user entity to check for the LinkedIn token
        User user = userRepository.findByEmail(loginRequest.getEmail()).orElseThrow(() -> new RuntimeException("User not found"));
        boolean isLinkedinConnected = user.getLinkedinAccessToken() != null &&
                                      (user.getLinkedinTokenExpiry() == null || user.getLinkedinTokenExpiry().isAfter(Instant.now()));

        // You may need to fetch roles and username from your user details implementation
        List<String> roles = user.getRoles().stream().map(role -> role.getName().name()).toList();
        String username = user.getEmail();

        return ResponseEntity.ok(new AuthResponse(jwt,
                user.getId(),
                username,
                user.getEmail(),
                roles,
                isLinkedinConnected));
    }
}