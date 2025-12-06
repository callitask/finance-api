package com.treishvaam.financeapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE - 1)
public class InternalSecretFilter extends OncePerRequestFilter {

  @Value("${app.security.internal-secret}")
  private String expectedSecret;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();

    // 1. Do NOT filter the public sitemap URL.
    if ("/api/sitemap.xml".equals(path)) {
      return true; // Tells the filter to skip this request.
    }

    // 2. ONLY apply this filter to its intended endpoint (V1)
    // The filter should ONLY RUN for POST requests to /api/v1/posts.
    boolean shouldFilter =
        "POST".equalsIgnoreCase(request.getMethod()) && path.equals("/api/v1/posts");

    return !shouldFilter; // Return true to skip filtering for all other requests.
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String headerSecret = request.getHeader("X-Internal-Secret");

    if (headerSecret != null && headerSecret.equals(expectedSecret)) {
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(
              "INTERNAL_SERVICE",
              null,
              Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL")));
      SecurityContextHolder.getContext().setAuthentication(auth);
    }

    filterChain.doFilter(request, response);
  }
}
