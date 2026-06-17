package com.aiplatform.trainer.config;

import com.aiplatform.trainer.model.preview.PreviewBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.Map;

/**
 * 把 Spring 容器里的 RedisTemplate (任意 StringRedisTemplate 子类) 注入到 PreviewBus.
 *
 * <p><b>关键</b>: trainer 模块没显式 import redis-starter, 容器里可能根本没 RedisTemplate bean.
 * 用 ApplicationListener 而不是 @Autowired: 容器刷新后事件触发, 此时如果找不到 bean 就静默跳过,
 * 不会阻塞 Spring 启动.</p>
 *
 * <p>如果将来 trainer 引了 redis-starter, 这个 listener 会自动拿到 StringRedisTemplate,
 * PreviewBus 走 Redis 版. 没引就降级走内存.</p>
 */
@Slf4j
@Configuration
public class PreviewBusConfig implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        // 尝试拿 StringRedisTemplate / RedisTemplate — 按类型找 (没有就降级)
        Object redis = null;
        String[] candidateNames = {"stringRedisTemplate", "redisTemplate"};
        for (String name : candidateNames) {
            try {
                redis = ctx.getBean(name);
                break;
            } catch (NoSuchBeanDefinitionException ignore) {
                // bean 不存在, 试下一个
            }
        }
        // 兜底: 按类型查 (反射找 StringRedisTemplate 类, trainer 模块没引依赖, 避免编译期 import)
        if (redis == null) {
            try {
                Class<?> stringRedisTemplateClass = Class.forName("org.springframework.data.redis.core.StringRedisTemplate");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Map beans = ctx.getBeansOfType(stringRedisTemplateClass);
                if (!beans.isEmpty()) redis = beans.values().iterator().next();
            } catch (ClassNotFoundException ignore) {
                // spring-data-redis 不在 classpath, 跳过
            } catch (Exception ignore) {
                // NoSuchBeanDefinitionException / NoUniqueBeanDefinitionException / 其他, 都跳过
            }
        }

        if (redis == null) {
            log.info("[PREVIEW-CONFIG] 容器里没 RedisTemplate, PreviewBus 走内存版 (降级 OK)");
            return;
        }
        // 反射找 opsForList + expire
        try {
            redis.getClass().getMethod("opsForList");
            redis.getClass().getMethod("expire", Object.class, java.time.Duration.class);
            PreviewBus.setRedis(redis);
            log.info("[PREVIEW-CONFIG] Redis 注入成功: {} (PreviewBus 走 Redis 版)", redis.getClass().getSimpleName());
        } catch (NoSuchMethodException e) {
            log.warn("[PREVIEW-CONFIG] bean 有 RedisTemplate 类但缺方法, 跳过: {}", redis.getClass().getName());
        } catch (Exception e) {
            log.warn("[PREVIEW-CONFIG] 检查 Redis bean 失败, 走内存版: {}", e.getMessage());
        }
    }
}