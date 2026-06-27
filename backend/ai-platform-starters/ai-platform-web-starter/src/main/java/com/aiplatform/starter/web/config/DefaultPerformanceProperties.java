package com.aiplatform.starter.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 默认性能配置 (HikariCP + MyBatis-Plus + 慢 SQL).
 *
 * <h3>应用 yml 覆盖示例</h3>
 * <pre>
 * spring:
 *   datasource:
 *     hikari:
 *       maximum-pool-size: 30
 * aiplatform:
 *   sql:
 *     slow-threshold-ms: 300
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "aiplatform")
public class DefaultPerformanceProperties {

    private Sql sql = new Sql();
    private Web web = new Web();

    @Data
    public static class Sql {
        /** 是否启用慢 SQL 监控 */
        private boolean enabled = true;
        /** 慢 SQL 阈值 (ms) */
        private long slowThresholdMs = 200;
        /** 是否打印慢 SQL 日志 */
        private boolean logEnabled = true;
    }

    @Data
    public static class Web {
        private Page page = new Page();

        @Data
        public static class Page {
            /** MyBatis-Plus 分页拦截器 */
            private boolean enabled = true;
        }
    }
}