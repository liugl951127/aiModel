package com.aiplatform.redis.autoconfigure;

import com.aiplatform.redis.distributed.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 分布式能力自动装配.
 * <p>所有用到 redis/redisson 的服务, 引入 redis-starter 后自动获得 7 个 Bean:</p>
 * <ul>
 *   <li>{@link DistributedLock} 分布式锁</li>
 *   <li>{@link SnowflakeIdGenerator} 雪花 ID</li>
 *   <li>{@link DistributedRateLimiter} 分布式限流</li>
 *   <li>{@link DistributedIdempotency} 分布式幂等</li>
 *   <li>{@link DistributedCache} 分布式缓存</li>
 *   <li>{@link DistributedEventBus} 分布式事件总线</li>
 *   <li>{@link DistributedScheduler} 分布式调度器</li>
 * </ul>
 *
 * <p>设计: 所有 bean 都是 {@code @Autowired(required = false)} 友好 —
 * Redis 不可用时, Bean 仍然创建, 但内部调用会 try-catch 降级 (不抛异常).
 * 这样服务能起来, 业务可继续跑 (只是分布式特性降级).</p>
 */
@Slf4j
@Configuration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "ai-platform.distributed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DistributedAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock(
            @org.springframework.beans.factory.annotation.Autowired(required = false) RedissonClient redisson) {
        return new DistributedLock(redisson);
    }

    /**
     * 雪花 ID 生成器. 同一服务 datacenterId=1, machineId 用 IP hash 区分.
     * <p>纯内存, 不依赖 Redis, 永远可用.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        long machineId;
        try {
            machineId = Math.abs(InetAddress.getLocalHost().getHostAddress().hashCode()) % 32;
        } catch (UnknownHostException e) {
            machineId = 1;
        }
        return new SnowflakeIdGenerator(1, machineId);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedRateLimiter distributedRateLimiter(
            @org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate redis) {
        return new DistributedRateLimiter(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedIdempotency distributedIdempotency(
            @org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate redis) {
        return new DistributedIdempotency(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedCache distributedCache(
            @org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate redis) {
        return new DistributedCache(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedEventBus distributedEventBus(
            @org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate redis,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        return new DistributedEventBus(redis, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedScheduler distributedScheduler(
            @org.springframework.beans.factory.annotation.Autowired(required = false) DistributedLock lock) {
        return new DistributedScheduler(lock);
    }
}
