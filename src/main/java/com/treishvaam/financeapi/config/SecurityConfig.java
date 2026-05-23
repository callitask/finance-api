/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Spring Security configuration for REST API protection, CORS, and OAuth2 resource
 * server settings.
 *
 * <p>Scope: - Defines public vs authenticated routing rules and sets up the security filter chain.
 *
 * <p>Critical Dependencies: - Backend: Intercepts all incoming API requests. - Frontend: Validates
 * JWT tokens from Keycloak.
 *
 * <p>Security Constraints: - Actuator endpoints (except health) must be restricted to ADMIN. - Do
 * not disable CSRF unless stateless. - Do not open CORS to `*` without evaluating risk.
 *
 * <p>Non-Negotiables: - Must strictly enforce Zero-Trust access rules for all internal and
 * administrative routes.
 *
 * <p>Change Intent: - Resolved CORS preflight failure for POST requests. Explicitly elevated
 * CorsFilter to Ordered.HIGHEST_PRECEDENCE so OPTIONS requests bypass internal security filters.
 *
 * <p>Future AI Guidance: - Always ensure public endpoints are explicitly defined and justified. -
 * Do NOT remove the FilterRegistrationBean<CorsFilter> configuration; it is required for proper
 * preflight handling.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Changed `/actuator/**` permitAll to
 * `/actuator/health` permitAll. - EDITED (Phase 2 Bug Fix): • Extracted CORS logic into a globally
 * registered `FilterRegistrationBean<CorsFilter>` with `Ordered.HIGHEST_PRECEDENCE`. Disabled
 * Spring Security's native `.cors()` to let the global filter handle it at the very start of the
 * Servlet chain. - EDITED (2026-05-15 BUG-FINANCE-02 Fix B): • Added explicit requestMatchers for
 * PUT /api/v1/posts/draft/** and POST /api/v1/posts/draft with .authenticated() — makes intent
 * explicit and prevents rule ordering ambiguity. • Added explicit requestMatchers for PUT
 * /api/v1/posts/** with authenticated() for edit flow. • Added DELETE /api/v1/posts/** with
 * hasAnyAuthority for post deletion. • Why: The previous config relied on
 * .anyRequest().authenticated() fallback for draft operations. While this SHOULD work, explicit
 * rules prevent future rule ordering bugs and make the security intent clear. Combined with the
 * nginx ModSecurity fix, this resolves the 403/CORS error on draft save, post edit, and publish
 * operations. * - EDITED (Phase 4 - SEC-07 Fix): • Replaced
 * `configuration.setAllowedHeaders(Arrays.asList("*"));` with explicit allowed headers. • Why the
 * edit was required: Prevent attackers from setting custom injection headers that might be picked
 * up by middleware. • What behavior must remain unchanged: CORS preflight must still function
 * correctly for legitimate requests. * - EDITED (Phase 4 - SEC-10 Fix): • Added
 * `InputSanitizationFilter` to the security filter chain to intercept queries and validate against
 * SQLi. • Why the edit was required: Application-layer defense-in-depth against SQL/NoSQL injection
 * via Redis. *
 *
 * <p>- EDITED (Phase 5 - First-Party Analytics): • Added explicit requestMatchers for
 * HttpMethod.POST to `/api/v1/analytics/**` with `.permitAll()`. • Retained
 * `.hasAnyAuthority("ROLE_ANALYST", "ROLE_ADMIN")` for all other methods on `/api/v1/analytics/**`.
 * • Why: Allows the frontend Faro/Tracking script to submit event beacons (POST) anonymously, while
 * keeping the analytics dashboard data reads (GET) strictly secured for administrators. *
 *
 * <p>- EDITED (Phase 5 - AEGIS Filter Injection): • Injected `AegisMainFilter` at the top of the
 * Spring Security chain. • Why: Ensures that Temporal Path mutations and Byzantine Consensus
 * evaluate BEFORE classical rate limiting and authentication flows.
 *
 * <p>- EDITED (Phase 5 - ZKP Admin Enforcement): • Injected `AegisZkpAdminFilter` into the Spring
 * Security chain. • Why: Enforces Zero-Knowledge Proof (L3-ZKA) authentication on all
 * `/api/v1/admin/**` endpoints, intercepting traffic immediately before the
 * UsernamePasswordAuthenticationFilter. * - EDITED (Phase 5.2 - AEGIS Filter Wiring Finalization):
 * • Injected `AegisDeceptionFilter` directly into the Spring Security chain before
 * `AegisMainFilter`. • Added `/api/v1/aegis/telemetry` to public POST endpoints to accept anonymous
 * frontend biometric hashes. • Why: Completes the AEGIS runtime execution hierarchy and enables the
 * L5-BIE telemetry pipeline.
 *
 * <p>- EDITED (Phase 6 - Dynamic Privacy Compliance Sync): • Whitelisted `X-Aegis-Biometric-Raw` in
 * the global CorsFilter configuration. • Why: Ensures preflight checks pass when the frontend
 * transmits complete biometric telemetry (jurisdiction allowing).
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InputSanitizationFilter;
import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.KeycloakRealmRoleConverter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
import com.treishvaam.financeapi.security.aegis.AegisDeceptionFilter;
import com.treishvaam.financeapi.security.aegis.AegisMainFilter;
import com.treishvaam.financeapi.security.aegis.AegisZkpAdminFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
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
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Allows @PreAuthorize to work
public class SecurityConfig {

    private final RateLimitingFilter rateLimitingFilter;
    private final InternalSecretFilter internalSecretFilter;
    private final InputSanitizationFilter inputSanitizationFilter;
    private final AegisMainFilter aegisMainFilter;
    private final AegisZkpAdminFilter aegisZkpAdminFilter;
    private final AegisDeceptionFilter aegisDeceptionFilter;

    @Value("#{'${cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    public SecurityConfig(
            RateLimitingFilter rateLimitingFilter,
            InternalSecretFilter internalSecretFilter,
            InputSanitizationFilter inputSanitizationFilter,
            AegisMainFilter aegisMainFilter,
            AegisZkpAdminFilter aegisZkpAdminFilter,
            AegisDeceptionFilter aegisDeceptionFilter) {
        this.rateLimitingFilter = rateLimitingFilter;
        this.internalSecretFilter = internalSecretFilter;
        this.inputSanitizationFilter = inputSanitizationFilter;
        this.aegisMainFilter = aegisMainFilter;
        this.aegisZkpAdminFilter = aegisZkpAdminFilter;
        this.aegisDeceptionFilter = aegisDeceptionFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.disable()) // CORS is handled globally by
                // FilterRegistrationBean below
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
                                                "/actuator/health",
                                                "/api/v1/health/**",
                                                "/api/v1/monitoring/ingest")
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
                                                // --- FIX: Allow Cloudflare Worker to fetch Sitemap
                                                // Metadata (Public) ---
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
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/v1/market/quotes/batch")
                                        .permitAll()

                                        // 5. Contact Form (Public Write)
                                        .requestMatchers("/api/v1/contact/**")
                                        .permitAll()

                                        // PHASE 5: Allow Public Analytics & AEGIS Telemetry Beacons
                                        // (POST ONLY)
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/analytics/**",
                                                "/api/v1/aegis/telemetry")
                                        .permitAll()

                                        // --- Draft Operations (Authenticated — any logged-in user)
                                        // ---
                                        .requestMatchers(HttpMethod.POST, "/api/v1/posts/draft")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.PUT, "/api/v1/posts/draft/**")
                                        .authenticated()

                                        // --- Auth Endpoints MUST be Authenticated ---
                                        .requestMatchers("/api/v1/auth/**")
                                        .authenticated()

                                        // 6. Secure Admin/Dashboard Routes
                                        .requestMatchers(
                                                "/api/v1/analytics/**") // Applies to GET requests
                                        // (Dashboard Reads)
                                        .hasAnyAuthority("ROLE_ANALYST", "ROLE_ADMIN")
                                        .requestMatchers("/api/v1/posts/admin/**")
                                        .hasAnyAuthority(
                                                "ROLE_EDITOR", "ROLE_PUBLISHER", "ROLE_ADMIN")

                                        // --- Post Publish & Edit (requires PUBLISHER or ADMIN) ---
                                        .requestMatchers(HttpMethod.POST, "/api/v1/posts")
                                        .hasAnyAuthority("ROLE_PUBLISHER", "ROLE_ADMIN")
                                        .requestMatchers(HttpMethod.PUT, "/api/v1/posts/**")
                                        .hasAnyAuthority(
                                                "ROLE_EDITOR", "ROLE_PUBLISHER", "ROLE_ADMIN")
                                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/bulk")
                                        .hasAnyAuthority(
                                                "ROLE_EDITOR", "ROLE_PUBLISHER", "ROLE_ADMIN")
                                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/**")
                                        .hasAnyAuthority("ROLE_PUBLISHER", "ROLE_ADMIN")

                                        // --- File Upload (requires PUBLISHER or ADMIN) ---
                                        .requestMatchers("/api/v1/files/upload")
                                        .hasAnyAuthority("ROLE_PUBLISHER", "ROLE_ADMIN")
                                        .requestMatchers("/api/v1/admin/**", "/api/v1/status/**")
                                        .hasAuthority("ROLE_ADMIN")

                                        // 7. Fallback: Require authentication for anything else
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())))
                .addFilterBefore(
                        aegisDeceptionFilter, AegisMainFilter.class) // L4-ADA DECEPTION INTERCEPTOR
                .addFilterBefore(
                        aegisMainFilter, InternalSecretFilter.class) // INJECTED AEGIS MASTER FILTER
                .addFilterBefore(
                        aegisZkpAdminFilter, UsernamePasswordAuthenticationFilter.class) // L3-ZKA
                .addFilterBefore(inputSanitizationFilter, AegisZkpAdminFilter.class)
                .addFilterBefore(rateLimitingFilter, InputSanitizationFilter.class)
                .addFilterBefore(internalSecretFilter, RateLimitingFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> globalCorsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration configuration = new CorsConfiguration();

        // Use AllowedOriginPatterns for better matching (handles subdomains & protocols)
        if (allowedOrigins == null
                || allowedOrigins.isEmpty()
                || (allowedOrigins.size() == 1 && allowedOrigins.get(0).isEmpty())) {
            configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        } else {
            configuration.setAllowedOriginPatterns(allowedOrigins);
        }

        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // SEC-07 & Phase 6: Explicitly limit headers and allow raw telemetry header
        configuration.setAllowedHeaders(
                Arrays.asList(
                        "Authorization",
                        "Content-Type",
                        "X-Requested-With",
                        "Accept",
                        "Origin",
                        "Access-Control-Request-Method",
                        "Access-Control-Request-Headers",
                        "X-CSRF-Token",
                        "X-AEGIS-ZKP-Proof",
                        "X-AEGIS-Challenge-ID",
                        "X-Aegis-Biometric-Hash",
                        "X-Aegis-Biometric-Raw")); // Added Raw Telemetry for full data fidelity

        configuration.setExposedHeaders(
                Arrays.asList(
                        "Authorization",
                        "Content-Type",
                        "X-Requested-With",
                        "X-AEGIS-POW-Challenge"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> bean =
                new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
                new KeycloakRealmRoleConverter());
        return jwtAuthenticationConverter;
    }
}
