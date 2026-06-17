package com.aiplatform.workflow.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.workflow.schema.ComponentSchema;
import com.aiplatform.workflow.schema.ComponentSchemaRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 组件参数 schema API — 前端 Workflow.vue 加载节点参数表单用.
 *
 * <ul>
 *   <li>{@code GET  /api/workflow/component-schemas} — 列出所有 32 节点 schema</li>
 *   <li>{@code GET  /api/workflow/component-schemas/{nodeId}} — 单个 schema</li>
 *   <li>{@code POST /api/workflow/component-schemas/{nodeId}/suggest} — AI 给参数推荐</li>
 * </ul>
 *
 * <p>schema 可在 {@link ComponentSchemaRegistry} 扩展, 也可通过 admin 后台覆盖 (TODO).
 * 当前 32 节点 schema 见 {@link ComponentSchemaRegistry#defaultSchemas()}.</p>
 */
@RestController
@RequestMapping("/api/workflow/component-schemas")
@RequiredArgsConstructor
public class ComponentSchemaController {

    private final ComponentSchemaRegistry registry;

    @GetMapping
    public Result<List<ComponentSchema>> list() {
        return Result.success(registry.all());
    }

    @GetMapping("/{nodeId}")
    public Result<ComponentSchema> get(@PathVariable String nodeId) {
        ComponentSchema s = registry.get(nodeId);
        if (s == null) {
            return Result.fail(404, "未找到组件 schema: " + nodeId);
        }
        return Result.success(s);
    }

    /**
     * AI 参数推荐: 用户输入部分参数, 后端基于 schema 给出建议.
     *
     * <p>当前简化版: 基于 schema 的 {@code examples} + 节点类型做规则建议.
     * 后续可对接大模型: 把 schema + user input 喂给 LLM, 拿智能推荐.</p>
     *
     * @param nodeId 节点类型
     * @param body   用户当前已填参数 {key: value, ...}
     * @return 每个参数的推荐值 + 建议理由
     */
    @PostMapping("/{nodeId}/suggest")
    public Result<Map<String, Object>> suggest(
            @PathVariable String nodeId,
            @RequestBody Map<String, Object> body) {
        ComponentSchema s = registry.get(nodeId);
        if (s == null) {
            return Result.fail(404, "未找到组件 schema: " + nodeId);
        }
        Map<String, Object> userInput = body == null ? Map.of() : body;

        // 规则版: 用 schema.examples + context 给推荐
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        java.util.List<Map<String, String>> suggestions = new java.util.ArrayList<>();
        for (ComponentSchema.Field f : s.getFields()) {
            String key = f.getKey();
            Object current = userInput.get(key);
            Map<String, String> sug = new java.util.LinkedHashMap<>();
            sug.put("key", key);
            sug.put("current", current == null ? "" : String.valueOf(current));
            // 推荐: schema.examples 里第一个, 或 default
            String recommended = f.getExamples() != null && !f.getExamples().isEmpty()
                    ? f.getExamples().get(0)
                    : String.valueOf(f.getDefault());
            sug.put("recommended", recommended);
            // 建议理由
            sug.put("reason", reasonFor(s.getId(), f));
            suggestions.add(sug);
        }
        result.put("nodeId", nodeId);
        result.put("suggestions", suggestions);
        result.put("summary", s.getDescription());
        return Result.success(result);
    }

    private String reasonFor(String nodeId, ComponentSchema.Field f) {
        // 简化: 按节点 + 字段名给一句话理由
        String k = f.getKey();
        if (nodeId.startsWith("train_")) {
            if (k.equals("lr")) return "学习率: 太大 loss 震荡, 太小收敛慢, 推荐 0.001";
            if (k.equals("maxIters")) return "迭代数: 太少欠拟合, 太多过拟合";
            if (k.equals("batchSize")) return "batch size 越大越稳, 但吃显存";
        }
        if (nodeId.startsWith("kb_") || nodeId.equals("infer_embed")) {
            if (k.equals("model")) return "BGE 中文小模型 512 维性价比高, 大模型 1024 维更准";
            if (k.equals("topK")) return "RAG topK 3-5 是甜点, 太多引入噪声";
            if (k.equals("chunkSize")) return "256 token 配合 32 overlap 适合大多数 QA 场景";
        }
        if (nodeId.startsWith("agent_")) {
            if (k.equals("maxSteps")) return "ReAct 5 步够用, 多了 LLM 容易跑偏";
        }
        if (nodeId.startsWith("infer_")) {
            if (k.equals("maxTokens")) return "60-100 token 适合对话, 长文可 500+";
        }
        return f.getDescription() != null ? f.getDescription() : "按 schema 推荐值";
    }
}
