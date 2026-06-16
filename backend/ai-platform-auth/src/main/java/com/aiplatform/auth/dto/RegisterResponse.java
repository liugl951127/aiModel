package com.aiplatform.auth.dto;

/**
 * 注册响应 DTO.
 */
public class RegisterResponse {
    private Long userId;
    private String username;
    private Long tenantId;
    private String message;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
