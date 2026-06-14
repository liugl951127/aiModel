package com.aiplatform.common.constant;

/**
 * Platform-wide constants (header names, JWT claim names, etc.).
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    public static final String HEADER_AUTH = "Authorization";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String TOKEN_PREFIX = "Bearer ";

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_ROLES = "roles";

    public static final Long SUPER_TENANT_ID = 1L;
    public static final Long PLATFORM_ADMIN_ROLE = 1L;
}
