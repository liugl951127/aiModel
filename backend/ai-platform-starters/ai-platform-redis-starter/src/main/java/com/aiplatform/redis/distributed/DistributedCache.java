package com.aiplatform.redis.distributed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式缓存 (防击穿 + 软失效).
 * <p>用法: 替代 @Cacheable 注解的硬编码, 动态 ttl + null 兜底.</p>
 *
 * <h3>用法</h3>
 * <pre>
 * User user = cache.getOrLoad("user:" + id, 60, () -> userMapper.selectById(id));
 * </pre>
 */
@Slf4j
public class DistributedCache {

    private final StringRedisTemplate redis;

    public DistributedCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * get-or-load 模式.
     * @param key cache key
     * @param ttlSec 失效秒
     * @param loader 加载函数
     * @return 缓存值
     */
    public <T> T getOrLoad(String key, int ttlSec, Supplier<T> loader) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                if ("__NULL__".equals(cached)) return null;
                return (T) cached;
            }
        } catch (Exception e) {
            log.warn("[Cache] read failed, fallback to loader: {}", e.getMessage());
        }
        T value = loader.get();
        try {
            redis.opsForValue().set(key, value == null ? "__NULL__" : value.toString(), ttlSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[Cache] write failed: {}", e.getMessage());
        }
        return value;
    }

    public void put(String key, Object value, int ttlSec) {
        if (redis == null) return;
        try { redis.opsForValue().set(key, value == null ? "__NULL__" : value.toString(), ttlSec, TimeUnit.SECONDS); }
        catch (Exception e) { log.warn("[Cache] put({}) failed: {}", key, e.getMessage()); }
    }

    public <T> T get(String key) {
        if (redis == null) return null;
        try { return (T) redis.opsForValue().get(key); }
        catch (Exception e) { log.warn("[Cache] get({}) failed: {}", key, e.getMessage()); return null; }
    }

    public void evict(String key) {
        if (redis == null) return;
        try { redis.delete(key); }
        catch (Exception e) { log.warn("[Cache] evict({}) failed: {}", key, e.getMessage()); }
    }

    /** evict 的别名 — 业务代码可读性更好 */
    public void invalidate(String key) {
        evict(key);
    }

    /**
     * get-or-load 模式 (复杂对象, JSON 序列化).
     * @param key cache key
     * @param ttlSec 失效秒
     * @param loader 加载函数 (返回复杂对象)
     * @param typeRef 反序列化目标类型 (e.g. new TypeReference<List<ModelRegistry>>(){})
     * @return 缓存值
     */
    public <T> T getOrLoadJson(String key, int ttlSec, Supplier<Object> loader, com.fasterxml.jackson.core.type.TypeReference<T> typeRef) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                if ("__NULL__".equals(cached)) return null;
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(cached, typeRef);
            }
        } catch (Exception e) {
            log.warn("[Cache] JSON read failed, fallback: {}", e.getMessage());
        }
        Object value = loader.get();
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
            redis.opsForValue().set(key, value == null ? "__NULL__" : json, ttlSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[Cache] JSON write failed: {}", e.getMessage());
        }
        @SuppressWarnings("unchecked")
        T result = (T) value;
        return result;
    }

    /**
     * get-or-load 模式 (单对象, JSON 序列化).
     */
    public <T> T getOrLoad(String key, int ttlSec, Supplier<T> loader, Class<T> type) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                if ("__NULL__".equals(cached)) return null;
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(cached, type);
            }
        } catch (Exception e) {
            log.warn("[Cache] JSON read failed, fallback: {}", e.getMessage());
        }
        T value = loader.get();
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
            redis.opsForValue().set(key, value == null ? "__NULL__" : json, ttlSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[Cache] JSON write failed: {}", e.getMessage());
        }
        return value;
    }

    public void evictByPattern(String pattern) {
        if (redis == null) return;
        try {
            java.util.Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) { log.warn("[Cache] evictByPattern({}) failed: {}", pattern, e.getMessage()); }
    }

    /**
     * 获取缓存统计 (Redis INFO keyspace).
     */
    public String stats() {
        try {
            try {
            return redis.getConnectionFactory().getConnection().info("keyspace").getProperty("keyspace");
        } catch (Exception e) {
            return "{}";
        }
        } catch (Exception e) {
            return "{}";
        }
    }
}
