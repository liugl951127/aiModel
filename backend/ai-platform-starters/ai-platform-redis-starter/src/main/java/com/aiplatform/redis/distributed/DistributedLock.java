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
 */
@Slf4j
public class DistributedLock {

    private final RedissonClient redisson;

    public DistributedLock(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public RLock lock(String key) {
        return redisson.getLock(key);
    }

    public boolean tryLock(String key, long waitSeconds, long leaseSeconds) {
        RLock l = redisson.getLock(key);
        try {
            return l.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public <T> T execute(String key, long waitSeconds, long leaseSeconds, Supplier<T> action) {
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
        } finally {
            if (got && l.isHeldByCurrentThread()) {
                try { l.unlock(); } catch (Exception e) { log.warn("unlock failed: {}", e.getMessage()); }
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
