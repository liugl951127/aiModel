package com.aiplatform.starter.mybatis.autofill;

import com.aiplatform.common.tenant.TenantContext;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * Auto-fill tenantId, createBy, updateBy, createTime, updateTime, deleted.
 *
 * <p>User id is resolved from a thread-local set by web layer's
 * {@code com.aiplatform.starter.web.security.SecurityContextHolder}
 * via the convenience setter {@link #setCurrentUserId(Long)}. This keeps
 * the mybatis-starter free of servlet API dependencies.</p>
 */
@Slf4j
public class MybatisAutoFillHandler implements MetaObjectHandler {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    /** Web layer sets this before each service call. */
    public static void setCurrentUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static void clear() {
        CURRENT_USER.remove();
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long tenantId = TenantContext.getTenantId();
        Long userId = CURRENT_USER.get();

        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        if (tenantId != null) {
            strictInsertFill(metaObject, "tenantId", Long.class, tenantId);
        }
        if (userId != null) {
            strictInsertFill(metaObject, "createBy", Long.class, userId);
            strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
