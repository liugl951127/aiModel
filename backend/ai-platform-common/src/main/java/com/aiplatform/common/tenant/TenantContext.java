package com.aiplatform.common.tenant;

/**
 * Holds the current tenant id on a thread local. Every request entering a controller
 * has its tenant id resolved by the {@link TenantInterceptor} before business code runs.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> CODE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT.get();
    }

    public static void setTenantCode(String code) {
        CODE.set(code);
    }

    public static String getTenantCode() {
        return CODE.get();
    }

    public static void clear() {
        CURRENT.remove();
        CODE.remove();
    }
}
