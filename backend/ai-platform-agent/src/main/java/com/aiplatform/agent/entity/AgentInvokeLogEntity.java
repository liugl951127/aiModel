package com.aiplatform.agent.entity;

import com.aiplatform.starter.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 调用日志 (DB 化, 重启不丢).
 * 每次 /api/conversation/chat 调一次写一行, 含:
 *   - agentId / sessionId / userId
 *   - input / output 文本
 *   - tokens (in/out) / durationMs
 *   - 状态 (OK / ERROR) / 错误信息
 *   - toolCalls (JSON 数组) / knowledgeHits (命中数)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_invoke_log")
public class AgentInvokeLogEntity extends BaseEntity {

    private Long agentId;
    private String sessionId;
    private String userId;
    private String input;
    private String output;
    private Integer inputTokens;
    private Integer outputTokens;
    private Long durationMs;
    private String status;
    private String errorMessage;
    private String toolCalls;        // JSON
    private Integer knowledgeHits;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}