package com.aiplatform.redis.distributed;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁 (Redisson).
 * <p>支持: tryLock / lock / unlock / 带 Supplier 的快捷方法.</p>
 *
 * <h3>用法</h3>
 * <pre>
 * // 1) 简单获取释放
 * RLock lock = distributedLock.lock("order:create:" + orderId);
 * try {
 *     // 临界区
 * } finally {
 *     lock.unlock();
 * }
 *
 * // 2) 带超时
 * boolean ok = distributedLock.tryLock("seckill:item:" + id, 3, 10);
 *
 * // 3) 一行
 * String result = distributedLock.execute("key", 3, 10, () -> doStuff());
 * </pre>
 *
 * <p><b>Redis 不可用降级</b>: RedissonClient 为 null 时,
 * {@code tryLock} 返回 false, {@code execute} 直接执行 (失去分布式语义).
 * lock() 抛 IllegalStateException — 调用方应该用 tryLock 路径.</p>
 */
@Slf4j
public class DistributedLock {

    private final RedissonClient redisson;
    private final boolean degraded;

    public DistributedLock(RedissonClient redisson) {
        this.redisson = redisson;
        this.degraded = (redisson == null);
        if (degraded) {
            log.warn("[Lock] Redis 不可用, 分布式锁降级为单进程");
        }
    }

    public boolean isDegraded() {
        return degraded;
    }

    public RLock lock(String key) {
        if (degraded) {
            throw new IllegalStateException("Redis 不可用, 分布式锁不可用 (key=" + key + "), 请改用 tryLock 路径");
        }
        return redisson.getLock(key);
    }

    public boolean tryLock(String key, long waitSeconds, long leaseSeconds) {
        if (degraded) {
            log.debug("[Lock] Redis 不可用, tryLock 直接返回 false (key={})", key);
            return false;
        }
        RLock l = redisson.getLock(key);
        try {
            return l.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("[Lock] tryLock({}) Redis 异常降级: {}", key, e.getMessage());
            return false;
        }
    }

    public <T> T execute(String key, long waitSeconds, long leaseSeconds, Supplier<T> action) {
        if (degraded) {
            // 降级: 直接执行 (失去分布式语义, 业务可能重复执行)
            log.debug("[Lock] Redis 不可用, execute 直接执行 (key={})", key);
            return action.get();
        }
        RLock l = redisson.getLock(key);
        boolean got = false;
        try {
            got = l.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (!got) {
                throw new LockAcquireException("获取锁失败: " + key);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquireException("中断: " + key);
        } catch (LockAcquireException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Lock] execute({}) Redis 异常降级直接执行: {}", key, e.getMessage());
            return action.get();
        } finally {
            if (got && l != null) {
                try {
                    if (l.isHeldByCurrentThread()) {
                        l.unlock();
                    }
                } catch (Exception e) { log.warn("unlock failed: {}", e.getMessage()); }
            }
        }
    }

    public void executeVoid(String key, long waitSeconds, long leaseSeconds, Runnable action) {
        execute(key, waitSeconds, leaseSeconds, () -> {
            action.run();
            return null;
        });
    }

    public static class LockAcquireException extends RuntimeException {
        public LockAcquireException(String msg) { super(msg); }
    }
}
