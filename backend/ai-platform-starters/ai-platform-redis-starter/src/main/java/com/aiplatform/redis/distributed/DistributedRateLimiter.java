package com.aiplatform.redis.distributed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;

/**
 * 分布式限流器 (Redis token bucket, Lua 原子).
 * <p>支持: 全局 / 用户 / 租户 / API 多维限流.</p>
 *
 * <h3>用法</h3>
 * <pre>
 * boolean ok = rateLimiter.tryAcquire("user:login:u123", 5, 60);
 * // 每 60s 最多 5 次, 失败抛 / 拒绝
 * </pre>
 */
@Slf4j
public class DistributedRateLimiter {

    /**
     * Lua 脚本: 滑动窗口限流.
     * key: 限流 key
     * limit: 时间窗内允许次数
     * window: 时间窗 (秒)
     */
    private static final String LUA_SCRIPT =
        "local current = redis.call('incr', KEYS[1]) " +
        "if tonumber(current) == 1 then " +
        "  redis.call('expire', KEYS[1], tonumber(ARGV[2])) " +
        "end " +
        "if tonumber(current) > tonumber(ARGV[1]) then " +
        "  return 0 " +
        "end " +
        "return 1";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public DistributedRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    /**
     * 尝试获取令牌.
     * @param key 限流 key (建议带 user/tenant 维度)
     * @param limit 窗口内允许次数
     * @param windowSec 时间窗秒
     * @return true 允许, false 限流
     */
    public boolean tryAcquire(String key, int limit, int windowSec) {
        try {
            Long result = redis.execute(script, Collections.singletonList(key), String.valueOf(limit), String.valueOf(windowSec));
            return result != null && result == 1;
        } catch (Exception e) {
            log.warn("限流检查失败, 默认放行: {}", e.getMessage());
            return true;  // 降级: 放行
        }
    }

    /**
     * 获取当前窗口计数.
     */
    public long currentCount(String key) {
        if (redis == null) return 0;
        try {
            String v = redis.opsForValue().get(key);
            return v == null ? 0 : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("currentCount({}) Redis 异常: {}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * 重置限流计数.
     */
    public void reset(String key) {
        if (redis == null) return;
        try { redis.delete(key); } catch (Exception e) { log.warn("reset({}) Redis 异常: {}", key, e.getMessage()); }
    }
}
