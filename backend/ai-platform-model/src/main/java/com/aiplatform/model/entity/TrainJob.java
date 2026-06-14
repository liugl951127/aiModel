package com.aiplatform.model.entity;

import com.aiplatform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_train_job")
public class TrainJob extends BaseEntity {
    private String jobCode;
    private Long modelId;
    private Long datasetId;
    private String algorithm;
    private Integer epochs;
    private Integer batchSize;
    private Double learningRate;
    private String status;
    private String config;
    private String logPath;
    private String outputPath;
    private String metrics;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer progress;
}
