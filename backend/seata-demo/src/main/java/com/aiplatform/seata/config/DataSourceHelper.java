package com.aiplatform.seata.config;

import com.zaxxer.hikari.HikariDataSource;

/**
 * 简化的 DataSource 工厂 — 不依赖 spring-boot 的 auto-config，
 * 直接 hard-code H2 内存库的 URL（沙箱无 MySQL）。
 *
 * <p>生产换成 MySQL 时把 jdbcUrl 换成 mysql url 即可；其它代码不动。</p>
 */
final class DataSourceHelper {

    private DataSourceHelper() {}

    static HikariDataSource build(String jdbcUrl) {
        // H2 in-memory 单 JVM 内多连接是允许的；LOCK_MODE=0 关文件锁让多连接并发不据塞。
        String url = jdbcUrl;
        if (url.contains("LOCK_MODE=")) {
            // 已经有了
        } else {
            url = url + ";LOCK_MODE=0;DB_CLOSE_DELAY=-1";
        }
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        return ds;
    }
}
