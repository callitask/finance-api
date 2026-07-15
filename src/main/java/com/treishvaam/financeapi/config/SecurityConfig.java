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
 * <p>- EDITED (Phase 6.3 - Cryptographic Origin Enforcement & GEO): • Registered
 * `AegisEdgeValidationFilter` as a `FilterRegistrationBean` with precedence HIGHER than CORS. •
 * Added `/api/public/geo/**`, `/llms.txt`, `/ai-feed.md` to public endpoints for Generative Engine
 * Optimization. • Why: Strictly prevents direct-IP scanner bypasses; drops traffic without a
 * Cloudflare Edge signature. *
 *
 * <p>- EDITED (Security Filter Chain Boot Crash Fix): • Reversed the declaration order of
 * `.addFilterBefore()` custom injections. • Why: Fixed `IllegalArgumentException: The Filter class
 * AegisMainFilter does not have a registered order`. Spring Security evaluates top-to-bottom and
 * requires the target class to already exist in the registry. Chaining them bottom-up against
 * `UsernamePasswordAuthenticationFilter` correctly maps the dependency tree.
 *
 * <p>- EDITED: • Expanded all public `.requestMatchers()` that use wildcards (`/**`) to explicitly
 * declare their exact root paths (e.g., `/api/v1/posts`, `/api/v1/categories`). • Why: Spring Boot
 * 3 / Spring Security 6 strict path matching natively rejects trailing-slash mismatches. The
 * Next.js SSR fetch cycle was requesting exact root paths, triggering 401 Unauthorized errors which
 * cascaded into 500 Internal Server Errors on the frontend.
 *
 * <p>- EDITED (Stop Spring Boot Error Masking): • Added `"/error"` to `permitAll()` paths. • Why:
 * Spring Boot intercepts 403/429 errors from AEGIS and forwards them to /error. Without
 * permitAll(), Spring Security intercepts this forward and rewrites the status to 401 Unauthorized,
 * masking the true origin and crashing the Next.js SSR engine which expects the actual raw status
 * code.
 *
 * <p>- EDITED (Zero-Trust IP Normalization & Integer Underflow Fix): • Replaced the underflowing
 * `Ordered.HIGHEST_PRECEDENCE - 10` filter registration with a strict mathematical sequence
 * explicitly injecting `AegisIpResolutionFilter`. • Sequence: EdgeValidation (Highest) ->
 * IpResolution (+1) -> CorsFilter (+2). • Why: The underflow pushed cryptographic validation to the
 * very end of the chain, breaking the Zero-Trust perimeter. The exact sequence prevents 127.0.0.1
 * spoofing bypasses while correctly feeding downstream rate-limiters the true user IP instead of
 * the Cloudflare node.
 *
 * <p>- EDITED (Analytics 401 Stabilization): • Appended explicit `/api/v1/analytics/event` to the
 * POST permitAll array. • Why: Defends against Spring Boot 3 strict path-matching anomalies where
 * the wildcard `/**` occasionally fails to map exact leaf endpoints for anonymous POST bodies,
 * resolving the 401 Unauthorized telemetry lockout.
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
import com.treishvaam.financeapi.security.aegis.AegisEdgeValidationFilter;
import com.treishvaam.financeapi.security.aegis.AegisIpResolutionFilter;
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
    private final AegisEdgeValidationFilter aegisEdgeValidationFilter;
    private final AegisIpResolutionFilter aegisIpResolutionFilter;

    @Value("#{'${cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    public SecurityConfig(
            RateLimitingFilter rateLimitingFilter,
            InternalSecretFilter internalSecretFilter,
            InputSanitizationFilter inputSanitizationFilter,
            AegisMainFilter aegisMainFilter,
            AegisZkpAdminFilter aegisZkpAdminFilter,
            AegisDeceptionFilter aegisDeceptionFilter,
            AegisEdgeValidationFilter aegisEdgeValidationFilter,
            AegisIpResolutionFilter aegisIpResolutionFilter) {
        this.rateLimitingFilter = rateLimitingFilter;
        this.internalSecretFilter = internalSecretFilter;
        this.inputSanitizationFilter = inputSanitizationFilter;
        this.aegisMainFilter = aegisMainFilter;
        this.aegisZkpAdminFilter = aegisZkpAdminFilter;
        this.aegisDeceptionFilter = aegisDeceptionFilter;
        this.aegisEdgeValidationFilter = aegisEdgeValidationFilter;
        this.aegisIpResolutionFilter = aegisIpResolutionFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // LAYER 1: STRICT CRYPTOGRAPHIC ORIGIN VERIFICATION (Absolute First)
    @Bean
    public FilterRegistrationBean<AegisEdgeValidationFilter>
            aegisEdgeValidationFilterRegistration() {
        FilterRegistrationBean<AegisEdgeValidationFilter> bean =
                new FilterRegistrationBean<>(aegisEdgeValidationFilter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE); // Mathematical baseline. NO UNDERFLOW.
        return bean;
    }

    // LAYER 2: GLOBAL IP NORMALIZATION (Executes ONLY after Cryptographic Proof is accepted)
    @Bean
    public FilterRegistrationBean<AegisIpResolutionFilter> aegisIpResolutionFilterRegistration() {
        FilterRegistrationBean<AegisIpResolutionFilter> bean =
                new FilterRegistrationBean<>(aegisIpResolutionFilter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1); // Ensures downstream sees the true client IP
        return bean;
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
                                                "/api/v1/health",
                                                "/api/v1/health/**",
                                                "/api/v1/monitoring/ingest",
                                                "/error") // Allow Spring Boot to forward internal
                                        // error pages without overwriting status
                                        // codes
                                        .permitAll()

                                        // 1.5 Actuator Catch-all (Secure)
                                        .requestMatchers("/actuator/**")
                                        .hasAuthority("ROLE_ADMIN")

                                        // 2. Static Assets, SEO & GEO (Public)
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/uploads",
                                                "/api/v1/uploads/**",
                                                "/sitemap.xml",
                                                "/sitemap-news.xml",
                                                "/feed.xml",
                                                "/sitemaps",
                                                "/sitemaps/**",
                                                "/favicon.ico",
                                                "/llms.txt",
                                                "/ai-feed.md",
                                                "/api/public",
                                                "/api/public/**") // Includes GEO endpoints
                                        .permitAll()

                                        // 3. Public API Read Access
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/posts",
                                                "/api/v1/posts/**",
                                                "/api/v1/categories",
                                                "/api/v1/categories/**",
                                                "/api/v1/market",
                                                "/api/v1/market/**",
                                                "/api/v1/news",
                                                "/api/v1/news/**",
                                                "/api/v1/search",
                                                "/api/v1/search/**",
                                                "/api/v1/logo")
                                        .permitAll()

                                        // 4. Market Quotes Batch (POST allowed publicly)
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/v1/market/quotes/batch")
                                        .permitAll()

                                        // 5. Contact Form (Public Write)
                                        .requestMatchers("/api/v1/contact", "/api/v1/contact/**")
                                        .permitAll()

                                        // PHASE 5: Allow Public Analytics & AEGIS Telemetry Beacons
                                        // (POST ONLY)
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/analytics",
                                                "/api/v1/analytics/event", // EXPLICIT ADDITION
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
                                        .requestMatchers("/api/v1/analytics/**") // Dashboard Reads
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
                // FIXED: Bottom-up registration anchoring against
                // UsernamePasswordAuthenticationFilter
                .addFilterBefore(
                        aegisZkpAdminFilter, UsernamePasswordAuthenticationFilter.class) // L3-ZKA
                .addFilterBefore(inputSanitizationFilter, AegisZkpAdminFilter.class)
                .addFilterBefore(rateLimitingFilter, InputSanitizationFilter.class)
                .addFilterBefore(internalSecretFilter, RateLimitingFilter.class)
                .addFilterBefore(
                        aegisMainFilter, InternalSecretFilter.class) // INJECTED AEGIS MASTER FILTER
                .addFilterBefore(
                        aegisDeceptionFilter,
                        AegisMainFilter.class); // L4-ADA DECEPTION INTERCEPTOR

        return http.build();
    }

    // LAYER 3: PREFLIGHT HANDLING
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

        // SEC-07 & Phase 6: Explicitly limit headers and allow raw telemetry header + signature
        // headers
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
                        "X-Aegis-Biometric-Raw",
                        "X-Aegis-Edge-Signature",
                        "X-Aegis-Edge-Timestamp",
                        "X-Aegis-Client-IP")); // Explicitly allow the new custom IP injection
        // header

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
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2); // Correctly offset behind IP normalizer
        return bean;
    }

    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
                new KeycloakRealmRoleConverter());
        return jwtAuthenticationConverter;
    }
}
