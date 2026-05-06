/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Spring Security configuration for REST API protection, CORS, and OAuth2 resource server settings.
 *
 * Scope:
 * - Defines public vs authenticated routing rules and sets up the security filter chain.
 *
 * Critical Dependencies:
 * - Backend: Intercepts all incoming API requests.
 * - Frontend: Validates JWT tokens from Keycloak.
 *
 * Security Constraints:
 * - Actuator endpoints (except health) must be restricted to ADMIN.
 * - Do not disable CSRF unless stateless.
 * - Do not open CORS to `*` without evaluating risk.
 *
 * Non-Negotiables:
 * - Must strictly enforce Zero-Trust access rules for all internal and administrative routes.
 *
 * Change Intent:
 * - Fix CVE-001: Restrict `/actuator/**` endpoints to `ROLE_ADMIN` to prevent stack fingerprinting, allowing only `/actuator/health` publicly.
 *
 * Future AI Guidance:
 * - Always ensure public endpoints are explicitly defined and justified.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - EDITED:
 * • Changed `/actuator/**` permitAll to `/actuator/health` permitAll and restricted `/actuator/**` to hasAuthority("ROLE_ADMIN").
 * • Why the edit was required: Fix CVE-001 to prevent unauthorized access to sensitive actuator management data.
 * • What behavior must remain unchanged: CORS and existing public API routing.
 *
 * - DO-NOT-DELETE RULE:
 * This IMMUTABLE CHANGE HISTORY section must never be deleted,
 * truncated, rewritten, or regenerated.
 * Future AI must append only.
 */
package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.KeycloakRealmRoleConverter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Allows @PreAuthorize to work
public class SecurityConfig {

  private final RateLimitingFilter rateLimitingFilter;
  private final InternalSecretFilter internalSecretFilter;

  @Value("#{'${cors.allowed-origins}'.split(',')}")
  private List<String> allowedOrigins;

  public SecurityConfig(
      RateLimitingFilter rateLimitingFilter, InternalSecretFilter internalSecretFilter) {
    this.rateLimitingFilter = rateLimitingFilter;
    this.internalSecretFilter = internalSecretFilter;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers -> headers.frameOptions(frame -> frame.disable()))
        .authorizeHttpRequests(
            auth ->
                auth
                    // 0. Pre-flight checks (CORS) - CRITICAL
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()

                    // 1. System, Health & Monitoring (Public)
                    .requestMatchers(
                        "/actuator/health", "/api/v1/health/**", "/api/v1/monitoring/ingest")
                    .permitAll()
                    
                    // 1.5 Actuator Catch-all (Secure)
                    .requestMatchers("/actuator/**")
                    .hasAuthority("ROLE_ADMIN")

                    // 2. Static Assets & SEO (Public)
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/uploads/**",
                        "/sitemap.xml",
                        "/sitemap-news.xml",
                        "/feed.xml",
                        "/sitemaps/**",
                        "/favicon.ico",
                        // --- FIX: Allow Cloudflare Worker to fetch Sitemap Metadata (Public) ---
                        "/api/public/**")
                    .permitAll()

                    // 3. Public API Read Access
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/posts/**",
                        "/api/v1/categories/**",
                        "/api/v1/market/**",
                        "/api/v1/news/**",
                        "/api/v1/search/**",
                        "/api/v1/logo")
                    .permitAll()

                    // 4. Market Quotes Batch (POST allowed publicly)
                    .requestMatchers(HttpMethod.POST, "/api/v1/market/quotes/batch")
                    .permitAll()

                    // 5. Contact Form (Public Write)
                    .requestMatchers("/api/v1/contact/**")
                    .permitAll()

                    // --- Auth Endpoints MUST be Authenticated ---
                    .requestMatchers("/api/v1/auth/**")
                    .authenticated()

                    // 6. Secure Admin/Dashboard Routes
                    .requestMatchers("/api/v1/analytics/**")
                    .hasAnyAuthority("ROLE_ANALYST", "ROLE_ADMIN")
                    .requestMatchers("/api/v1/posts/admin/**")
                    .hasAnyAuthority("ROLE_EDITOR", "ROLE_PUBLISHER", "ROLE_ADMIN")
                    .requestMatchers("/api/v1/files/upload")
                    .hasAnyAuthority("ROLE_PUBLISHER", "ROLE_ADMIN")
                    .requestMatchers("/api/v1/admin/**", "/api/v1/status/**")
                    .hasAuthority("ROLE_ADMIN")

                    // 7. Fallback: Require authentication for anything else
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(internalSecretFilter, RateLimitingFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // Use AllowedOriginPatterns for better matching (handles subdomains & protocols)
    // This allows exact matches AND wildcards, unlike setAllowedOrigins which is strict.
    if (allowedOrigins == null
        || allowedOrigins.isEmpty()
        || (allowedOrigins.size() == 1 && allowedOrigins.get(0).isEmpty())) {
      configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
    } else {
      configuration.setAllowedOriginPatterns(allowedOrigins);
    }

    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setExposedHeaders(
        Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
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