package com.aiplatform.common.util;

import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Convenience accessor for the current request's security info. Backed by the request itself
 * (no Spring Security session is required at every layer).
 */
public final class SecurityContextHolder {

    private SecurityContextHolder() {
    }

    public static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "无请求上下文");
        }
        return attrs.getRequest();
    }

    public static String currentToken() {
        String auth = currentRequest().getHeader(CommonConstants.HEADER_AUTH);
        if (auth == null || !auth.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return null;
        }
        return auth.substring(CommonConstants.TOKEN_PREFIX.length());
    }

    public static Long currentUserId() {
        String token = currentToken();
        if (token == null) {
            return null;
        }
        Object v = new JwtUtils() {{ init(); }}.getClaim(token, CommonConstants.CLAIM_USER_ID);
        return v == null ? null : Long.parseLong(v.toString());
    }

    public static Long currentTenantId() {
        String header = currentRequest().getHeader(CommonConstants.HEADER_TENANT_ID);
        if (header != null && !header.isBlank()) {
            return Long.parseLong(header);
        }
        return null;
    }
}
