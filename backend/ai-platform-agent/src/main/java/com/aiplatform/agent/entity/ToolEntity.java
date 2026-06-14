package com.aiplatform.agent.entity;

import com.aiplatform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_tool")
public class ToolEntity extends BaseEntity {
    private String toolCode;
    private String toolName;
    private String toolType;
    private String description;
    private String parameters;
    private String endpoint;
    private String handler;
    private Integer status;
}
