package com.aiplatform.agent.entity;

import com.aiplatform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_message")
public class Message extends BaseEntity {
    private String sessionId;
    private String role;
    private String content;
    private String toolName;
    private String toolCall;
    private Integer step;
    private Integer status;
}
