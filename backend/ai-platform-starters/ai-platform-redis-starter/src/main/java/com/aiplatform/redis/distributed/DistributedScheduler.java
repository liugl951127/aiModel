package com.aiplatform.redis.distributed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式调度器 (基于 Redisson 锁 + leader 选举).
 * <p>集群中只有一个节点执行定时任务, 避免重复执行.</p>
 *
 * <h3>用法</h3>
 * <pre>
 * &#64;Scheduled(cron = "0 0 * * * ?")
 * public void hourlyJob() {
 *     distributedScheduler.runAsLeader("job:hourly:cleanup", 300, () -> {
 *         // 只有 leader 节点执行
 *         cleanupOldData();
 *     });
 * }
 * </pre>
 */
@Slf4j
public class DistributedScheduler {

    private final DistributedLock lock;
    private final String nodeId;

    public DistributedScheduler(DistributedLock lock) {
        this.lock = lock;
        this.nodeId = UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 只让 leader 节点执行任务.
     *
     * @param jobName  任务名 (全集群唯一)
     * @param maxSec   最大执行秒数
     * @param task     任务
     * @return true 本节点是 leader 并执行了
     */
    public boolean runAsLeader(String jobName, int maxSec, Runnable task) {
        String key = "scheduler:lock:" + jobName;
        boolean got = lock.tryLock(key, 0, maxSec);
        if (!got) {
            log.debug("[Scheduler] skip {} (另一节点执行中)", jobName);
            return false;
        }
        try {
            log.info("[Scheduler] {} leader={} start", jobName, nodeId);
            long t0 = System.currentTimeMillis();
            task.run();
            long dt = System.currentTimeMillis() - t0;
            log.info("[Scheduler] {} done in {}ms", jobName, dt);
            return true;
        } catch (Exception e) {
            log.error("[Scheduler] {} failed: {}", jobName, e.getMessage(), e);
            return false;
        }
    }

    public <T> T runAsLeader(String jobName, int maxSec, Supplier<T> task) {
        String key = "scheduler:lock:" + jobName;
        boolean got = lock.tryLock(key, 0, maxSec);
        if (!got) return null;
        try {
            return task.get();
        } finally {
            try {
                org.redisson.api.RLock l = lock.lock(key);
                if (l.isHeldByCurrentThread()) l.unlock();
            } catch (Exception ignore) {}
        }
    }

    public String getNodeId() { return nodeId; }
}
