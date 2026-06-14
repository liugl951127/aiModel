package com.aiplatform.common.tenant;

import com.aiplatform.common.annotation.IgnoreTenant;
import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.common.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * Resolves the tenant id for the current request and stores it in {@link TenantContext}.
 *
 * Resolution order:
 *   1. {@code X-Tenant-Id} header (preferred, set by gateway from JWT)
 *   2. {@code tenantId} claim from the bearer token
 *   3. {@code @IgnoreTenant} on the handler - skip resolution, downstream is responsible
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

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
            // In a real cluster this is enforced; allow it through and let the data layer
            // complain if the entity requires tenant_id. This keeps public endpoints usable.
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
        if (token != null && token.startsWith(CommonConstants.TOKEN_PREFIX)) {
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
