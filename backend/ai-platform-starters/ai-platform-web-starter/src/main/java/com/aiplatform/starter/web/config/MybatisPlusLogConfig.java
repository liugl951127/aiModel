package com.aiplatform.starter.web.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * MyBatis-Plus log-impl 自动配置 — 打印所有 SQL 到指定 logger.
 *
 * <p>默认开启. 业务模块可在 application.yml 中覆盖:
 * <pre>
 * aiplatform:
 *   mybatis-plus:
 *     log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl  # 关掉日志
 * </pre>
 *
 * <p>说明: 此配置只设置默认值 (若 application.yml 没指定),
 * 让 LogImpl 默认 stdout 打印 SQL (开发环境方便).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "aiplatform.sql.log-enabled", havingValue = "true", matchIfMissing = true)
public class MybatisPlusLogConfig {

    @Value("${aiplatform.sql.log-impl:org.apache.ibatis.logging.stdout.StdOutImpl}")
    private String logImpl;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @PostConstruct
    public void init() {
        log.info("[MybatisPlusLog] SQL 日志实现: {} (DB: {})", logImpl, datasourceUrl);
    }

    public String getLogImpl() {
        return logImpl;
    }

    public void applyTo(Configuration config) {
        if (config != null && config.getLogImpl() == null) {
            try {
                Class<?> clazz = Class.forName(logImpl);
                config.setLogImpl((org.apache.ibatis.logging.Log) clazz.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                log.warn("[MybatisPlusLog] failed to apply {}: {}", logImpl, e.getMessage());
            }
        }
    }
}