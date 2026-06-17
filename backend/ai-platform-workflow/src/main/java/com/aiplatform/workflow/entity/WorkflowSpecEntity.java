package com.aiplatform.workflow.entity;

import com.aiplatform.starter.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流定义 (DB 持久化, 替代老 ConcurrentHashMap 内存版).
 *
 * <p>specJson 是前端发来的完整 JSON: {nodes, edges, viewport, ...}.
 * 我们用 TEXT 字段保存, 反序列化时直接转 Map/List, 不强制 schema.</p>
 *
 * <p>前端前端兼容: 旧版发 {name, steps:[{type,name,dependsOn,params}]} 也保留
 * (WorkflowEngine 仍按 steps 跑). 新版优先 specJson.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_spec")
public class WorkflowSpecEntity extends BaseEntity {

    /** 用户给的名字 (e.g. "LoRA 训练流程"). */
    private String name;

    /** 作者 username (X-Username 头). */
    private String author;

    /** 描述. */
    private String description;

    /** 前端 JSON 全量 (nodes + edges + viewport). */
    private String specJson;

    /** 节点数量 (冗余, 列表展示快). */
    private Integer nodeCount;

    /** 边数量. */
    private Integer edgeCount;

    /** 运行次数. */
    private Integer runCount;

    /** 最近一次运行时间. */
    private java.time.LocalDateTime lastRunAt;
}