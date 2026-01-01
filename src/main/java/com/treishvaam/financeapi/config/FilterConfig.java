package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.security.InternalSecretFilter;
import com.treishvaam.financeapi.security.RateLimitingFilter;
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
    FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false); // Disable auto-registration
    return registration;
  }
}
