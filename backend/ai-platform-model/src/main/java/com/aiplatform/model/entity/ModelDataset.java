package com.aiplatform.model.entity;

import com.aiplatform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_dataset")
public class ModelDataset extends BaseEntity {
    private String datasetCode;
    private String datasetName;
    private String format;
    private Long sampleCount;
    private String language;
    private String description;
    private String storagePath;
    private String status;
}
