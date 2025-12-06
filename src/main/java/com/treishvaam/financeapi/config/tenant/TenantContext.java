package com.treishvaam.financeapi.config.tenant;

public class TenantContext {

  private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

  public static String getCurrentTenant() {
    return currentTenant.get();
  }

  public static void setCurrentTenant(String tenantId) {
    currentTenant.set(tenantId);
  }

  public static void clear() {
    currentTenant.remove();
  }
}
