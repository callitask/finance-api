package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.config.tenant.TenantInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // This registers our interceptor to run for all API endpoints
        registry.addInterceptor(tenantInterceptor).addPathPatterns("/api/**");
    }
}
