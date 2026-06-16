package com.aiplatform.redis.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义 Redisson 配置 — 覆盖 redisson-spring-boot-starter 的 auto-config.
 * <p>关键点: {@code lazyInitialization=true} + 短超时 + 失败降级,
 * 让服务在 Redis 暂时不可用时仍能启动 (开发环境友好).</p>
 *
 * <p>Spring 装配顺序: 这个类优先于 {@code RedissonAutoConfigurationV2},
 * 因为 spring boot 的 auto-config 用 {@code @ConditionalOnMissingBean}.</p>
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${redisson.timeout:3000}")
    private int timeout;

    /**
     * 自定义 RedissonClient — 覆盖 starter 的 auto-config.
     *
     * <p>关键:
     * <ol>
     *   <li>{@code setLazyInitialization(true)} — 第一次使用时才连, 启动不阻塞</li>
     *   <li>3s connect timeout + 1 retry — 连不上快速失败, 不卡 Spring 启动</li>
     *   <li>{@code @ConditionalOnMissingBean} — 业务可自定义覆盖</li>
     * </ol>
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setPassword(password.isEmpty() ? null : password)
                .setConnectTimeout(timeout)
                .setTimeout(timeout)
                .setRetryAttempts(1)
                .setRetryInterval(500)
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(8)
                .setSubscriptionConnectionPoolSize(8);
        // ★ 关键: lazy 初始化, 不在启动时连 Redis (顶层 Config)
        config.setLazyInitialization(true);
        log.info("[REDISSON] initialized, address={}, database={}, lazy=true, timeout={}ms",
                address, database, timeout);
        return Redisson.create(config);
    }
}
