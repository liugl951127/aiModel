package com.aiplatform.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分布式事务总开关.
 *
 * <p>在 application.yml 里设 {@code ai-platform.distributed-tx.enabled}, 跟 Nacos 联动.
 * 关闭时所有 @GlobalTransactional 自动走 @Transactional 本地事务兜底, 产品照样跑.</p>
 *
 * <p>默认: enabled=true (开启 seata, 失败降级到本地事务)</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-platform.distributed-tx")
public class DistributedTxProperties {

    /** 总开关 */
    private boolean enabled = true;
    /** seata TC 地址 (空 = 走本地事务) */
    private String seataServerAddr = "127.0.0.1:8091";
    /** 事务分组 */
    private String txServiceGroup = "default_tx_group";
    /** 超时 (ms) */
    private int timeoutMs = 60000;
    /** 自动降级: seata TC 不可用时, 降级为本地事务 */
    private boolean autoFallback = true;
}
