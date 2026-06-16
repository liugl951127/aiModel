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
        Boolean ok = redis.opsForValue().setIfAbsent(key, "CLAIMED", ttlSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 保存执行结果 (供重复请求返回).
     */
    public void saveResult(String key, String result, int ttlSec) {
        redis.opsForValue().set(key + ":result", result, ttlSec, TimeUnit.SECONDS);
    }

    /**
     * 获取上次执行结果.
     */
    public String getResult(String key) {
        return redis.opsForValue().get(key + ":result");
    }

    /**
     * 释放幂等 key (异常时回滚).
     */
    public void release(String key) {
        redis.delete(key);
        redis.delete(key + ":result");
    }
}
