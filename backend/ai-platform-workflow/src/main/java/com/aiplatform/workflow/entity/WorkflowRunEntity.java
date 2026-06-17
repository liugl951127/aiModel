package com.aiplatform.workflow.entity;

import com.aiplatform.starter.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工作流执行实例 (DB 化, 重启服务不丢).
 *
 * <p>对应 workflow_run 表. runId 跟老 WorkflowRun.runId 对齐.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_run")
public class WorkflowRunEntity extends BaseEntity {

    /** runId (UUID, 跟内存 WorkflowRun.runId 一致). */
    private String runId;

    /** 关联的 workflow_spec.id. */
    private Long specId;

    /** spec 名 (冗余, 历史快照). */
    private String specName;

    /** PENDING / RUNNING / SUCCEEDED / FAILED. */
    private String status;

    /** 0-100. */
    private Integer progress;

    /** 当前 step 名 (用户看). */
    private String currentStep;

    /** 输入 JSON. */
    private String input;

    /** 输出 JSON (最后节点 result). */
    private String output;

    /** 失败节点 ID. */
    private String failedNodeId;

    /** 失败节点名 (冗余). */
    private String failedNodeName;

    /** 失败原因. */
    private String failedReason;

    /** 耗时 ms. */
    private Long durationMs;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}