package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.KeycloakRealmRoleConverter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
import java.util.Arrays;
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
@EnableMethodSecurity
public class SecurityConfig {

  private final RateLimitingFilter rateLimitingFilter;
  private final InternalSecretFilter internalSecretFilter;

  public SecurityConfig(
      RateLimitingFilter rateLimitingFilter, InternalSecretFilter internalSecretFilter) {
    this.rateLimitingFilter = rateLimitingFilter;
    this.internalSecretFilter = internalSecretFilter;
  }

  // --- FIX 1: Restore PasswordEncoder (Fixes Crash) ---
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
        .headers(headers -> headers.frameOptions(frame -> frame.disable())) // Keycloak iframe fix
        .authorizeHttpRequests(
            auth ->
                auth
                    // 1. System & Health
                    .requestMatchers(
                        "/actuator/**", "/api/v1/health/**", "/api/v1/monitoring/ingest")
                    .permitAll()

                    // 2. Static Assets & SEO (Restored from 5-day old config)
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/uploads/**", // Fixes Blog Images
                        "/sitemap.xml",
                        "/sitemap-news.xml",
                        "/feed.xml",
                        "/sitemaps/**",
                        "/favicon.ico")
                    .permitAll()

                    // 3. Public API Read Access
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/posts/**",
                        "/api/v1/categories/**",
                        "/api/v1/market/**",
                        "/api/v1/news/**", // Fixes News Widget
                        "/api/v1/search/**",
                        "/api/v1/logo")
                    .permitAll()

                    // 4. Auth & Public Write
                    .requestMatchers("/api/v1/auth/**", "/api/v1/contact/**")
                    .permitAll()

                    // 5. Protected Routes (RBAC)
                    // Fixes Dashboard Data (Analysts/Admins only)
                    .requestMatchers("/api/v1/analytics/**")
                    .hasAnyRole("analyst", "admin")

                    // Fixes Upload Security (Only Publishers/Admins can upload)
                    .requestMatchers("/api/v1/files/upload")
                    .hasAnyRole("publisher", "admin")

                    // Admin Actions
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("admin")
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
    // Gateway-Level CORS support (Nginx handles rejection, we just allow the flow)
    configuration.setAllowedOriginPatterns(Arrays.asList("*"));
    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
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
