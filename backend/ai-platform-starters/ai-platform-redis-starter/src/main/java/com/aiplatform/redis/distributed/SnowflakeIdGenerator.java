package com.aiplatform.redis.distributed;

import org.springframework.beans.factory.annotation.Value;

/**
 * 雪花 ID 生成器 (Twitter Snowflake).
 * <p>64 位: 1 符号 + 41 时间戳 + 10 机器 (5 datacenter + 5 worker) + 12 序列.</p>
 * <p>理论: 单机 4096/ms, 集群 1024 * 4096 = 419 万/ms.</p>
 *
 * <h3>使用</h3>
 * <pre>
 * &#64;Autowired SnowflakeIdGenerator idGen;
 * long id = idGen.nextId();  // "2024_xxxxxxx" 形式字符串
 * </pre>
 */
public class SnowflakeIdGenerator {

    // 起始时间戳: 2024-01-01
    private static final long START_STAMP = 1704038400000L;

    // 各部分 bit 数
    private static final long SEQUENCE_BIT = 12;
    private static final long MACHINE_BIT = 5;
    private static final long DATACENTER_BIT = 5;

    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);
    private static final long MAX_MACHINE = ~(-1L << MACHINE_BIT);
    private static final long MAX_DATACENTER = ~(-1L << DATACENTER_BIT);

    // 位移
    private static final long MACHINE_LEFT = SEQUENCE_BIT;
    private static final long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private static final long TIMESTAMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

    private final long datacenterId;
    private final long machineId;
    private long sequence = 0L;
    private long lastStamp = -1L;

    public SnowflakeIdGenerator(long datacenterId, long machineId) {
        if (datacenterId > MAX_DATACENTER || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 超出范围 [0, " + MAX_DATACENTER + "]");
        }
        if (machineId > MAX_MACHINE || machineId < 0) {
            throw new IllegalArgumentException("machineId 超出范围 [0, " + MAX_MACHINE + "]");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long currStamp = currentTimeMillis();
        if (currStamp < lastStamp) {
            // 时钟回拨: 等待 5ms 重试, 然后强制用 lastStamp
            try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            currStamp = currentTimeMillis();
        }
        if (currStamp == lastStamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                // 当前 ms 序列用完, 等下一 ms
                currStamp = nextMill();
            }
        } else {
            sequence = 0L;
        }
        lastStamp = currStamp;
        return (currStamp - START_STAMP) << TIMESTAMP_LEFT
                | datacenterId << DATACENTER_LEFT
                | machineId << MACHINE_LEFT
                | sequence;
    }

    /**
     * 字符串形式 ID (前 4 位年份, 后 16 位 16 进制).
     */
    public String nextIdStr() {
        long id = nextId();
        return String.valueOf(id);
    }

    private long nextMill() {
        long mill = currentTimeMillis();
        while (mill <= lastStamp) {
            mill = currentTimeMillis();
        }
        return mill;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 解析 ID 携带的时间戳.
     */
    public static long extractTimestamp(long id) {
        return (id >> TIMESTAMP_LEFT) + START_STAMP;
    }
}
