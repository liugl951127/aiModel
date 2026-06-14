package com.aiplatform.starter.web.interceptor;

import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.starter.mybatis.autofill.MybatisAutoFillHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Pulls {@code X-User-Id} from the request (set by the gateway after JWT validation)
 * and pushes it into {@link MybatisAutoFillHandler}'s thread-local so mybatis-plus
 * can auto-fill the {@code create_by} / {@code update_by} columns.
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            try {
                MybatisAutoFillHandler.setCurrentUserId(Long.parseLong(userId));
            } catch (NumberFormatException ignored) {
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MybatisAutoFillHandler.clear();
    }
}
