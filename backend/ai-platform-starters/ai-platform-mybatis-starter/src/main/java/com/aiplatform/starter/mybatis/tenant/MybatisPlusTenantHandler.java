package com.aiplatform.starter.mybatis.tenant;

import com.aiplatform.common.tenant.TenantContext;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;

import java.util.Set;

/**
 * MyBatis-Plus multi-tenant line handler. Injects a {@code tenant_id = ?} predicate
 * into every query that targets a multi-tenant table.
 */
@Slf4j
public class MybatisPlusTenantHandler implements TenantLineHandler {

    /** Tables that are NOT tenant scoped (system tables, join tables, etc.). */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "sys_tenant",
            "sys_user",
            "sys_role",
            "sys_menu",
            "sys_user_role",
            "sys_role_menu"
    );

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return new NullValue();
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName.toLowerCase());
    }
}
