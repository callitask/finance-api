package com.treishvaam.financeapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;
    
    @Value("${storage.upload-dir}")
    private String uploadDir;

    // Uncommented and updated addCorsMappings to allow CORS requests from your live frontend domain with all necessary methods and headers
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /**
     * --- NEW METHOD ---
     * This exposes the 'user-uploads' directory to be accessible via the /api/uploads/** URL path.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // --- MODIFICATION: Use the injected, configurable path ---
        String resourceLocation = java.nio.file.Paths.get(uploadDir).toFile().getAbsolutePath();
        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations("file:/" + resourceLocation + "/");
    }
}