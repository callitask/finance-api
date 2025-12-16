package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.KeycloakRealmRoleConverter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final RateLimitingFilter rateLimitingFilter;
  private final InternalSecretFilter internalSecretFilter;

  public SecurityConfig(
      RateLimitingFilter rateLimitingFilter, InternalSecretFilter internalSecretFilter) {
    this.rateLimitingFilter = rateLimitingFilter;
    this.internalSecretFilter = internalSecretFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // 1. CORS Configuration (Must be first to handle pre-flight checks)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        // 2. Disable CSRF (Stateless API)
        .csrf(csrf -> csrf.disable())

        // 3. Session Management (Stateless)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 4. Security Headers (CRITICAL FIX for Keycloak Silent SSO & Iframes)
        .headers(
            headers ->
                headers.frameOptions(
                    frame ->
                        frame.disable()) // Allows browser to iframe the backend for auth checks
            )

        // 5. URL Authorization Rules
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public Endpoints
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/posts/**",
                        "/api/v1/categories/**",
                        "/api/v1/market/**",
                        "/api/v1/contact/**",
                        "/api/v1/files/**",
                        "/api/v1/health/**",
                        "/api/v1/search/**",
                        "/actuator/**",
                        "/api/v1/monitoring/ingest", // Allow Faro RUM
                        "/sitemap.xml",
                        "/favicon.ico")
                    .permitAll()

                    // Admin Actions
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("admin")

                    // Secure everything else
                    .anyRequest()
                    .authenticated())

        // 6. OAuth2 Resource Server (Keycloak)
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))

        // 7. Custom Filters
        .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(internalSecretFilter, RateLimitingFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // Explicitly allow Frontend, Localhost, and Backend (Self)
    configuration.setAllowedOrigins(
        Arrays.asList(
            "https://treishfin.treishvaamgroup.com",
            "http://localhost:3000",
            "https://backend.treishvaamgroup.com"));

    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    // Allow all headers to prevent "Request header field X is not allowed" errors
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return jwtAuthenticationConverter;
  }
}
