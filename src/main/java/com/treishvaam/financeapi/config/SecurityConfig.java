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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Allow CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() 
                
                // Public API Endpoints (Read-Only)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/posts", 
                    "/api/posts/url/**",
                    "/api/categories", 
                    // Nginx handles /api/uploads/, but if a request hits here, we allow it just in case
                    "/api/uploads/**", 
                    "/api/market/**",
                    "/api/news/**",
                    "/sitemap.xml",
                    "/sitemaps/**" 
                ).permitAll()

                // Public API Endpoints (Write/Post allowed for specific actions like auth)
                .requestMatchers(
                    "/api/auth/**",
                    "/api/contact/**",
                    "/api/market/quotes/batch",
                    "/swagger-ui/**", 
                    "/v3/api-docs/**"
                ).permitAll()
                
                // ADMIN-ONLY paths
                .requestMatchers(
                    "/api/posts/admin/**", 
                    "/api/market/admin/**", 
                    "/api/status/**", 
                    "/api/analytics/**",
                    "/api/admin/actions/**",
                    // File upload is an Admin action
                    "/api/files/upload" 
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
            "http://localhost:3000",
            "http://localhost" // Allow local Nginx
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "x-requested-with", "X-Internal-Secret"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}