package com.treishvaam.financeapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  // Static resource serving is now handled by Nginx (Enterprise Architecture).
  // We keep the class for potential future CORS or Interceptor configs.

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // Intentionally empty.
    // Images are served via Nginx location /api/uploads/ -> C:/treishvaam-uploads-prod/
    // Frontend is served via Cloudflare Pages.
  }
}
