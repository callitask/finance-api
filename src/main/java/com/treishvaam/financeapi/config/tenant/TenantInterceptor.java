package com.treishvaam.financeapi.config.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Intercepts every HTTP request to extract, sanitize, and strictly validate the
 * Tenant ID.
 *
 * <p>Scope: - Zero-Trust boundary for multitenancy.
 *
 * <p>Security Constraints: - Tenant IDs must be strictly whitelisted to prevent injection or
 * cross-tenant contamination.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: Baseline TenantInterceptor. - EDITED: •
 * Added strict whitelist validation to explicitly authorize the 'agro' tenant. • Date / Phase:
 * Phase 3 (Backend Dynamic Integration).
 */
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

            // 2. Strict Whitelist Enforcement
            if (!"finance".equals(tenantId) && !"agro".equals(tenantId)) {
                tenantId = TenantContext.DEFAULT_TENANT;
            }
        } else {
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
        // 5. CRITICAL: Always clear context to prevent memory leaks and data bleeding in thread
        // pools
        TenantContext.clear();
        MDC.remove(MDC_KEY_TENANT);
    }
}
