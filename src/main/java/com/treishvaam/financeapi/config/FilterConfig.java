package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InputSanitizationFilter;
import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
import com.treishvaam.financeapi.security.aegis.AegisDeceptionFilter;
import com.treishvaam.financeapi.security.aegis.AegisMainFilter;
import com.treishvaam.financeapi.security.aegis.AegisZkpAdminFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    // Prevent InternalSecretFilter from running automatically outside SecurityChain
    @Bean
    public FilterRegistrationBean<InternalSecretFilter> internalSecretFilterRegistration(
            InternalSecretFilter filter) {
        FilterRegistrationBean<InternalSecretFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Disable auto-registration
        return registration;
    }

    // Prevent RateLimitingFilter from running automatically outside SecurityChain
    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(
            RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Disable auto-registration
        return registration;
    }

    // Prevent AegisDeceptionFilter from running automatically outside SecurityChain
    @Bean
    public FilterRegistrationBean<AegisDeceptionFilter> aegisDeceptionFilterRegistration(
            AegisDeceptionFilter filter) {
        FilterRegistrationBean<AegisDeceptionFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Disable auto-registration
        return registration;
    }

    // Prevent AegisMainFilter from running automatically outside SecurityChain
    @Bean
    public FilterRegistrationBean<AegisMainFilter> aegisMainFilterRegistration(
            AegisMainFilter filter) {
        FilterRegistrationBean<AegisMainFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Disable auto-registration
        return registration;
    }

    // Prevent InputSanitizationFilter from running automatically outside SecurityChain
    @Bean
    public FilterRegistrationBean<InputSanitizationFilter> inputSanitizationFilterRegistration(
            InputSanitizationFilter filter) {
        FilterRegistrationBean<InputSanitizationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Disable auto-registration
        return registration;
    }

    // Prevent AegisZkpAdminFilter from running automatically outside SecurityChain
    @Bean
    public FilterRegistrationBean<AegisZkpAdminFilter> aegisZkpAdminFilterRegistration(
            AegisZkpAdminFilter filter) {
        FilterRegistrationBean<AegisZkpAdminFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Disable auto-registration
        return registration;
    }
}
