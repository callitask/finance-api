package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.KeycloakRealmRoleConverter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

  private final InternalSecretFilter internalSecretFilter;
  private final RateLimitingFilter rateLimitingFilter;

  public SecurityConfig(
      InternalSecretFilter internalSecretFilter, RateLimitingFilter rateLimitingFilter) {
    this.internalSecretFilter = internalSecretFilter;
    this.rateLimitingFilter = rateLimitingFilter;
  }

  // --- FIX: Restore PasswordEncoder for DataInitializer ---
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authz ->
                authz
                    // --- 1. Allow Prometheus Metrics & Health ---
                    .requestMatchers("/actuator/**", "/health")
                    .permitAll()

                    // --- 2. Public V1 API Endpoints ---
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/posts",
                        "/api/v1/posts/public/**",
                        "/api/v1/posts/url/**",
                        "/api/v1/categories",
                        "/api/v1/uploads/**",
                        "/api/v1/market/**",
                        "/api/v1/news/**",
                        "/api/v1/search/**",
                        "/sitemap.xml",
                        "/sitemap-news.xml",
                        "/feed.xml",
                        "/sitemaps/**",
                        "/favicon.ico")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/contact/**",
                        "/api/v1/market/quotes/batch",
                        "/api/v1/market/widget",
                        "/faro-collector/**", // Added Faro Collector endpoint
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/error")
                    .permitAll()

                    // --- 3. Protected Endpoints (RBAC) ---
                    .requestMatchers("/api/v1/analytics/**")
                    .hasAnyRole("ANALYST", "ADMIN")
                    .requestMatchers(
                        "/api/v1/posts/admin/publish/**",
                        "/api/v1/posts/admin/delete/**",
                        "/api/v1/market/admin/**",
                        "/api/v1/status/**",
                        "/api/v1/admin/actions/**",
                        "/api/v1/files/upload")
                    .hasAnyRole("PUBLISHER", "ADMIN")
                    .requestMatchers(
                        "/api/v1/posts/draft", "/api/v1/posts/draft/**", "/api/v1/posts/admin/**")
                    .hasAnyRole("EDITOR", "PUBLISHER", "ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

    http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
    http.addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return converter;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(
        List.of("https://treishfin.treishvaamgroup.com", "http://localhost:3000"));
    configuration.setAllowedMethods(
        List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setExposedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
