package com.aiplatform.redis.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 分布式事件总线 (Redis pub/sub).
 * <p>跨服务异步事件: model.deployed / workflow.completed / audit.log / ...</p>
 *
 * <h3>用法</h3>
 * <pre>
 * // 发送
 * eventBus.publish("model.deployed", Map.of("modelId", 1, "stage", "prod"));
 *
 * // 订阅
 * eventBus.subscribe("model.*", event -> {
 *     log.info("收到事件: {}", event);
 * });
 * </pre>
 *
 * <p><b>Redis 不可用降级</b>: publish 静默丢弃, subscribe 静默注册 (实际不工作),
 * 业务不会因事件总线挂掉报错.</p>
 */
@Slf4j
public class DistributedEventBus {

    public static final String CHANNEL_PREFIX = "ai-platform:event:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer container;
    private final Map<String, Consumer<Map<String, Object>>> subscribers = new ConcurrentHashMap<>();
    private final boolean degraded;

    public DistributedEventBus(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.degraded = (redis == null);
        if (this.degraded) {
            log.warn("[EventBus] Redis 不可用, 事件总线降级 (publish 丢弃, subscribe 不工作)");
            this.container = null;
        } else {
            this.container = new RedisMessageListenerContainer();
            this.container.setConnectionFactory(redis.getConnectionFactory());
            this.container.start();
        }
    }

    public boolean isDegraded() {
        return degraded;
    }

    /**
     * 发布事件.
     */
    public void publish(String topic, Object payload) {
        if (degraded) {
            log.debug("[EventBus] degraded mode, drop event: {}", topic);
            return;
        }
        String channel = CHANNEL_PREFIX + topic;
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "topic", topic,
                "ts", System.currentTimeMillis(),
                "payload", payload
            ));
            redis.convertAndSend(channel, json);
            log.debug("[EventBus] published: {}", channel);
        } catch (Exception e) {
            log.warn("[EventBus] publish failed ({}), 事件已丢: {}", e.getMessage(), topic);
        }
    }

    /**
     * 订阅事件 (支持 * 通配符).
     */
    public void subscribe(String pattern, Consumer<Map<String, Object>> handler) {
        if (degraded) {
            log.debug("[EventBus] degraded mode, register subscriber {} (not active)", pattern);
            return;
        }
        String fullPattern = CHANNEL_PREFIX + pattern;
        subscribers.put(fullPattern, handler);
        MessageListener listener = (Message message, byte[] channelBytes) -> {
            try {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> event = objectMapper.readValue(body, Map.class);
                handler.accept(event);
            } catch (Exception e) {
                log.warn("[EventBus] handle event failed: {}", e.getMessage());
            }
        };
        MessageListenerAdapter adapter = new MessageListenerAdapter(listener);
        container.addMessageListener(adapter, new PatternTopic(fullPattern));
        log.info("[EventBus] subscribed: {}", fullPattern);
    }

    public void shutdown() {
        if (container == null) return;
        try { container.stop(); } catch (Exception e) { log.warn(e.getMessage()); }
    }
}
