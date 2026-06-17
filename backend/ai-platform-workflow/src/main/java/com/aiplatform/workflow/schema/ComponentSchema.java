package com.aiplatform.workflow.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组件参数 schema. 一个组件 (节点类型) 对应一个 schema,
 * 描述组件的所有可配置参数 + 推荐值 + 描述.
 *
 * <p>用于: 前端节点配置 dialog 自动生成表单 + AI 智能推荐.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComponentSchema {

    /** 节点 ID (跟 NodeExecutor 的 case 一致, e.g. "train_lora") */
    private String id;
    /** 节点显示名 */
    private String name;
    /** 分组 (训练/评估/...) */
    private String group;
    /** 描述 */
    private String description;
    /** 调用哪个后端服务 (e.g. "trainer" / "knowledge") */
    private String backend;
    /** 关联模型 (e.g. "minigpt" / "BAAI/bge-small-zh-v1.5") */
    private String defaultModel;
    /** 节点字段 (参数) */
    private List<Field> fields;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Field {
        /** 字段名 (跟 NodeExecutor 内部 map key 一致) */
        private String key;
        /** 显示标签 */
        private String label;
        /** 类型: string | number | boolean | select | textarea */
        private String type;
        /** 默认值 */
        private Object defaultValue;
        /** 数字的 min / max / step */
        private Number min;
        private Number max;
        private Number step;
        /** select 选项 */
        private List<String> options;
        /** 推荐示例值 (用于 AI 智能推荐, 取第一个作为默认建议) */
        private List<String> examples;
        /** 字段描述 (tooltip 用) */
        private String description;
        /** 是否必填 */
        private boolean required;

        public Object getDefault() { return defaultValue; }
    }
}
