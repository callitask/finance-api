package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.KeycloakRealmRoleConverter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
import java.util.Arrays;
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
                        "/actuator/**", "/api/v1/health/**", "/api/v1/monitoring/ingest")
                    .permitAll()

                    // 2. Static Assets & SEO (Public)
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/uploads/**",
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

                    // --- FIX START: Auth Endpoints MUST be Authenticated ---
                    // Explicitly require login for user profile data and updates.
                    .requestMatchers("/api/v1/auth/**")
                    .authenticated()
                    // --- FIX END ---

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
    configuration.setAllowedOrigins(allowedOrigins);
    // Explicitly allowing PUT for profile updates
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
