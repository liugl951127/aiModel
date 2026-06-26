package com.aiplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO.
 * <p>手机号 + 验证码 + 密码 + 公司 (tenant) 选择.
 * 用于公开注册接口 POST /api/auth/register.</p>
 */
public class RegisterRequest {

    @NotBlank(message = "国家区号不能为空")
    private String countryCode = "+86";

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不对")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 8, message = "验证码长度 4-8")
    private String captcha;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码 6-64 位")
    private String password;

    @NotNull(message = "公司不能为空")
    private Long tenantId;

    /**
     * 部门 (可选). 不存为独立表, 字符串属性.
     */
    private String department;

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCaptcha() { return captcha; }
    public void setCaptcha(String captcha) { this.captcha = captcha; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}
