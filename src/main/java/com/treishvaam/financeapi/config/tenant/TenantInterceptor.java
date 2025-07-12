package com.treishvaam.financeapi.config.tenant;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Autowired
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String origin = request.getHeader("Origin");
        String tenantId = null;

        if (origin != null) {
            URI originUri = new URI(origin);
            String host = originUri.getHost();

            // --- THIS LOGIC DETERMINES THE TENANT ID ---
            // Handles live frontend domains
            if (host.endsWith("treishvaamgroup.com")) {
                tenantId = host.split("\\.")[0]; // e.g., "treishfin" from "treishfin.treishvaamgroup.com"
            }
            // Handles local development environment
            else if (host.equals("localhost")) {
                // For local testing, we'll default to the 'treishfin' tenant.
                // You can change this to whatever tenant you are testing.
                tenantId = "treishfin";
            }
        }

        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
            // Enable the Hibernate filter for the current session
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // No action needed here for our use case
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // Clear the tenant context after the request is complete to prevent memory leaks
        TenantContext.clear();
    }
}
