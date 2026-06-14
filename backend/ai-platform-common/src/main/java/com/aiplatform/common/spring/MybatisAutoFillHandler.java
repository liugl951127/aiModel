package com.aiplatform.common.spring;

import com.aiplatform.common.tenant.TenantContext;
import com.aiplatform.common.util.SecurityContextHolder;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * Auto-fill {@code tenantId}, {@code createBy}, timestamps, soft-delete flag.
 */
@Slf4j
public class MybatisAutoFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long tenantId = TenantContext.getTenantId();
        Long userId = safeUserId();

        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        if (tenantId != null) {
            this.strictInsertFill(metaObject, "tenantId", Long.class, tenantId);
        }
        if (userId != null) {
            this.strictInsertFill(metaObject, "createBy", Long.class, userId);
            this.strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        Long userId = safeUserId();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateBy", Long.class, userId);
        }
    }

    private Long safeUserId() {
        try {
            return SecurityContextHolder.currentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
