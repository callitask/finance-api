package com.treishvaam.financeapi.config.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * remain unchanged — TenantInterceptor depends on it exactly as written. - getTenantId() must
 * always return a non-null, non-empty string (falls back to DEFAULT_TENANT).
 *
 * <p>Change Intent: - P2-2 (CVE-008): ScopedValue migration was ATTEMPTED but REJECTED because
 * ScopedValue is a preview API in Java 21 (stable only in Java 23 via JEP 481). Enabling
 * --enable-preview in production Maven builds is unsafe. Reverted to InheritableThreadLocal.
 * ScopedValue migration is deferred until the project upgrades to Java 23+.
 *
 * <p>Future AI Guidance: - DO NOT attempt ScopedValue migration on Java 21 without adding
 * --enable-preview to maven-compiler-plugin compilerArgs in pom.xml AND verifying the CI/CD
 * pipeline supports preview APIs. - When Java 23+ is adopted, replace InheritableThreadLocal with
 * ScopedValue.newInstance() and use ScopedValue.where(TENANT, id).run(() -> ...) pattern. - Do NOT
 * use ThreadLocal (non-inheritable) — child threads spawned manually will not inherit the tenant
 * context. InheritableThreadLocal is the correct choice for Java 21.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Baseline TenantContext using
 * InheritableThreadLocal. • Phase: Initial multi-tenant implementation.
 *
 * <p>- FAILED / REJECTED (2026-05-14 P2-2 CVE-008 Attempt): • Attempted to replace
 * InheritableThreadLocal with ScopedValue (Java 21). • FAILED: ScopedValue is a preview API in Java
 * 21 (JEP 429 — preview only). Stable in Java 23 (JEP 481). • Build error: "java.lang .ScopedValue
 * is a preview API and is disabled by default. Use --enable-preview to enable." • REJECTED: Adding
 * --enable-preview to production Maven builds is unsafe and non-standard. • DO NOT RETRY
 * ScopedValue on Java 21 without --enable-preview. Defer to Java 23 upgrade.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
public class TenantContext {

    private static final Logger logger = LoggerFactory.getLogger(TenantContext.class);

    // DEFAULT_TENANT is used as a fallback or for public data access
    public static final String DEFAULT_TENANT = "public";

    // InheritableThreadLocal ensures that if a thread spawns a child thread
    // manually,
    // the tenant ID is passed down.
    // NOTE: ScopedValue migration deferred to Java 23+ — see FAILED/REJECTED
    // history above.
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
