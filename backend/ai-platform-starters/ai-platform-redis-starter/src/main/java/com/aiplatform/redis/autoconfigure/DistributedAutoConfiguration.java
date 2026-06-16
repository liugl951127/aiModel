package com.aiplatform.redis.autoconfigure;

import com.aiplatform.redis.distributed.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

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
 */
@Configuration
@ConditionalOnClass(RedissonClient.class)
public class DistributedAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock(RedissonClient redisson) {
        return new DistributedLock(redisson);
    }

    /**
     * 雪花 ID 生成器. 同一服务 datacenterId=1, machineId 用 IP hash 区分.
     */
    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        long machineId = Math.abs(java.net.InetAddress.getLoopbackAddress().getHostAddress().hashCode()) % 32;
        return new SnowflakeIdGenerator(1, machineId);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedRateLimiter distributedRateLimiter(StringRedisTemplate redis) {
        return new DistributedRateLimiter(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedIdempotency distributedIdempotency(StringRedisTemplate redis) {
        return new DistributedIdempotency(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedCache distributedCache(StringRedisTemplate redis) {
        return new DistributedCache(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedEventBus distributedEventBus(StringRedisTemplate redis, ObjectMapper objectMapper) {
        return new DistributedEventBus(redis, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedScheduler distributedScheduler(DistributedLock lock) {
        return new DistributedScheduler(lock);
    }
}
