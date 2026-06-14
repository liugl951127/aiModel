package com.aiplatform.starter.web.interceptor;

import com.aiplatform.common.annotation.IgnoreTenant;
import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.common.tenant.TenantContext;
import com.aiplatform.common.util.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * Resolves the tenant id for the current request and stores it in {@link TenantContext}.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code X-Tenant-Id} header (preferred, set by the gateway from JWT)</li>
 *   <li>{@code tenantId} claim from the bearer token</li>
 *   <li>{@code @IgnoreTenant} on the handler - skip resolution</li>
 * </ol>
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean ignored = false;
        if (handler instanceof HandlerMethod hm) {
            ignored = hm.hasMethodAnnotation(IgnoreTenant.class)
                    || hm.getBeanType().isAnnotationPresent(IgnoreTenant.class);
        }

        Long tenantId = resolveTenantId(request);
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        } else if (!ignored) {
            log.debug("[TENANT] no tenant resolved for {}", request.getRequestURI());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }

    private Long resolveTenantId(HttpServletRequest request) {
        String header = request.getHeader(CommonConstants.HEADER_TENANT_ID);
        if (header != null && !header.isBlank()) {
            try {
                return Long.parseLong(header);
            } catch (NumberFormatException ignored) {
            }
        }
        String token = request.getHeader(CommonConstants.HEADER_AUTH);
        if (token != null && token.startsWith(CommonConstants.TOKEN_PREFIX) && jwtUtils != null) {
            String raw = token.substring(CommonConstants.TOKEN_PREFIX.length());
            try {
                Object claim = jwtUtils.getClaim(raw, CommonConstants.CLAIM_TENANT_ID);
                if (Objects.nonNull(claim)) {
                    return Long.parseLong(claim.toString());
                }
            } catch (Exception e) {
                log.debug("[TENANT] cannot read tenantId claim: {}", e.getMessage());
            }
        }
        return null;
    }
}
