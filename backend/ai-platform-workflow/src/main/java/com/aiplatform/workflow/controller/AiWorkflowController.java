package com.aiplatform.workflow.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.workflow.ai.AiWorkflowGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 工作流生成控制器.
 *
 * <p>用户输入自然语言, 后端根据关键词识别场景, 输出工作流 JSON 节点 + 边.
 * 前端拿到结果后直接铺到画布上.</p>
 *
 * <h2>接口</h2>
 * <ul>
 *   <li>{@code POST /api/workflow/ai-generate}  主体接口</li>
 *   <li>{@code GET  /api/workflow/ai-scenarios}  列出 10 个支持场景 (前端预设用)</li>
 * </ul>
 *
 * @author liugl
 * @since 2026-06-17
 */
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class AiWorkflowController {

    private final AiWorkflowGenerator generator;

    /**
     * 自然语言 → 工作流 JSON.
     * <p>请求: {@code { "input": "做一个 RAG 知识库问答流程" }}</p>
     * <p>响应: 完整的工作流定义 (name/description/nodes/edges)</p>
     */
    @PostMapping("/ai-generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, String> body) {
        String input = body == null ? "" : body.getOrDefault("input", "");
        Map<String, Object> wf = generator.generate(input);
        return Result.success(wf);
    }

    /**
     * 多轮修改: 在现有画布上根据追加需求修改.
     * <p>请求: {@code { "input": "多 3 个评估节点", "current": {...当前画布...} }}</p>
     * <p>响应: 新的画布 (含 action 字段: replace/add_node/delete_node/update_params)</p>
     */
    @PostMapping("/ai-modify")
    public Result<Map<String, Object>> modify(@RequestBody Map<String, Object> body) {
        String input = body == null ? "" : String.valueOf(body.getOrDefault("input", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> current = (Map<String, Object>) body.get("current");
        Map<String, Object> wf = generator.generate(input, current);
        return Result.success(wf);
    }

    /**
     * 列出 10 个支持场景 (给前端预设按钮用).
     */
    @GetMapping("/ai-scenarios")
    public Result<List<Map<String, Object>>> scenarios() {
        List<Map<String, Object>> r = List.of(
                Map.of("key", "rag", "name", "RAG 知识库问答", "icon", "📚",
                        "input", "做一个 RAG 知识库问答, 用 BGE 中文嵌入",
                        "desc", "文档入库 → 向量检索 → AI 回答"),
                Map.of("key", "lora_train", "name", "LoRA 模型微调", "icon", "⚙️",
                        "input", "训练个营销文案的 LoRA, epochs=3",
                        "desc", "数据加载 → 切片 → LoRA 训练 → 评估 → 注册"),
                Map.of("key", "marketing", "name", "营销文案生成", "icon", "✍️",
                        "input", "写小红书/公众号/抖音 3 平台营销文案",
                        "desc", "需求解析 → AI 创作 → 多平台改写"),
                Map.of("key", "customer_service", "name", "客服自动回复", "icon", "🎧",
                        "input", "做一个客服自动回复 + 工单流程",
                        "desc", "意图识别 → 检索 → 回复 + 工单"),
                Map.of("key", "contract_review", "name", "合同风险审查", "icon", "📜",
                        "input", "审查合同风险条款, 给出修改建议",
                        "desc", "解析 → 识别 → 标注 → 建议"),
                Map.of("key", "etl", "name", "数据 ETL 流水线", "icon", "🔄",
                        "input", "把 MySQL 数据 ETL 到 ClickHouse, 每天同步",
                        "desc", "源数据 → 清洗 → 转换 → 入库"),
                Map.of("key", "evaluate", "name", "模型评估流水线", "icon", "🧪",
                        "input", "评估 RAG 系统的幻觉率和准确率",
                        "desc", "模型 → BLEU → 幻觉 → 报告"),
                Map.of("key", "agent", "name", "智能体 ReAct", "icon", "🤖",
                        "input", "做一个能调工具的智能体",
                        "desc", "思考 → 工具调用 → 反思 → 输出"),
                Map.of("key", "deploy", "name", "模型部署上线", "icon", "🚀",
                        "input", "把模型部署上线, 灰度 10%",
                        "desc", "注册 → ONNX → 部署 → 灰度")
        );
        return Result.success(r);
    }
}
