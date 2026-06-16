package com.aiplatform.starter.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Lightweight Redis helper. 所有方法自带降级, Redis 不可用时返回 null/false, 不抛异常.
 */
@Slf4j
@Component
public class RedisUtils {

    private final StringRedisTemplate redisTemplate;

    public RedisUtils(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, String value, long timeoutSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[Redis] set({}) failed: {}", key, e.getMessage());
        }
    }

    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[Redis] get({}) failed: {}", key, e.getMessage());
            return null;
        }
    }

    public boolean delete(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.warn("[Redis] del({}) failed: {}", key, e.getMessage());
            return false;
        }
    }

    public Long increment(String key) {
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn("[Redis] incr({}) failed: {}", key, e.getMessage());
            return null;
        }
    }

    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("[Redis] hasKey({}) failed: {}", key, e.getMessage());
            return false;
        }
    }

    public void expire(String key, long timeoutSeconds) {
        try {
            redisTemplate.expire(key, timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[Redis] expire({}) failed: {}", key, e.getMessage());
        }
    }

    /**
     * 健康检查: 用于 /actuator/health 端点.
     */
    public boolean ping() {
        try {
            return "PONG".equalsIgnoreCase(redisTemplate.getConnectionFactory().getConnection().ping());
        } catch (Exception e) {
            return false;
        }
    }
}
