package com.aiplatform.starter.web.config;

import com.aiplatform.starter.web.interceptor.SlowSqlMybatisInterceptor;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis 拦截器配置 — 慢 SQL 监控 + 分页.
 *
 * <p>通过 SqlSessionFactory#setPlugins() 把慢 SQL 拦截器注册到 MyBatis.
 * MyBatis-Plus 的分页拦截器通过 {@link MybatisPlusInterceptor} 内部添加.
 *
 * <h3>配置</h3>
 * <pre>
 * aiplatform:
 *   sql:
 *     enabled: true                # 是否启用慢 SQL 监控, 默认 true
 *     slow-threshold-ms: 200       # 慢 SQL 阈值 ms, 默认 200
 *     log-enabled: true            # 是否打印慢 SQL 日志
 *   web:
 *     page:
 *       enabled: true             # MyBatis-Plus 分页拦截器, 默认 true
 * </pre>
 */
@Slf4j
@Configuration
@ConditionalOnMissingBean(MybatisInterceptorConfig.class)
@RequiredArgsConstructor
public class MybatisInterceptorConfig {

    private final SqlSessionFactory sqlSessionFactory;

    @Value("${aiplatform.sql.slow-threshold-ms:200}")
    private long slowThresholdMs;

    @Value("${aiplatform.sql.log-enabled:true}")
    private boolean sqlLogEnabled;

    @Value("${aiplatform.web.page.enabled:true}")
    private boolean pageEnabled;

    /**
     * 自定义 SQL 拦截器注册 — 慢 SQL 监控.
     * 每次 create 时打印一次启动日志.
     */
    @Bean
    public SqlSessionFactory slowSqlSqlSessionFactory(SqlSessionFactory origin, List<org.apache.ibatis.plugin.Interceptor> customInterceptors) throws Exception {
        List<org.apache.ibatis.plugin.Interceptor> interceptors = new ArrayList<>(origin.getConfiguration().getInterceptors());
        // 业务拦截器 (慢 SQL 等)
        if (sqlLogEnabled) {
            SlowSqlMybatisInterceptor slowSql = new SlowSqlMybatisInterceptor();
            // 通过反射设值 — Spring @Value 在字段被注入前已注入
            slowSql.getClass().getDeclaredField("slowThresholdMs").setAccessible(true);
            slowSql.getClass().getDeclaredField("slowThresholdMs").set(slowSql, slowThresholdMs);
            slowSql.getClass().getDeclaredField("logEnabled").setAccessible(true);
            slowSql.getClass().getDeclaredField("logEnabled").set(slowSql, sqlLogEnabled);
            interceptors.add(slowSql);
            log.info("[MybatisInterceptorConfig] 慢 SQL 监控启用, threshold={}ms", slowThresholdMs);
        }
        // 合并已有拦截器
        origin.getConfiguration().setInterceptors(interceptors);
        return origin;
    }

    /**
     * MyBatis-Plus 分页拦截器.
     * 配合 MybatisPlusInterceptor 自动注入分页 (Page<T>).
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (pageEnabled) {
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
            log.info("[MybatisInterceptorConfig] MyBatis-Plus 分页拦截器已注册");
        }
        return interceptor;
    }
}