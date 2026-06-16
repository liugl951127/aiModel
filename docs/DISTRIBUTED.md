# 分布式能力文档

> 基于 Redis + Redisson 实现的企业级分布式能力集, 共 7 大能力 + 1 链路追踪.
> 所有能力均通过 `ai-platform-redis-starter` 自动装配, 业务代码 `@Autowired` 即可使用.

## 能力清单

| # | 能力 | 后端实现 | 典型场景 |
|---|---|---|---|
| 1 | 分布式锁 | Redisson Lock | 防重提交、库存扣减、订单创建互斥 |
| 2 | 雪花 ID | Snowflake (41+10+12 bit) | 训练 jobId、workflow runId、订单号 |
| 3 | 分布式限流 | Redis token bucket + Lua | API 防刷、登录爆破、QPS 配额 |
| 4 | 分布式幂等 | Redis SETNX + 缓存结果 | 表单防重、API 重试安全 |
| 5 | 分布式缓存 | Redis String + TTL | L1/L2 缓存、防击穿、null 兜底 |
| 6 | 事件总线 | Redis Pub/Sub | 跨服务异步事件 (model.deployed / workflow.completed) |
| 7 | 分布式调度 | Redisson Lock + leader 选举 | 集群定时任务分片, 只 leader 执行 |
| 8 | 链路追踪 | Micrometer TraceId 透传 | 全链路 traceId 跨服务追踪 |

## 自动装配

引入依赖:
```xml
<dependency>
    <groupId>com.aiplatform</groupId>
    <artifactId>ai-platform-redis-starter</artifactId>
</dependency>
```

业务代码直接 `@Autowired`:
```java
@Autowired DistributedLock lock;
@Autowired SnowflakeIdGenerator idGen;
@Autowired DistributedRateLimiter rateLimiter;
@Autowired DistributedIdempotency idempotency;
@Autowired DistributedCache cache;
@Autowired DistributedEventBus eventBus;
@Autowired DistributedScheduler scheduler;
```

## 1. 分布式锁

```java
// 1) 简单获取/释放
RLock lock = distributedLock.lock("order:create:" + orderId);
try {
    // 临界区
} finally {
    if (lock.isHeldByCurrentThread()) lock.unlock();
}

// 2) 带超时
boolean got = distributedLock.tryLock("seckill:item:" + id, 3, 10);
// 等 3s, 持锁 10s
if (!got) throw new BusinessException(429, "系统繁忙, 请重试");

// 3) 一行
String result = distributedLock.execute("key", 3, 10, () -> doStuff());
```

**特性**: Redisson 看门狗自动续期 (默认 30s, lease 秒数 * 1/3 续期), 防止业务未完成锁就过期.

## 2. 雪花 ID

```java
long id = idGen.nextId();  // 18-19 位 long
String str = idGen.nextIdStr();  // 字符串

// 解析时间戳
long ts = SnowflakeIdGenerator.extractTimestamp(id);
```

**特性**: 单机 4096/ms, 集群 419 万/ms. 时钟回拨自动等待 5ms 重试.

## 3. 分布式限流

```java
boolean allowed = rateLimiter.tryAcquire("user:login:u123", 5, 60);
// 每 60s 最多 5 次
if (!allowed) return Result.fail(429, "操作频繁, 请稍后再试");

// 限流状态
long current = rateLimiter.currentCount("user:login:u123");
rateLimiter.reset("user:login:u123");
```

**算法**: 滑动窗口 (Redis INCR + EXPIRE), Lua 原子执行, 失败降级放行.

## 4. 分布式幂等

```java
@PostMapping("/order")
public Result<String> createOrder(
    @RequestHeader("X-Idempotency-Key") String token,
    @RequestBody OrderDTO dto
) {
    String key = "idem:order:create:" + token;
    if (!idempotency.tryClaim(key, 30)) {
        String cached = idempotency.getResult(key);
        return Result.success(cached);  // 返回上次结果
    }
    String orderId = orderService.create(dto);
    idempotency.saveResult(key, orderId, 300);
    return Result.success(orderId);
}
```

**机制**: SETNX 占位 → 业务执行 → saveResult 缓存结果. 重复请求直接返回缓存.

## 5. 分布式缓存

```java
User user = cache.getOrLoad("user:" + id, 60, () -> userMapper.selectById(id));
// 首次慢查, 60s 内命中

// 主动失效
cache.evict("user:" + id);
cache.evictByPattern("user:*");
```

**特性**: 写 null 也缓存 (防止击穿). Redis 故障降级直接走 loader.

## 6. 分布式事件总线

```java
// 发送
eventBus.publish("model.deployed", Map.of("modelId", 1, "stage", "prod"));

// 订阅 (支持 * 通配符)
eventBus.subscribe("model.*", event -> {
    log.info("收到事件: {}", event);
    auditService.record(event);
});
```

**特性**: Redis Pub/Sub 实时推送, 通配符订阅, JSON 序列化.

## 7. 分布式调度 (Leader 选举)

```java
@Scheduled(cron = "0 0 * * * ?")
public void hourlyJob() {
    scheduler.runAsLeader("job:hourly:cleanup", 300, () -> {
        cleanupOldData();
    });
}
```

**机制**: 集群中只有抢到 Redisson 锁的节点是 leader, 执行任务. 其它节点跳过.

## 前端演示页

`/distributed` 页面提供 7 大能力的实时交互测试:
- 抢锁/释放
- 批量生成 ID (含时间戳解析)
- 滑动窗口限流 (可视化进度条)
- 幂等提交 (重复点看效果)
- 缓存 HIT/MISS
- 事件发布/订阅 (实时日志)
- Leader 选举 (节点 ID + 计数)

## 文件清单

```
backend/ai-platform-starters/ai-platform-redis-starter/
├── pom.xml                                                    # 加 redisson
├── src/main/resources/META-INF/spring/
│   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── src/main/java/com/aiplatform/redis/
    ├── autoconfigure/DistributedAutoConfiguration.java         # 自动装配
    └── distributed/                                            # 7 个分布式工具
        ├── DistributedLock.java
        ├── SnowflakeIdGenerator.java
        ├── DistributedRateLimiter.java
        ├── DistributedIdempotency.java
        ├── DistributedCache.java
        ├── DistributedEventBus.java
        └── DistributedScheduler.java

backend/ai-platform-system/src/main/java/com/aiplatform/system/controller/
└── DistributedController.java                                 # 演示端点 /api/distributed/*

frontend/src/views/Distributed.vue                             # 演示页
frontend/src/api/index.js                                      # distributedApi 模块
```

## 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/distributed/health | 健康检查 |
| POST | /api/distributed/lock/demo | 抢锁 |
| POST | /api/distributed/lock/release | 释放 |
| GET | /api/distributed/snowflake/next?n=5 | 生成 N 个 ID |
| POST | /api/distributed/ratelimiter/check | 限流检查 |
| POST | /api/distributed/ratelimiter/reset | 重置 |
| POST | /api/distributed/idempotency/submit | 幂等提交 |
| GET | /api/distributed/cache/get | 读缓存 |
| POST | /api/distributed/cache/evict | 清除 |
| POST | /api/distributed/event/publish | 发布事件 |
| POST | /api/distributed/event/subscribe | 订阅 |
| GET | /api/distributed/event/log | 事件日志 |
| POST | /api/distributed/scheduler/leader | 抢 leader |
| GET | /api/distributed/scheduler/info | 节点信息 |
