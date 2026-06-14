package com.aiplatform.agent.entity;

import com.aiplatform.starter.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_agent")
public class AgentEntity extends BaseEntity {
    private String agentCode;
    private String agentName;
    private String agentType;
    private String description;
    private String avatar;
    private String systemPrompt;
    private String tools;
    private Long modelId;
    private String modelCode;
    private Double temperature;
    private Integer maxSteps;
    private Integer status;
    private String tags;
}
