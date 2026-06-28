package com.aiplatform.starter.web.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 慢 SQL 监控拦截器 (MyBatis Interceptor 标准方式).
 *
 * <p>拦截 {@link StatementHandler#prepare(Connection, Integer)} — SQL 真正执行前.
 * 计时准备耗时 + 执行耗时 (total - prepare ≈ DB 执行时间).
 *
 * <h3>配置</h3>
 * <pre>
 * aiplatform:
 *   sql:
 *     slow-threshold-ms: 200  # 慢 SQL 阈值 ms, 默认 200
 *     log-enabled: true        # 是否打印慢 SQL 日志
 * </pre>
 *
 * <h3>注册</h3>
 * Spring 自动扫描 @Component, 但 MyBatis 拦截器需配置在 SqlSessionFactory 的 plugins.
 * 由 {@link MybatisInterceptorConfig} 把这个 bean 注册为 interceptor.
 */
@Slf4j
@Component
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {java.sql.Connection.class, Integer.class}),
    @Signature(type = StatementHandler.class, method = "update", args = {java.sql.Connection.class}),
    @Signature(type = StatementHandler.class, method = "batch", args = {java.sql.Connection.class})
})
public class SlowSqlMybatisInterceptor implements Interceptor {

    @Value("${aiplatform.sql.slow-threshold-ms:200}")
    private long slowThresholdMs;

    @Value("${aiplatform.sql.log-enabled:true}")
    private boolean logEnabled;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!logEnabled) return invocation.proceed();
        long start = System.currentTimeMillis();
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - start;
            log.error("[SQL-ERR] cost={}ms e={}", cost, e.getMessage());
            throw e;
        }
        long cost = System.currentTimeMillis() - start;
        if (cost >= slowThresholdMs) {
            String sqlInfo = extractSql(invocation);
            log.warn("[SQL-SLOW] cost={}ms {}", cost, sqlInfo);
        }
        return result;
    }

    private String extractSql(Invocation invocation) {
        try {
            StatementHandler handler = (StatementHandler) invocation.getTarget();
            MetaObject metaObject = SystemMetaObject.forObject(handler);
            MappedStatement ms = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
            BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
            String sql = boundSql != null ? boundSql.getSql().replaceAll("\\s+", " ").trim() : "?";
            String params = boundSql != null
                ? String.valueOf(boundSql.getParameterObject())
                : "";
            if (params.length() > 150) params = params.substring(0, 150) + "...";
            return String.format("msId=%s sql=%s params=%s", ms != null ? ms.getId() : "?", sql, params);
        } catch (Exception e) {
            return "(extract failed: " + e.getMessage() + ")";
        }
    }

    @Override
    public Object plugin(Object target) {
        return org.apache.ibatis.plugin.Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 配置由 @Value 注入
    }
}