package com.aiplatform.starter.web.security;

import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.common.util.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Convenience accessor for the current request's security info. Backed by the request itself
 * (no Spring Security session is required at every layer).
 *
 * <p>This class is a Spring component; inject it as a normal bean.</p>
 */
@Component
public class SecurityContextHolder {

    @Resource
    private JwtUtils jwtUtils;

    public HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "无请求上下文");
        }
        return attrs.getRequest();
    }

    public String currentToken() {
        String auth = currentRequest().getHeader(CommonConstants.HEADER_AUTH);
        if (auth == null || !auth.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return null;
        }
        return auth.substring(CommonConstants.TOKEN_PREFIX.length());
    }

    public Long currentUserId() {
        String token = currentToken();
        if (token == null || jwtUtils == null) {
            return null;
        }
        Object v = jwtUtils.getClaim(token, CommonConstants.CLAIM_USER_ID);
        return v == null ? null : Long.parseLong(v.toString());
    }

    public Long currentTenantId() {
        String header = currentRequest().getHeader(CommonConstants.HEADER_TENANT_ID);
        if (header != null && !header.isBlank()) {
            return Long.parseLong(header);
        }
        return null;
    }
}
