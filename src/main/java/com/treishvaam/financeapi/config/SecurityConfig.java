package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.JwtTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // This enables @PreAuthorize on the new controller
public class SecurityConfig {
    private final JwtTokenFilter jwtTokenFilter;
    private final InternalSecretFilter internalSecretFilter;

    public SecurityConfig(JwtTokenFilter jwtTokenFilter, InternalSecretFilter internalSecretFilter) {
        this.jwtTokenFilter = jwtTokenFilter;
        this.internalSecretFilter = internalSecretFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
    
    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedPercent(true);
        firewall.setAllowUrlEncodedSlash(true); 
        firewall.setAllowSemicolon(true);
        return firewall;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // FIX: Allow all CORS preflight OPTIONS requests
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() 
                
                // Rule for static assets (CSS, JS, images, etc.)
                .requestMatchers(
                    "/static/**", 
                    "/favicon.ico", 
                    "/logo.webp",
                    "/logo192.webp", 
                    "/logo512.webp",
                    "/manifest.json",
                    "/amitsagar-kandpal-photo.png"
                ).permitAll()
                // Rule for public frontend pages served by ViewController
                .requestMatchers(
                    HttpMethod.GET,
                    "/", "/about", "/services", "/vision", "/education",
                    "/contact", "/login", "/blog", 
                    "/category/**",
                    "/post/**",
                    "/ssr-test",
                    "/dashboard/**"
                ).permitAll()
                // Rule for public API endpoints (GET ONLY)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/posts", 
                    "/api/posts/url/**",
                    "/api/categories", 
                    "/api/uploads/**",
                    "/api/market/**",
                    "/api/news/**",
                    "/sitemap.xml",    // Now serves the static index file
                    "/sitemaps/**"  // Now serves the static child files
                    // FIXED: Removed /api/logo since controller was deleted
                ).permitAll()
                // Rule for other public API endpoints (ALL HTTP METHODS including POST)
                .requestMatchers(
                    "/api/auth/**",
                    "/api/contact/**",
                    "/api/market/quotes/batch", // --- ADDED: Allow POST for batch quotes ---
                    "/swagger-ui/**", 
                    "/v3/api-docs/**"
                ).permitAll()
                
                // ADMIN-ONLY paths
                .requestMatchers(
                    "/api/posts/admin/**", 
                    "/api/market/admin/**", 
                    "/api/status/**", 
                    "/api/analytics/**",
                    "/api/admin/actions/**" // ADDED: Secure the new admin controller
                ).hasAuthority("ROLE_ADMIN")

                .anyRequest().authenticated()
            );
        http.addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
            "https://treishfin.treishvaamgroup.com",
            "http://localhost:3000"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "x-requested-with", "X-Internal-Secret"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}