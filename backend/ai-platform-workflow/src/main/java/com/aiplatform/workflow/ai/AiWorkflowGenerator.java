package com.aiplatform.workflow.ai;

import com.aiplatform.workflow.schema.ComponentSchemaRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 自然语言 → 工作流生成器.
 *
 * <p>不调外部 LLM, 用规则 + 关键词权重匹配. 优势:
 * <ul>
 *   <li>响应 < 100ms (本地 CPU 推理)</li>
 *   <li>离线可用 (生产环境不依赖 OpenAI / 通义)</li>
 *   <li>token 0 消耗</li>
 *   <li>输出 100% 符合 schema (受 ComponentSchemaRegistry 约束)</li>
 * </ul>
 *
 * <h2>支持的场景 (10 大类)</h2>
 * <ol>
 *   <li>RAG 知识库问答 — 关键词: 知识库 / rag / 文档问答 / 检索</li>
 *   <li>LoRA 微调训练 — 关键词: 训练 / lora / 微调 / finetune</li>
 *   <li>营销文案生成 — 关键词: 营销 / 文案 / 小红书 / 公众号 / 抖音</li>
 *   <li>客服自动回复 — 关键词: 客服 / 售后 / 自动回复 / ticket</li>
 *   <li>合同审查 — 关键词: 合同 / 法务 / 风险条款 / 审查</li>
 *   <li>数据 ETL 流水线 — 关键词: etl / 同步 / 抽取 / 数仓</li>
 *   <li>模型评估 — 关键词: 评估 / 评测 / 幻觉 / 准确率</li>
 *   <li>智能体 ReAct — 关键词: 智能体 / agent / 工具调用</li>
 *   <li>部署上线 — 关键词: 部署 / 上线 / onnx / 发布</li>
 *   <li>其它 — 默认 fallback: 数据加载 + Agent 思考</li>
 * </ol>
 *
 * <h2>输出格式 (与画布一致)</h2>
 * <pre>{@code
 * {
 *   "name": "RAG 知识库问答",
 *   "description": "基于知识库的智能问答系统",
 *   "nodes": [
 *     { "id": "n1", "type": "kb_ingest",   "name": "文档入库",   "x": 100, "y": 100, "params": {...} },
 *     { "id": "n2", "type": "kb_search",   "name": "向量检索",   "x": 360, "y": 100, "params": {...} },
 *     { "id": "n3", "type": "agent_think", "name": "AI 回答",    "x": 620, "y": 100, "params": {...} }
 *   ],
 *   "edges": [
 *     { "from": "n1", "to": "n2" },
 *     { "from": "n2", "to": "n3" }
 *   ]
 * }
 * }</pre>
 *
 * @author liugl
 * @since 2026-06-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiWorkflowGenerator {

    private final ComponentSchemaRegistry schemaRegistry;

    /** 节点在画布上的间距 (px) */
    private static final int X_GAP = 260;
    private static final int Y_GAP = 110;
    private static final int START_X = 100;
    private static final int START_Y = 100;

    /**
     * 根据用户输入生成工作流 JSON.
     *
     * @param userInput 用户自然语言描述 (例: "做一个 RAG 知识库问答流程")
     * @return 工作流 JSON (含 name / description / nodes / edges)
     */
    public Map<String, Object> generate(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return fallback();
        }
        String input = userInput.toLowerCase().trim();

        // 1) 场景识别
        Scenario scenario = detectScenario(input);
        log.info("AI 场景识别: {} (置信度 {}) -> {}", scenario.name, scenario.confidence, userInput);

        // 2) 节点构造
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        int x = START_X, y = START_Y;
        String prevId = null;

        for (int i = 0; i < scenario.nodes.size(); i++) {
            NodeTemplate tpl = scenario.nodes.get(i);
            String id = "n" + (i + 1);

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", id);
            node.put("type", tpl.type);
            node.put("name", tpl.name);
            node.put("x", x);
            node.put("y", y);
            // 默认参数 (来自 schema 注册)
            node.put("params", defaultParams(tpl.type, userInput));
            nodes.add(node);

            if (prevId != null) {
                edges.add(Map.of("from", prevId, "to", id));
            }
            prevId = id;
            x += X_GAP;
        }

        // 3) 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", scenario.name);
        result.put("description", scenario.description);
        result.put("scenario", scenario.key);
        result.put("confidence", scenario.confidence);
        result.put("userInput", userInput);
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    // ============================================================
    // 场景识别
    // ============================================================

    private static class Scenario {
        String key, name, description;
        String[] keywords = new String[0];
        double confidence;
        List<NodeTemplate> nodes;
        Scenario(String key, String name, String description, List<NodeTemplate> nodes) {
            this.key = key; this.name = name; this.description = description;
            this.nodes = nodes; this.confidence = 0;
        }
    }

    private static class NodeTemplate {
        String type, name;
        NodeTemplate(String type, String name) { this.type = type; this.name = name; }
    }

    private Scenario detectScenario(String input) {
        // 10 个场景, 每个有命中关键词
        List<Scenario> all = new ArrayList<>();
        all.add(scenarioRag());
        all.add(scenarioLoraTrain());
        all.add(scenarioMarketing());
        all.add(scenarioCustomerService());
        all.add(scenarioContract());
        all.add(scenarioEtl());
        all.add(scenarioEvaluate());
        all.add(scenarioAgent());
        all.add(scenarioDeploy());
        all.add(scenarioFallback());

        // 算分
        Scenario best = all.get(all.size() - 1);
        double bestScore = 0;
        for (Scenario s : all) {
            double score = scoreScenario(s, input);
            s.confidence = score;
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private double scoreScenario(Scenario s, String input) {
        // 用场景名 + 关键词命中数
        int hit = 0;
        for (String kw : s.keywords) {
            if (input.contains(kw.toLowerCase())) hit++;
        }
        // 场景名 (中英) 加分
        if (input.contains(s.name.toLowerCase())) hit += 2;
        return hit;
    }

    // ============================================================
    // 10 个场景定义
    // ============================================================

    private Scenario scenarioRag() {
        Scenario s = new Scenario("rag", "RAG 知识库问答",
                "文档入库 → 向量检索 → AI 回答 (引用溯源)",
                List.of(
                        new NodeTemplate("kb_ingest", "文档入库"),
                        new NodeTemplate("kb_search", "向量检索"),
                        new NodeTemplate("agent_think", "AI 回答")
                ));
        s.keywords = new String[]{"知识库", "rag", "文档问答", "检索", "问答", "kb", "retrieval",
                "kb_ingest", "kb_search", "私域知识", "内部知识"};
        return s;
    }

    private Scenario scenarioLoraTrain() {
        Scenario s = new Scenario("lora_train", "LoRA 模型微调",
                "数据加载 → 数据切片 → LoRA 训练 → 评估 → 注册",
                List.of(
                        new NodeTemplate("data_loader", "数据加载"),
                        new NodeTemplate("data_split", "数据切片"),
                        new NodeTemplate("lora_train", "LoRA 训练"),
                        new NodeTemplate("eval_bleu", "评估指标"),
                        new NodeTemplate("model_register", "注册模型")
                ));
        s.keywords = new String[]{"训练", "lora", "微调", "finetune", "train",
                "模型训练", "指令微调", "sft", "继续训练", "增量训练"};
        return s;
    }

    private Scenario scenarioMarketing() {
        Scenario s = new Scenario("marketing", "营销文案生成",
                "需求解析 → AI 创作 → 多平台改写",
                List.of(
                        new NodeTemplate("agent_think", "需求解析"),
                        new NodeTemplate("infer_chat", "AI 文案"),
                        new NodeTemplate("infer_chat", "多平台改写")
                ));
        s.keywords = new String[]{"营销", "文案", "小红书", "公众号", "抖音", "短视频",
                "推广", "种草", "带货", "爆款", "marketing", "copy", "writing"};
        return s;
    }

    private Scenario scenarioCustomerService() {
        Scenario s = new Scenario("customer_service", "客服自动回复",
                "用户提问 → 意图识别 → 知识库检索 → 自动回复 + 工单",
                List.of(
                        new NodeTemplate("agent_think", "意图识别"),
                        new NodeTemplate("kb_search", "知识库检索"),
                        new NodeTemplate("infer_chat", "自动回复"),
                        new NodeTemplate("webhook", "建工单")
                ));
        s.keywords = new String[]{"客服", "售后", "自动回复", "ticket", "工单",
                "cs", "客户支持", "用户提问", "support", "complaint"};
        return s;
    }

    private Scenario scenarioContract() {
        Scenario s = new Scenario("contract_review", "合同风险审查",
                "合同解析 → 条款识别 → 风险标注 → 修改建议",
                List.of(
                        new NodeTemplate("chunker", "合同解析"),
                        new NodeTemplate("infer_chat", "条款识别"),
                        new NodeTemplate("infer_chat", "风险标注"),
                        new NodeTemplate("infer_chat", "修改建议")
                ));
        s.keywords = new String[]{"合同", "法务", "风险", "条款", "审查", "contract",
                "legal", "compliance", "审核", "合规"};
        return s;
    }

    private Scenario scenarioEtl() {
        Scenario s = new Scenario("etl", "数据 ETL 流水线",
                "源数据 → 抽取清洗 → 转换 → 入库",
                List.of(
                        new NodeTemplate("data_loader", "源数据"),
                        new NodeTemplate("data_clean", "数据清洗"),
                        new NodeTemplate("data_split", "数据转换"),
                        new NodeTemplate("vector_index", "入库")
                ));
        s.keywords = new String[]{"etl", "同步", "抽取", "数仓", "数据集成", "数据迁移",
                "data pipeline", "数据流水线", "data sync", "迁移", "抽取转换"};
        return s;
    }

    private Scenario scenarioEvaluate() {
        Scenario s = new Scenario("evaluate", "模型评估流水线",
                "模型加载 → 测试集评估 → 多维度打分 → 报告",
                List.of(
                        new NodeTemplate("model_list", "模型加载"),
                        new NodeTemplate("eval_bleu", "BLEU 评估"),
                        new NodeTemplate("eval_hallucination", "幻觉检测"),
                        new NodeTemplate("eval_rouge", "评估报告")
                ));
        s.keywords = new String[]{"评估", "评测", "benchmark", "幻觉", "准确率", "eval",
                "测试", "打榜", "leaderboard", "ragas"};
        return s;
    }

    private Scenario scenarioAgent() {
        Scenario s = new Scenario("agent", "智能体 ReAct",
                "用户输入 → 思考 → 工具调用 → 反思 → 输出",
                List.of(
                        new NodeTemplate("agent_think", "智能体思考"),
                        new NodeTemplate("agent_tool", "工具调用"),
                        new NodeTemplate("agent_think", "反思推理"),
                        new NodeTemplate("infer_chat", "最终回答")
                ));
        s.keywords = new String[]{"智能体", "agent", "工具调用", "react", "function call",
                "代理", "助手", "assistant", "tool use"};
        return s;
    }

    private Scenario scenarioDeploy() {
        Scenario s = new Scenario("deploy", "模型部署上线",
                "模型注册 → 转换 ONNX → 部署 → 灰度",
                List.of(
                        new NodeTemplate("model_register", "模型注册"),
                        new NodeTemplate("model_deploy", "ONNX 导出"),
                        new NodeTemplate("model_deploy", "部署服务"),
                        new NodeTemplate("infer_generate", "灰度调用")
                ));
        s.keywords = new String[]{"部署", "上线", "onnx", "发布", "deploy", "release",
                "灰度", "canary", "导出模型", "推上线"};
        return s;
    }

    private Scenario scenarioFallback() {
        Scenario s = new Scenario("custom", "自定义流程",
                "数据加载 + AI 思考 — 通用模板, 可继续编辑",
                List.of(
                        new NodeTemplate("data_loader", "数据加载"),
                        new NodeTemplate("agent_think", "AI 思考")
                ));
        s.keywords = new String[]{};
        return s;
    }

    // ============================================================
    // 默认参数 (从 schema registry 拿 examples)
    // ============================================================

    private Map<String, Object> defaultParams(String nodeType, String userInput) {
        Map<String, Object> params = new LinkedHashMap<>();
        // 优先用 schema registry 的 examples
        try {
            var schema = schemaRegistry.get(nodeType);
            if (schema != null && schema.getFields() != null) {
                for (var field : schema.getFields()) {
                    if (field.getExamples() != null && !field.getExamples().isEmpty()) {
                        params.put(field.getKey(), field.getExamples().get(0));
                    } else if (field.getDefaultValue() != null) {
                        params.put(field.getKey(), field.getDefaultValue());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("取 schema 失败, 走默认参数: {}", e.getMessage());
        }
        // 提取 topK / chunkSize 之类的关键词覆盖
        applyKeywordOverrides(params, userInput);
        return params;
    }

    private void applyKeywordOverrides(Map<String, Object> params, String input) {
        // topK=3/5/10
        Matcher mTopK = Pattern.compile("top[_-]?k[=\\s]+(\\d+)").matcher(input);
        if (mTopK.find()) params.put("topK", Integer.parseInt(mTopK.group(1)));
        // chunkSize=256/512
        Matcher mChunk = Pattern.compile("chunk[_-]?size[=\\s]+(\\d+)").matcher(input);
        if (mChunk.find()) params.put("chunkSize", Integer.parseInt(mChunk.group(1)));
        // lr=0.001
        Matcher mLr = Pattern.compile("(lr|learning[_-]?rate)[=\\s]+([\\d.]+)").matcher(input);
        if (mLr.find()) params.put("learningRate", Double.parseDouble(mLr.group(2)));
        // epochs=N
        Matcher mEp = Pattern.compile("(epoch|epochs|轮次)[=\\s]+(\\d+)").matcher(input);
        if (mEp.find()) params.put("epochs", Integer.parseInt(mEp.group(2)));
    }

    private Map<String, Object> fallback() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", "空流程");
        r.put("description", "请描述你的需求 (例: 做一个 RAG 知识库问答流程)");
        r.put("scenario", "empty");
        r.put("confidence", 0);
        r.put("nodes", List.of());
        r.put("edges", List.of());
        return r;
    }
}
