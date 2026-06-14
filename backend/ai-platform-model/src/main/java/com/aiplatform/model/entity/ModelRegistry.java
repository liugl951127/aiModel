package com.aiplatform.model.entity;

import com.aiplatform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_registry")
public class ModelRegistry extends BaseEntity {

    private String modelCode;
    private String modelName;
    private String modelType;
    private String baseModel;
    private String description;
    private String tags;
    private String framework;
    private Long parameterCount;
    private String contextLength;
    private String language;
    private String status;
    private String version;
    private String storagePath;
    private String exportFormat;
    private String onnxPath;
    private String tokenizerPath;
    private LocalDateTime trainingStartedAt;
    private LocalDateTime trainingFinishedAt;
    private String metrics;
}
