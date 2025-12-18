package com.treishvaam.financeapi.config.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/** Intercepts every HTTP request to extract and set the Tenant ID. */
@Component
public class TenantInterceptor implements HandlerInterceptor {

  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String MDC_KEY_TENANT = "tenantId";

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {

    String tenantId = request.getHeader(TENANT_HEADER);

    // 1. Enterprise Validation: Sanitize the input
    if (tenantId != null) {
      tenantId = tenantId.trim().replaceAll("[^a-zA-Z0-9_-]", ""); // Prevent injection
    }

    // 2. Fallback Logic
    if (tenantId == null || tenantId.isEmpty()) {
      // For public endpoints, we might default to 'public' or specific logic
      // In a strict SaaS, you might reject the request here with 400 Bad Request
      tenantId = TenantContext.DEFAULT_TENANT;
    }

    // 3. Set Context
    TenantContext.setTenantId(tenantId);

    // 4. Update Logging Context (MDC) so all logs show the Tenant ID
    MDC.put(MDC_KEY_TENANT, tenantId);

    return true;
  }

  @Override
  public void postHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      ModelAndView modelAndView) {
    // No operation needed here
  }

  @Override
  public void afterCompletion(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      Exception ex) {
    // 5. CRITICAL: Always clear context to prevent memory leaks and data bleeding in thread pools
    TenantContext.clear();
    MDC.remove(MDC_KEY_TENANT);
  }
}
