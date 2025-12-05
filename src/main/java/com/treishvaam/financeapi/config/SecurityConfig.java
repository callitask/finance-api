package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.JwtTokenFilter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtTokenFilter jwtTokenFilter;
    private final InternalSecretFilter internalSecretFilter;
    private final RateLimitingFilter rateLimitingFilter;

    public SecurityConfig(JwtTokenFilter jwtTokenFilter, 
                          InternalSecretFilter internalSecretFilter,
                          RateLimitingFilter rateLimitingFilter) {
        this.jwtTokenFilter = jwtTokenFilter;
        this.internalSecretFilter = internalSecretFilter;
        this.rateLimitingFilter = rateLimitingFilter;
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // --- 1. Allow Prometheus Metrics & Health ---
                .requestMatchers("/actuator/**", "/health").permitAll()

                // --- 2. Public V1 API Endpoints ---
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() 
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
                    "/sitemaps/**",
                    "/favicon.ico"
                ).permitAll()
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/contact/**",
                    "/api/v1/market/quotes/batch",
                    "/api/v1/market/widget",
                    "/swagger-ui/**", 
                    "/v3/api-docs/**",
                    "/error" 
                ).permitAll()
                
                // --- 3. Admin Secured V1 Endpoints ---
                .requestMatchers(
                    "/api/v1/posts/admin/**", 
                    "/api/v1/market/admin/**", 
                    "/api/v1/status/**", 
                    "/api/v1/analytics/**",
                    "/api/v1/admin/actions/**",
                    "/api/v1/files/upload" 
                ).hasAuthority("ROLE_ADMIN")
                
                .anyRequest().authenticated()
            );
        
        // --- ORDER MATTERS: RateLimit -> InternalSecret -> JWT ---
        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); 
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}