package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.JwtTokenFilter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
import com.treishvaam.financeapi.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenFilter jwtTokenFilter;
    private final CustomUserDetailsService userDetailsService;
    private final InternalSecretFilter internalSecretFilter;
    private final RateLimitingFilter rateLimitingFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // --- AUTHORIZATION RULES ---
            .authorizeHttpRequests(auth -> auth
                // 1. Allow Public Auth Endpoints
                .requestMatchers("/api/auth/**").permitAll()
                
                // 2. Allow Public Content (Blogs, Market Data)
                .requestMatchers("/api/posts/public/**", "/api/posts/{slug}", "/api/posts/category/**").permitAll()
                .requestMatchers("/api/categories/**").permitAll()
                .requestMatchers("/api/market/public/**").permitAll()
                .requestMatchers("/api/search/**").permitAll()
                .requestMatchers("/api/contact/**").permitAll()
                .requestMatchers("/api/news/public/**").permitAll()
                .requestMatchers("/api/files/**").permitAll()
                .requestMatchers("/api/uploads/**").permitAll() 
                .requestMatchers("/api/page-content/**").permitAll()
                .requestMatchers("/api/status/**").permitAll()
                
                // 3. Allow Swagger UI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                // 4. OBSERVABILITY (Phase 9 Update)
                // Allow Prometheus to scrape metrics without logging in
                .requestMatchers("/actuator/**").permitAll()

                // 5. Internal Service Communication
                .requestMatchers("/api/internal/**").permitAll()

                // 6. Everything else requires Authentication
                .anyRequest().authenticated()
            )
            
            // --- EXCEPTION HANDLING ---
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Error: Unauthorized");
                })
            )
            
            // --- FILTERS ---
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow specific origins (adjust for production domain)
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://treishfin.treishvaamgroup.com")); 
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Internal-Secret"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}