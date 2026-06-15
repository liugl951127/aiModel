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
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        return ds;
    }
}
