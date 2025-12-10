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
        .allowedOriginPatterns("*") // Use patterns to allow credential support
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
        .allowedHeaders("*")
        .exposedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
