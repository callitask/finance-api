/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - MVC configuration: registers {@code TenantInterceptor} for tenant-aware routes and
 * preserves legacy static-resource serving for uploads/sitemaps.
 *
 * <p>Scope: - {@code addInterceptors()} registration (VULN-19: TenantInterceptor was unregistered);
 * untouched {@code addResourceHandlers()} legacy rules.
 *
 * <p>Security Constraints: - Interceptor must stay registered on {@code /api/**}, {@code
 * /sitemap*}, {@code /llms.txt}, {@code /ai-feed.md}, {@code /ontology.json}. - No CORS mappings
 * here (SecurityConfig is the single source of truth for CORS).
 *
 * <p>Change Intent: - Safely register TenantInterceptor without breaking legacy user_preferences
 * rows (zero-crash migration handled in UserPreferencesServiceImpl).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Batch 2B - P2.6, VULN-19): • Injected
 * TenantInterceptor via constructor and registered it on /api/**, /sitemap*, /llms.txt,
 * /ai-feed.md, /ontology.json. • Added AI-CONTEXT block; {@code addResourceHandlers(...)} rules
 * preserved verbatim.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section acts as the institutional memory
 * for future AI sessions. It must never be deleted, truncated, rewritten, or regenerated. Future AI
 * must append only.
 */
package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.config.tenant.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // REMOVED addCorsMappings to prevent conflict with SecurityConfig.
    // SecurityConfig.java is now the single source of truth for CORS.

    private final TenantInterceptor tenantInterceptor;

    public WebConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns(
                        "/api/**", "/sitemap*", "/llms.txt", "/ai-feed.md", "/ontology.json");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 1. Serve Uploads (Match the API URL structure)
        // Fixes: /api/v1/uploads/image.webp -> /app/uploads/image.webp
        registry.addResourceHandler("/api/v1/uploads/**")
                .addResourceLocations("file:/app/uploads/");

        // Fallback for legacy paths
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:/app/uploads/");

        // 2. Serve Sitemaps
        registry.addResourceHandler("/api/v1/sitemaps/**")
                .addResourceLocations("file:/app/sitemaps/");

        registry.addResourceHandler("/sitemaps/**").addResourceLocations("file:/app/sitemaps/");
    }
}
