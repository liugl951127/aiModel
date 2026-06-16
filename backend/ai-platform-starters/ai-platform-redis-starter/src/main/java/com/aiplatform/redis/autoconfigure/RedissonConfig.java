package com.aiplatform.redis.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自定义 Redisson 配置 — 覆盖 redisson-spring-boot-starter 的 auto-config.
 *
 * <h3>核心策略</h3>
 * <ol>
 *   <li><b>方法名 = "redisson"</b>: 覆盖 starter 中
 *       {@code RedissonAutoConfigurationV2#redisson(...)} 方法, Spring 看到同 bean 名就跳过 starter 的</li>
 *   <li><b>启动时主动连</b>: 后台线程 tryLock 触发实际连接, 5s 内拿到结果就 log [OK]/[WARN]</li>
 *   <li><b>不阻塞启动</b>: 后台线程 + lazy=true + 短超时, 失败仅 warn, 不抛异常</li>
 *   <li><b>健康检查</b>: {@link #REDIS_READY} AtomicBoolean 暴露状态</li>
 * </ol>
 *
 * <p>可配项 (application.yml):</p>
 * <pre>
 * redisson:
 *   warmup-on-startup: true        # 是否启动时主动连 (默认 true)
 *   warmup-timeout-ms: 5000        # 启动 warmup 最多等几 ms
 * </pre>
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

    @Value("${redisson.warmup-on-startup:true}")
    private boolean warmupOnStartup;

    @Value("${redisson.warmup-timeout-ms:5000}")
    private long warmupTimeoutMs;

    /**
     * Redisson 连接状态 (供健康检查用)
     */
    public static final AtomicBoolean REDIS_READY = new AtomicBoolean(false);

    /**
     * 自定义 RedissonClient — 方法名必须叫 "redisson",
     * <p>与 starter 的 {@code RedissonAutoConfigurationV2#redisson} 同名, Spring 通过
     * {@code @ConditionalOnMissingBean(name = "redisson")} 跳过 starter 的注册.</p>
     */
    @Bean(name = "redisson", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "redisson")
    public RedissonClient redisson() {
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
        // 懒加载: 避免首次 op 阻塞. 后台 warmup 线程会主动连
        config.setLazyInitialization(true);
        log.info("[REDISSON] configured: address={}, database={}, timeout={}ms, warmup={}",
                address, database, timeout, warmupOnStartup);
        RedissonClient client = Redisson.create(config);
        // 创建后立即异步 warmup
        if (warmupOnStartup) {
            scheduleWarmup(client);
        }
        return client;
    }

    /**
     * 异步 warmup: 触发实际连接, 不阻塞调用方.
     */
    private void scheduleWarmup(RedissonClient client) {
        Thread t = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                // tryLock 触发实际连接 (等 warmup-timeout-ms)
                boolean acquired = client.getLock("__ai_platform_warmup__")
                        .tryLock(warmupTimeoutMs, 100, TimeUnit.MILLISECONDS);
                long dt = System.currentTimeMillis() - t0;
                if (acquired) {
                    try { client.getLock("__ai_platform_warmup__").unlock(); } catch (Exception ignore) {}
                }
                // 不管 acquired true/false, 只要没抛异常就说明连接成功
                REDIS_READY.set(true);
                log.info("[REDISSON] [OK] connected to Redis in {}ms ({}:{})", dt, host, port);
            } catch (Exception e) {
                long dt = System.currentTimeMillis() - t0;
                REDIS_READY.set(false);
                log.warn("[REDISSON] [WARN] startup connect failed ({}:{}) after {}ms: {} — service will start in degraded mode",
                        host, port, dt, e.getMessage());
            }
        }, "redisson-warmup");
        t.setDaemon(true);
        t.start();
    }
}
