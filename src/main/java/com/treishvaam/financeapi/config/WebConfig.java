package com.treishvaam.financeapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${storage.upload-dir}")
    private String uploadDir;

    @Value("${storage.sitemap-dir}")
    private String sitemapDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Handler for file uploads
        String uploadResourceLocation = Paths.get(uploadDir).toFile().getAbsolutePath();
        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations("file:/" + uploadResourceLocation + "/");

        // NEW: Handler for static sitemaps
        String sitemapResourceLocation = Paths.get(sitemapDir).toFile().getAbsolutePath();
        
        // Serve the main sitemap index file
        registry.addResourceHandler("/sitemap.xml")
                .addResourceLocations("file:/" + sitemapResourceLocation + "/sitemap.xml");
        
        // Serve all child sitemap files (e.g., /sitemaps/posts-1.xml)
        registry.addResourceHandler("/sitemaps/**")
                .addResourceLocations("file:/" + sitemapResourceLocation + "/");
    }
}