package com.treishvaam.financeapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // This applies the CORS rules to all of your API endpoints
                .allowedOrigins("http://localhost:3000") // The address of your React frontend
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // The HTTP methods you want to allow
                .allowedHeaders("*") // Allows all headers in the request
                .allowCredentials(true); // Allows cookies and authorization headers
    }
}