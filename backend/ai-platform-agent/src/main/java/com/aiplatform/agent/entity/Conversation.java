package com.aiplatform.agent.entity;

import com.aiplatform.starter.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_conversation")
public class Conversation extends BaseEntity {
    private String sessionId;
    private Long agentId;
    private String title;
    private String summary;
    private Integer status;
}
