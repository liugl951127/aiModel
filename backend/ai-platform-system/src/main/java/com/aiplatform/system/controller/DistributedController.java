package com.aiplatform.system.controller;

import com.aiplatform.redis.distributed.*;
import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分布式能力演示控制器.
 * <p>前端 /distributed 页面调这里, 看 8 大分布式能力.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/distributed")
@RequiredArgsConstructor
public class DistributedController {

    private final DistributedLock lock;
    private final SnowflakeIdGenerator idGen;
    private final DistributedRateLimiter rateLimiter;
    private final DistributedIdempotency idempotency;
    private final DistributedCache cache;
    private final DistributedEventBus eventBus;
    private final DistributedScheduler scheduler;

    private final AtomicInteger concurrentCounter = new AtomicInteger(0);
    private final AtomicInteger idempotencyCounter = new AtomicInteger(0);
    private long leaderHits = 0;

    // ============== 1. 分布式锁 ==============
    /**
     * 演示分布式锁: 并发 5 次, 只有 1 次能拿到锁.
     * 模拟 "创建订单" 互斥场景.
     */
    @PostMapping("/lock/demo")
    public Result<Map<String, Object>> lockDemo(@RequestBody Map<String, Object> body) {
        String orderId = (String) body.getOrDefault("orderId", "order-" + UUID.randomUUID().toString().substring(0, 6));
        String key = "demo:lock:order:create:" + orderId;
        int waitMs = 2000, holdMs = 1;
        long t0 = System.currentTimeMillis();
        boolean got = lock.tryLock(key, waitMs / 1000, holdMs);
        long dt = System.currentTimeMillis() - t0;
        Map<String, Object> ret = new HashMap<>();
        ret.put("key", key);
        ret.put("acquired", got);
        ret.put("elapsedMs", dt);
        if (got) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            lock.lock(key);  // 取一个引用解锁用
        }
        ret.put("currentCount", concurrentCounter.incrementAndGet());
        return Result.success(ret);
    }

    @PostMapping("/lock/release")
    public Result<String> releaseLock(@RequestParam String key) {
        var l = lock.lock(key);
        if (l.isHeldByCurrentThread()) {
            l.unlock();
            return Result.success("released");
        }
        return Result.success("not held");
    }

    // ============== 2. 雪花 ID ==============
    @GetMapping("/snowflake/next")
    public Result<Map<String, Object>> nextId(@RequestParam(defaultValue = "5") int n) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            ids.add(idGen.nextIdStr());
        }
        long dt = System.currentTimeMillis() - t0;
        Map<String, Object> ret = new HashMap<>();
        ret.put("ids", ids);
        ret.put("count", n);
        ret.put("elapsedMs", dt);
        ret.put("qps", n * 1000.0 / Math.max(dt, 1));
        long firstId = Long.parseLong(ids.get(0));
        ret.put("firstTimestamp", SnowflakeIdGenerator.extractTimestamp(firstId));
        ret.put("firstHumanTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
            .format(new java.util.Date(SnowflakeIdGenerator.extractTimestamp(firstId))));
        return Result.success(ret);
    }

    // ============== 3. 分布式限流 ==============
    @PostMapping("/ratelimiter/check")
    public Result<Map<String, Object>> rateLimitCheck(@RequestBody Map<String, Object> body) {
        String key = (String) body.getOrDefault("key", "demo:rl:user1");
        int limit = body.get("limit") == null ? 5 : ((Number) body.get("limit")).intValue();
        int window = body.get("window") == null ? 60 : ((Number) body.get("window")).intValue();
        boolean allowed = rateLimiter.tryAcquire(key, limit, window);
        long current = rateLimiter.currentCount(key);
        Map<String, Object> ret = new HashMap<>();
        ret.put("key", key);
        ret.put("allowed", allowed);
        ret.put("current", current);
        ret.put("limit", limit);
        ret.put("windowSec", window);
        return Result.success(ret);
    }

    @PostMapping("/ratelimiter/reset")
    public Result<String> rateLimitReset(@RequestParam String key) {
        rateLimiter.reset(key);
        return Result.success("reset ok");
    }

    // ============== 4. 分布式幂等 ==============
    @PostMapping("/idempotency/submit")
    public Result<Map<String, Object>> idempotencySubmit(
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey,
        @RequestBody Map<String, Object> body
    ) {
        if (idemKey == null) idemKey = UUID.randomUUID().toString();
        String key = "demo:idem:order:" + idemKey;
        int ttl = 60;

        boolean first = idempotency.tryClaim(key, ttl);
        if (!first) {
            String cached = idempotency.getResult(key);
            int counter = idempotencyCounter.incrementAndGet();
            Map<String, Object> ret = new HashMap<>();
            ret.put("first", false);
            ret.put("duplicateCount", counter);
            ret.put("cachedResult", cached);
            ret.put("idempotencyKey", idemKey);
            return Result.success(ret);
        }
        // 首次: 模拟业务
        String orderId = "ORD-" + System.currentTimeMillis();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        String result = "订单创建成功: " + orderId;
        idempotency.saveResult(key, result, ttl);
        Map<String, Object> ret = new HashMap<>();
        ret.put("first", true);
        ret.put("orderId", orderId);
        ret.put("result", result);
        ret.put("idempotencyKey", idemKey);
        return Result.success(ret);
    }

    // ============== 5. 分布式缓存 ==============
    @GetMapping("/cache/get")
    public Result<Map<String, Object>> cacheGet(@RequestParam String key) {
        long t0 = System.currentTimeMillis();
        // loader 函数里模拟 500ms 慢查询
        String val = cache.getOrLoad(key, 30, () -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            return "loaded-" + System.currentTimeMillis();
        });
        long dt = System.currentTimeMillis() - t0;
        Map<String, Object> ret = new HashMap<>();
        ret.put("key", key);
        ret.put("value", val);
        ret.put("elapsedMs", dt);
        ret.put("hit", dt < 50);  // 快的就是 hit
        return Result.success(ret);
    }

    @PostMapping("/cache/evict")
    public Result<String> cacheEvict(@RequestParam String key) {
        cache.evict(key);
        return Result.success("evicted");
    }

    // ============== 6. 分布式事件总线 ==============
    private final java.util.List<Map<String, Object>> eventLog = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    @PostMapping("/event/publish")
    public Result<Map<String, Object>> eventPublish(@RequestBody Map<String, Object> body) {
        String topic = (String) body.getOrDefault("topic", "demo.event");
        Object payload = body.get("payload");
        eventBus.publish(topic, payload);
        Map<String, Object> ret = new HashMap<>();
        ret.put("topic", topic);
        ret.put("published", true);
        return Result.success(ret);
    }

    @PostMapping("/event/subscribe")
    public Result<String> eventSubscribe(@RequestParam String topic) {
        eventBus.subscribe(topic, event -> {
            synchronized (eventLog) {
                eventLog.add(event);
                if (eventLog.size() > 50) eventLog.remove(0);
            }
            log.info("[EventBus] received: {}", event);
        });
        return Result.success("subscribed: " + topic);
    }

    @GetMapping("/event/log")
    public Result<java.util.List<Map<String, Object>>> eventLog() {
        return Result.success(eventLog);
    }

    // ============== 7. 分布式调度 (leader 选举) ==============
    private long lastLeader = -1;
    private long leaderRuns = 0;

    @PostMapping("/scheduler/leader")
    public Result<Map<String, Object>> schedulerLeader() {
        boolean got = scheduler.runAsLeader("demo:scheduler:daily", 5, () -> {
            leaderHits++;
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        });
        Map<String, Object> ret = new HashMap<>();
        ret.put("isLeader", got);
        ret.put("nodeId", scheduler.getNodeId());
        ret.put("totalRuns", leaderHits);
        return Result.success(ret);
    }

    @GetMapping("/scheduler/info")
    public Result<Map<String, Object>> schedulerInfo() {
        Map<String, Object> ret = new HashMap<>();
        ret.put("nodeId", scheduler.getNodeId());
        ret.put("leaderRuns", leaderHits);
        return Result.success(ret);
    }

    // ============== 8. 综合 / 健康 ==============
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> ret = new HashMap<>();
        ret.put("status", "UP");
        ret.put("features", java.util.List.of(
            "DistributedLock", "SnowflakeId", "RateLimiter",
            "Idempotency", "Cache", "EventBus", "Scheduler"));
        ret.put("nodeId", scheduler.getNodeId());
        return Result.success(ret);
    }
}
