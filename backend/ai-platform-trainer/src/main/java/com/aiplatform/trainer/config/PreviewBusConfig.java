package com.aiplatform.trainer.config;

import com.aiplatform.trainer.model.preview.PreviewBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 把 Spring 容器里的 RedisTemplate (任意 StringRedisTemplate 子类) 注入到 PreviewBus.
 *
 * <p>不直接 import StringRedisTemplate, 是为了不让 trainer 模块强依赖 spring-data-redis
 * (它在父 pom 是 transitive, 但 trainer pom 没显式 import). 用 Object 类型, PreviewBus
 * 内部反射调用. Redis 不在线 / 容器里没 bean 时, PreviewBus 静默走内存版 (降级).</p>
 */
@Slf4j
@Configuration
public class PreviewBusConfig {

    @Autowired(required = false)
    private Object redisTemplate;

    @PostConstruct
    public void init() {
        if (redisTemplate == null) {
            log.info("[PREVIEW-CONFIG] 未发现 RedisTemplate, PreviewBus 走内存版");
            return;
        }
        // 只接 StringRedisTemplate / RedisTemplate (有 opsForList / expire)
        if (hasMethods(redisTemplate, "opsForList", "expire")) {
            PreviewBus.setRedis(redisTemplate);
            log.info("[PREVIEW-CONFIG] Redis 注入成功: {}", redisTemplate.getClass().getSimpleName());
        } else {
            log.warn("[PREVIEW-CONFIG] bean 不像是 RedisTemplate, 跳过: {}", redisTemplate.getClass().getName());
        }
    }

    private static boolean hasMethods(Object o, String... names) {
        for (String n : names) {
            try { o.getClass().getMethod(n); } catch (Exception e) { return false; }
        }
        return true;
    }
}