package com.aiplatform.agent.entity;

import com.aiplatform.starter.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多智能体流程编排案例实体。
 * <p>每个案例描述一个完整的多智能体协作场景：包含输入、若干智能体节点、
 * 节点间消息流、最终输出和 KPI。案例既可作为模板复用，也是项目成果
 * 展示的最小数据单元。</p>
 *
 * <h2>字段</h2>
 * <ul>
 *   <li>{@code caseKey} — 业务唯一 key，前端按它索引</li>
 *   <li>{@code title} / {@code summary} / {@code description} — 展示文案</li>
 *   <li>{@code domain} — 行业（marketing / legal / research / training / ...）</li>
 *   <li>{@code agentSpec} — JSON：智能体节点列表 + 工具 + 流程图</li>
 *   <li>{@code flowSpec} — JSON：可执行的 workflow 步骤</li>
 *   <li>{@code finalOutput} — JSON：完整产出（最终报告 / 训练数据 / 部署清单）</li>
 *   <li>{@code kpis} — JSON：效果指标（耗时 / 准确率 / 拒答率）</li>
 *   <li>{@code featured} — 是否首页推荐</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_multi_agent_case")
public class MultiAgentCase extends BaseEntity {
    private String caseKey;
    private String title;
    private String summary;
    private String description;
    private String domain;
    private String agentSpec;
    private String flowSpec;
    private String finalOutput;
    private String kpis;
    private Integer featured;
}
