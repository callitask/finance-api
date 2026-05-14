package com.treishvaam.financeapi.config.tenant;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Thread-safe storage for the current Request's Tenant ID. Provides zero-trust tenant
 * isolation across all request-handling threads.
 *
 * <p>Scope: - Responsible for: storing, retrieving, and clearing the tenant ID per request. - Must
 * NEVER be responsible for: routing decisions, authentication, or business logic.
 *
 * <p>Critical Dependencies: - Backend: TenantInterceptor.java calls setTenantId / getTenantId /
 * clear on every request lifecycle. - All JPA @Filter tenant conditions depend on getTenantId()
 * returning the correct value.
 *
 * <p>Security Constraints: - Tenant ID must NEVER leak across requests. clear() MUST be called in
 * TenantInterceptor.afterCompletion() on every request without exception.
 *
 * <p>Non-Negotiables: - The public API (setTenantId, getTenantId, clear, DEFAULT_TENANT) must
 * remain unchanged — TenantInterceptor depends on it exactly as written. - ScopedValue binding is
 * handled by runWithTenant() which wraps the request execution scope. - getTenantId() must always
 * return a non-null, non-empty string (falls back to DEFAULT_TENANT).
 *
 * <p>Change Intent: - P2-2 (CVE-008): Migrated from InheritableThreadLocal to ScopedValue (Java
 * 21). InheritableThreadLocal does not guarantee correct propagation with Java 21 Virtual Threads
 * (Project Loom). Under Virtual Thread scheduling, a child virtual thread may inherit a stale or
 * wrong tenant context from a pooled carrier thread, causing cross-tenant data leakage. ScopedValue
 * is immutable per scope and safe for Virtual Threads by design.
 *
 * <p>Future AI Guidance: - Do NOT revert to ThreadLocal or InheritableThreadLocal. The ScopedValue
 * pattern is the Java 21 standard for request-scoped context with Virtual Threads. - The
 * runWithTenant() method is the ONLY correct way to bind a tenant for a scope. Do not call
 * TENANT.get() outside of a runWithTenant() scope — it will throw NoSuchElementException. -
 * TenantInterceptor.preHandle() must call runWithTenant() to wrap the downstream filter chain if
 * this pattern is adopted fully. For now, the ThreadLocal fallback field preserves backward
 * compatibility with the interceptor pattern.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Baseline TenantContext using
 * InheritableThreadLocal. • Phase: Initial multi-tenant implementation.
 *
 * <p>- EDITED (2026-05-14 P2-2 CVE-008 Fix): • Replaced InheritableThreadLocal with ScopedValue
 * (Java 21 stable). • Added TENANT ScopedValue field and runWithTenant() helper. • Preserved
 * ThreadLocal fallback (currentTenantFallback) for backward compatibility with TenantInterceptor's
 * preHandle/afterCompletion pattern which cannot wrap a ScopedValue scope around the full filter
 * chain without refactoring the interceptor. • getTenantId() checks ScopedValue first (isBound()),
 * then falls back to ThreadLocal, then DEFAULT_TENANT. • Why the edit was required: Java 21 Virtual
 * Threads do not reliably inherit ThreadLocal values from carrier threads. Cross-tenant
 * contamination is possible under high concurrency. • What behavior must remain unchanged:
 * setTenantId(), getTenantId(), clear(), DEFAULT_TENANT constant — all called identically by
 * TenantInterceptor.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantContext {

  private static final Logger logger = LoggerFactory.getLogger(TenantContext.class);

  /** DEFAULT_TENANT is used as a fallback or for public data access. */
  public static final String DEFAULT_TENANT = "public";

  /**
   * ScopedValue for Java 21 Virtual Thread-safe tenant binding. Use runWithTenant() to bind a
   * tenant for a specific execution scope. ScopedValue is immutable within a scope and does NOT
   * leak across Virtual Thread boundaries.
   */
  public static final ScopedValue<String> TENANT = ScopedValue.newInstance();

  /**
   * ThreadLocal fallback for backward compatibility with TenantInterceptor's
   * preHandle/afterCompletion lifecycle. This is used when the caller sets tenant via setTenantId()
   * rather than runWithTenant(). Will be removed in a future phase when TenantInterceptor is
   * refactored to use ScopedValue.where().run() wrapping.
   */
  private static final ThreadLocal<String> currentTenantFallback = new ThreadLocal<>();

  /**
   * Sets the tenant ID for the current thread via the ThreadLocal fallback. Called by
   * TenantInterceptor.preHandle(). Prefer runWithTenant() for new code using Virtual Threads.
   */
  public static void setTenantId(String tenantId) {
    logger.debug("Setting Tenant Context (ThreadLocal fallback): {}", tenantId);
    currentTenantFallback.set(tenantId);
  }

  /**
   * Returns the current tenant ID. Checks ScopedValue first (Virtual Thread-safe), then ThreadLocal
   * fallback, then DEFAULT_TENANT. Never returns null or empty string.
   */
  public static String getTenantId() {
    // 1. Check ScopedValue (set via runWithTenant — Virtual Thread safe)
    if (TENANT.isBound()) {
      String scopedTenant = TENANT.get();
      if (scopedTenant != null && !scopedTenant.trim().isEmpty()) {
        return scopedTenant;
      }
    }
    // 2. Fall back to ThreadLocal (set via setTenantId — interceptor pattern)
    String fallback = currentTenantFallback.get();
    if (fallback != null && !fallback.trim().isEmpty()) {
      return fallback;
    }
    // 3. Default
    return DEFAULT_TENANT;
  }

  /**
   * Clears the ThreadLocal fallback. Called by TenantInterceptor.afterCompletion(). CRITICAL: Must
   * always be called to prevent memory leaks in thread pools.
   */
  public static void clear() {
    logger.debug("Clearing Tenant Context");
    currentTenantFallback.remove();
  }

  /**
   * Executes the given Runnable within a ScopedValue-bound tenant context. This is the preferred
   * Virtual Thread-safe method for binding tenant context in new async code. Example usage:
   * TenantContext.runWithTenant("finance", () -> blogPostService.findAll());
   *
   * @param tenantId the tenant ID to bind for this scope
   * @param action the code to execute within this tenant scope
   */
  public static void runWithTenant(String tenantId, Runnable action) {
    ScopedValue.where(TENANT, tenantId).run(action);
  }
}
