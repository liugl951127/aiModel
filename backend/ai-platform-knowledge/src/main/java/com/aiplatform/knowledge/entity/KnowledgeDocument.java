package com.aiplatform.knowledge.entity;

import com.aiplatform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KnowledgeDocument extends BaseEntity {
    private Long kbId;
    private String docCode;
    private String docName;
    private String docType;
    private Long sizeBytes;
    private String storagePath;
    private Integer status;
    private Integer chunkCount;
    private String errorMessage;
}
