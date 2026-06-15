package com.aiplatform.seata.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体调用日志。ReAct 任务每跑一次记一行，traceId 用于 ELK / Loki 聚合。
 * 数据库 schema：{@code agent_agent}.
 */
@Data
@TableName("agent_invoke_log")
public class AgentInvokeLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String traceId;
    private Long userId;
    private String agentCode;
    private String prompt;
    private String response;
    private Long tokens;
    private Integer status;          // 1=成功 0=失败
    private LocalDateTime createTime;
}
