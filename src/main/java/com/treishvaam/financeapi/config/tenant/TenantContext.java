package com.treishvaam.financeapi.config.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe storage for the current Request's Tenant ID. Uses InheritableThreadLocal to support
 * child threads, reinforced by AsyncConfig.
 */
public class TenantContext {

  private static final Logger logger = LoggerFactory.getLogger(TenantContext.class);

  // DEFAULT_TENANT is used as a fallback or for public data access
  public static final String DEFAULT_TENANT = "public";

  // InheritableThreadLocal ensures that if a thread spawns a child thread manually,
  // the tenant ID is passed down.
  private static final ThreadLocal<String> currentTenant = new InheritableThreadLocal<>();

  public static void setTenantId(String tenantId) {
    logger.debug("Setting Tenant Context: {}", tenantId);
    currentTenant.set(tenantId);
  }

  public static String getTenantId() {
    String tenantId = currentTenant.get();
    if (tenantId == null || tenantId.trim().isEmpty()) {
      return DEFAULT_TENANT;
    }
    return tenantId;
  }

  public static void clear() {
    logger.debug("Clearing Tenant Context");
    currentTenant.remove();
  }
}
