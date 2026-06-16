package com.aiplatform.redis.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自定义 Redisson 配置 — 覆盖 redisson-spring-boot-starter 的 auto-config.
 *
 * <h3>核心策略</h3>
 * <ol>
 *   <li><b>方法名 = "redisson"</b>: 覆盖 starter 中
 *       {@code RedissonAutoConfigurationV2#redisson(...)} 方法, Spring 看到同 bean 名就跳过 starter 的</li>
 *   <li><b>同步 warmup</b>: {@code ContextRefreshedEvent} 触发, 主线程 join (限时 warmupTimeoutMs),
 *       启动结束前 warmup 一定完成 (或超时), <b>不残留异步线程</b> — 避免 shutdown 时 Netty 回调撞
 *       applicationTaskExecutor 正在 destruction 的窗口</li>
 *   <li><b>懒加载兜底</b>: {@code setLazyInitialization(true)}, 减少 Netty 资源</li>
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

    private volatile RedissonClient clientRef;

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
                .setRetryAttempts(3)               // 业务重试 3 次, 不只 1 次
                .setRetryInterval(1000)            // 重试间隔 1s
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(8)
                .setSubscriptionConnectionPoolSize(8)
                .setIdleConnectionTimeout(30000)   // 30s 空闲超时 (防止 stale)
                .setKeepAlive(true)                // TCP keep-alive
                .setTcpNoDelay(true);              // 关闭 Nagle 算法, 减少延迟
        // 懒加载: 避免启动时 netty 连接. warmup 主动触发.
        config.setLazyInitialization(true);
        log.info("[REDISSON] configured: address={}, database={}, timeout={}ms, warmup={}",
                address, database, timeout, warmupOnStartup);
        clientRef = Redisson.create(config);
        return clientRef;
    }

    /**
     * 同步 warmup: Context 全部 refresh 完后, 主线程 join 一个 worker 线程 (限时 warmupTimeoutMs).
     * <p>这样:</p>
     * <ol>
     *   <li>warmup 完成/超时后, 不会再有异步线程持有 Netty 回调</li>
     *   <li>Spring 关闭时, Redisson.shutdown() 不会撞上正在 cancelled 的 future
     *       试图在 applicationTaskExecutor 上排队的 race</li>
     *   <li>最坏情况就是 startup 多等 warmupTimeoutMs (默认 5s)</li>
     * </ol>
     */
    @EventListener(ContextRefreshedEvent.class)
    public void warmupRedis() {
        if (!warmupOnStartup) {
            log.info("[REDISSON] warmup-on-startup=false, skip");
            return;
        }
        RedissonClient client = clientRef;
        if (client == null) {
            log.warn("[REDISSON] no RedissonClient bean, skip warmup");
            return;
        }

        // worker 线程
        Thread worker = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                boolean acquired = client.getLock("__ai_platform_warmup__")
                        .tryLock(0, 100, TimeUnit.MILLISECONDS);
                if (acquired) {
                    try {
                        client.getLock("__ai_platform_warmup__").unlock();
                    } catch (Exception ignore) {
                    }
                }
                long dt = System.currentTimeMillis() - t0;
                REDIS_READY.set(true);
                log.info("[REDISSON] [OK] connected in {}ms ({}:{})", dt, host, port);
            } catch (Throwable e) {
                long dt = System.currentTimeMillis() - t0;
                REDIS_READY.set(false);
                log.warn("[REDISSON] [WARN] connect failed ({}:{}) after {}ms: {} — degraded mode",
                        host, port, dt, e.getMessage());
            }
        }, "redisson-warmup-worker");
        worker.setDaemon(true);

        // 同步等待 (限时)
        long t0 = System.currentTimeMillis();
        try {
            worker.start();
            worker.join(warmupTimeoutMs);
            long dt = System.currentTimeMillis() - t0;
            if (worker.isAlive()) {
                log.warn("[REDISSON] [WARN] warmup timeout after {}ms — will check on first op", dt);
                // 不 interrupt worker (可能在 Netty 回调中), 标记为 best-effort
                worker = null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[REDISSON] warmup interrupted after {}ms", System.currentTimeMillis() - t0);
        }
    }
}
