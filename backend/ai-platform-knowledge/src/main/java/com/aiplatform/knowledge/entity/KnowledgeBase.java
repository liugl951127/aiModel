package com.aiplatform.knowledge.entity;

import com.aiplatform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_base")
public class KnowledgeBase extends BaseEntity {
    private String kbCode;
    private String kbName;
    private String description;
    private String indexName;
    private Integer status;
    private String embeddingModel;
}
