package com.treishvaam.financeapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${storage.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation = java.nio.file.Paths.get(uploadDir).toFile().getAbsolutePath();
        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations("file:/" + resourceLocation + "/");
    }
}