package com.aiplatform.redis.distributed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 分布式幂等性.
 * <p>防止表单重复提交 / API 重试导致数据重复.</p>
 *
 * <h3>用法</h3>
 * <pre>
 * // token 由前端生成 (UUID), 调 API 时带 header "X-Idempotency-Key"
 * String key = "idem:order:create:" + token;
 * boolean first = idempotency.tryClaim(key, 30);  // 30s 内只允许一次
 * if (!first) {
 *     // 重复请求, 直接返回上次结果
 *     String cached = idempotency.getResult(key);
 *     return cached;
 * }
 * String result = doStuff();
 * idempotency.saveResult(key, result, 300);
 * </pre>
 *
 * <p><b>Redis 不可用降级</b>: {@code tryClaim} 始终返回 true (失去幂等保护),
 * 业务可继续运行, 重复请求可能执行多次 — 由调用方决定是否启用本地兜底.</p>
 */
@Slf4j
public class DistributedIdempotency {

    private final StringRedisTemplate redis;

    public DistributedIdempotency(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试认领幂等 key.
     * @return true=首次, false=已存在
     */
    public boolean tryClaim(String key, int ttlSec) {
        if (redis == null) {
            log.debug("[Idem] Redis 不可用, 失去幂等保护, 直接放行 ({})", key);
            return true;
        }
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, "CLAIMED", ttlSec, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("[Idem] tryClaim({}) Redis 异常, 降级放行: {}", key, e.getMessage());
            return true;
        }
    }

    /**
     * 保存执行结果 (供重复请求返回).
     */
    public void saveResult(String key, String result, int ttlSec) {
        if (redis == null) return;
        try {
            redis.opsForValue().set(key + ":result", result, ttlSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[Idem] saveResult({}) Redis 异常: {}", key, e.getMessage());
        }
    }

    /**
     * 获取上次执行结果.
     */
    public String getResult(String key) {
        if (redis == null) return null;
        try {
            return redis.opsForValue().get(key + ":result");
        } catch (Exception e) {
            log.warn("[Idem] getResult({}) Redis 异常: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 释放幂等 key (异常时回滚).
     */
    public void release(String key) {
        if (redis == null) return;
        try {
            redis.delete(key);
            redis.delete(key + ":result");
        } catch (Exception e) {
            log.warn("[Idem] release({}) Redis 异常: {}", key, e.getMessage());
        }
    }
}
