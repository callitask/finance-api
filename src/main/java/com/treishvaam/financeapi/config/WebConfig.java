package com.treishvaam.financeapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  // Static resource serving is mostly handled by Nginx, but CORS logic here
  // reinforces the SecurityConfig to prevent "No Access-Control-Allow-Origin" errors.

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // Intentionally empty.
    // Images are served via Nginx location /api/uploads/ -> C:/treishvaam-uploads-prod/
    // Frontend is served via Cloudflare Pages.
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        // Use patterns to allow credential support (cookies/auth headers)
        .allowedOriginPatterns(
            "https://*.treishvaamgroup.com",
            "http://localhost:3000",
            "https://treishfin.treishvaamgroup.com")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
        // Explicitly allow Faro/Grafana headers to prevent Preflight 403 errors
        .allowedHeaders("*", "x-faro-session-id", "x-faro-session-vol", "x-faro-session-meta")
        .exposedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
