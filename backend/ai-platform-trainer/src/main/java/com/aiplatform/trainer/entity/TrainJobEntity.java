package com.aiplatform.trainer.entity;

import com.aiplatform.starter.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 训练任务 DB 实体 (替代 ConcurrentHashMap 内存版).
 *
 * <p>jobCode 是前端用的 ID (8 位 UUID, 跟老 JobState.jobId 对齐).</p>
 *
 * <p>实时状态 (SSE 流) 还在内存 (ConcurrentHashMap), 这里只存历史/快照.
 * 关键状态变更 (status/progress/loss/outputPath) 同步写到这里.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_train_job")
public class TrainJobEntity extends BaseEntity {

    /** 跟老 JobState.jobId 对齐 — 8位 UUID. */
    private String jobCode;

    /** 关联模型编码 (如 minigpt-zh-v1). */
    private String modelCode;

    /** 关联数据集 ID. */
    private Long datasetId;

    /** 算法/模型类型 (minigpt/lstm/transformer). */
    private String algorithm;

    /** epochs. */
    private Integer epochs;

    /** batch_size. */
    private Integer batchSize;

    /** learning_rate. */
    private Double learningRate;

    /** queued / running / succeeded / failed. */
    private String status;

    /** JSON: 完整 Config 快照 (nLayer/nHead/nEmbd/.../guard). */
    private String config;

    /** 语料路径. */
    private String logPath;

    /** 输出目录. */
    private String outputPath;

    /** JSON: metrics (finalLoss/iters/factualSupport/...). */
    private String metrics;

    /** 进度 0-100. */
    private Integer progress;

    /** 错误信息. */
    private String errorMessage;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}