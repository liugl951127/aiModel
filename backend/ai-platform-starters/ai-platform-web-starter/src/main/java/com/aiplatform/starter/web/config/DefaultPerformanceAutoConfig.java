package com.aiplatform.starter.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 默认性能配置启用 (HikariCP + 慢 SQL).
 *
 * <p>启用 {@link DefaultPerformanceProperties} 让 application.yml 的
 * aiplatform.sql.* 和 aiplatform.web.page.* 配置生效.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DefaultPerformanceProperties.class)
@ConditionalOnProperty(name = "aiplatform.perf.enabled", havingValue = "true", matchIfMissing = true)
public class DefaultPerformanceAutoConfig {

    @Bean
    @ConditionalOnMissingBean(DefaultPerformanceProperties.class)
    public DefaultPerformanceProperties defaultPerformanceProperties() {
        log.info("[DefaultPerformance] 默认性能配置加载: 慢 SQL 监控 + HikariCP 调优");
        return new DefaultPerformanceProperties();
    }
}