package com.treishvaam.financeapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  // REMOVED addCorsMappings to prevent conflict with SecurityConfig.
  // SecurityConfig.java is now the single source of truth for CORS.

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // 1. Serve Uploads (Match the API URL structure)
    // Fixes: /api/v1/uploads/image.webp -> /app/uploads/image.webp
    registry.addResourceHandler("/api/v1/uploads/**").addResourceLocations("file:/app/uploads/");

    // Fallback for legacy paths
    registry.addResourceHandler("/uploads/**").addResourceLocations("file:/app/uploads/");

    // 2. Serve Sitemaps
    registry.addResourceHandler("/api/v1/sitemaps/**").addResourceLocations("file:/app/sitemaps/");

    registry.addResourceHandler("/sitemaps/**").addResourceLocations("file:/app/sitemaps/");
  }
}
