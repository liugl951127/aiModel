package com.aiplatform.trainer.model.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Process-local pub-sub for live training events. Each job has its own
 * listener list; the SSE controller attaches a listener when a client
 * subscribes and detaches it when the stream closes.
 *
 * <p>This intentionally avoids Spring's {@code ApplicationEventPublisher}
 * because we want fine-grained, per-job fan-out and explicit lifecycle.
 */
@Slf4j
public final class PreviewBus {

    /** Event kinds. */
    public enum EventType { STEP, SAMPLE, METRIC, WARN, DONE }

    /** Event payload pushed to listeners. */
    public record Event(EventType type, int step, double loss, Map<String, Double> metrics,
                        Map<String, Double> antiHallucination, String sample) {}

    private static final Map<String, List<Consumer<Event>>> LISTENERS = new ConcurrentHashMap<>();
    /** Last N events per job, replayed to late subscribers. */
    private static final Map<String, java.util.Deque<Event>> RING = new ConcurrentHashMap<>();
    private static final int RING_SIZE = 64;

    /** ★ Redis 持久化 (重启不丢), 由 PreviewBusConfig 注入. */
    private static volatile Object REDIS;  // StringRedisTemplate (反射调用, 不引依赖)
    private static final ObjectMapper OM = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** Spring 启动时调用 (setter injection). */
    public static void setRedis(Object redis) { REDIS = redis; }

    /** 反射调用 ListOps / Expire. 不让 trainer 模块依赖 spring-data-redis.
     * ★ 用 java.lang.reflect.Proxy 兼容动态代理, 走 proxy.getClass().getInterfaces() + find method. */
    private static void redisPush(String key, String value, Duration ttl) {
        if (REDIS == null) return;
        try {
            Object listOps = invokeOnProxy(REDIS, "opsForList");
            if (listOps == null) return;
            invokeOnProxy(listOps, "rightPush", key, value);
            invokeOnProxy(listOps, "trim", key, -RING_SIZE, -1);
            invokeOnProxy(REDIS, "expire", key, ttl);
        } catch (Exception e) { log.debug("[PREVIEW] redisPush 失败: {}", e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private static List<String> redisRange(String key, long start, long end) {
        if (REDIS == null) return null;
        try {
            Object listOps = invokeOnProxy(REDIS, "opsForList");
            if (listOps == null) return null;
            return (List<String>) invokeOnProxy(listOps, "range", key, start, end);
        } catch (Exception e) {
            log.debug("[PREVIEW] redisRange 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 在 proxy 或普通对象上找方法并调用. */
    private static Object invokeOnProxy(Object target, String methodName, Object... args) throws Exception {
        if (target == null) return null;
        // ★ 1) 先在自己类上找
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] == null ? Object.class : args[i].getClass();
                if (paramTypes[i] == Integer.class) paramTypes[i] = int.class;
                else if (paramTypes[i] == Long.class) paramTypes[i] = long.class;
                else if (paramTypes[i] == Boolean.class) paramTypes[i] = boolean.class;
            }
            Method m = target.getClass().getMethod(methodName, paramTypes);
            return m.invoke(target, args);
        } catch (NoSuchMethodException ignore) {
            // 2) 在接口上找 (Proxy 的接口)
            for (Class<?> iface : target.getClass().getInterfaces()) {
                try {
                    Class<?>[] paramTypes = new Class<?>[args.length];
                    for (int i = 0; i < args.length; i++) {
                        paramTypes[i] = args[i] == null ? Object.class : args[i].getClass();
                    }
                    Method m = iface.getMethod(methodName, paramTypes);
                    return m.invoke(target, args);
                } catch (NoSuchMethodException ignore2) { /* try next */ }
            }
            return null;
        }
    }

    private static String ringKey(String jobId) { return "preview:ring:" + jobId; }
    private static final Duration RING_TTL = Duration.ofHours(2);

    private static Event fromJson(String json) {
        try {
            return OM.readValue(json, Event.class);
        } catch (Exception e) {
            log.debug("[PREVIEW] JSON 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private PreviewBus() {}

    /** Register a listener for {@code jobId}. Returns a handle to detach.
     *  Late subscribers get a replay of the last {@link #RING_SIZE} events first. */
    public static AutoCloseable subscribe(String jobId, Consumer<Event> listener) {
        List<Consumer<Event>> list = LISTENERS.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        log.debug("[PREVIEW] subscribe job={} (now {} listeners)", jobId, list.size());
        // ★ 先从 Redis 拉历史 (重启服务后还有), 再从内存拉
        List<Event> history = new ArrayList<>();
        try {
            List<String> raw = redisRange(ringKey(jobId), -RING_SIZE, -1);
            if (raw != null) for (String s : raw) history.add(fromJson(s));
        } catch (Exception e) { log.debug("[PREVIEW] Redis ring 拉取失败: {}", e.getMessage()); }
        // 叠加内存 ring
        java.util.Deque<Event> ring = RING.get(jobId);
        if (ring != null) history.addAll(ring);
        // 去重 (同 step) — 优先 Redis (最新), 跳过内存里的同 step
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            Event e = history.get(i);
            if (e == null) continue;
            int key = (e.step() << 16) ^ (e.type() == null ? 0 : e.type().ordinal());
            if (seen.contains(key)) { history.remove(i); continue; }
            seen.add(key);
        }
        // 推给新订阅者
        for (Event e : history) {
            try { listener.accept(e); } catch (Exception ignore) {}
        }
        return () -> {
            List<Consumer<Event>> ls = LISTENERS.get(jobId);
            if (ls != null) ls.remove(listener);
            log.debug("[PREVIEW] unsubscribe job={} (now {} listeners)", jobId, ls == null ? 0 : ls.size());
        };
    }

    public static boolean hasListeners(String jobId) {
        List<Consumer<Event>> ls = LISTENERS.get(jobId);
        return ls != null && !ls.isEmpty();
    }

    /** Push an event to every listener of {@code jobId}. */
    public static void publish(String jobId, Event event) {
        // 内存 ring (原样)
        java.util.Deque<Event> ring = RING.computeIfAbsent(jobId,
                k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        ring.addLast(event);
        while (ring.size() > RING_SIZE) ring.pollFirst();
        // ★ Redis ring (重启后 late subscriber 也能看到)
        try {
            String json = OM.writeValueAsString(event);
            redisPush(ringKey(jobId), json, RING_TTL);
        } catch (Exception e) { log.debug("[PREVIEW] Redis ring 写入失败: {}", e.getMessage()); }
        // 推给当前所有订阅者
        List<Consumer<Event>> ls = LISTENERS.get(jobId);
        if (ls == null || ls.isEmpty()) return;
        for (Consumer<Event> l : ls) {
            try {
                l.accept(event);
            } catch (Exception e) {
                log.warn("[PREVIEW] listener threw: {}", e.getMessage());
            }
        }
    }

    /** Drop all listeners for a finished job. */
    public static void clear(String jobId) {
        LISTENERS.remove(jobId);
    }
}
