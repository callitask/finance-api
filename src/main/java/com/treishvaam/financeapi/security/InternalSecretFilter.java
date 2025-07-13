package com.treishvaam.financeapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.annotation.Order; // ADD THIS IMPORT
import org.springframework.core.Ordered; // ADD THIS IMPORT
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Collections;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE - 1) // ADD THIS LINE: Ensures it runs before other important filters
public class InternalSecretFilter extends OncePerRequestFilter {

    @Value("${app.security.internal-secret}")
    private String expectedSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only filter POST to /api/posts
        return !("POST".equalsIgnoreCase(request.getMethod()) &&
                 request.getRequestURI().equals("/api/posts"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String headerSecret = request.getHeader("X-Internal-Secret");

        // Authenticate if secret is valid
        if (headerSecret != null && headerSecret.equals(expectedSecret)) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "INTERNAL_SERVICE",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        // Continue the filter chain regardless
        filterChain.doFilter(request, response);
    }
}