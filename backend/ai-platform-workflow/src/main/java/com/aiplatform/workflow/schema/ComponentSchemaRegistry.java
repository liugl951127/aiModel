package com.aiplatform.workflow.schema;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 32 节点 schema 注册表. 每个节点对应一个 {@link ComponentSchema}.
 *
 * <p>设计: 字段 (fields) 跟 {@code NodeExecutor} switch 里用的 map key 完全一致.
 * 也就是说, 节点执行时从 input.get("key") 取值, 这里给用户填的就是这个 key.</p>
 *
 * <p>前端 Workflow.vue 双击节点打开配置 dialog 时, 调
 * {@code GET /api/workflow/component-schemas/{nodeId}} 拿 schema 动态生成表单.</p>
 */
@Slf4j
@Component
public class ComponentSchemaRegistry {

    private final Map<String, ComponentSchema> schemas = new LinkedHashMap<>();

    public ComponentSchemaRegistry() {
        for (ComponentSchema s : defaultSchemas()) {
            schemas.put(s.getId(), s);
        }
        log.info("[SCHEMA] loaded {} component schemas", schemas.size());
    }

    public List<ComponentSchema> all() {
        return new ArrayList<>(schemas.values());
    }

    public ComponentSchema get(String id) {
        return schemas.get(id);
    }

    /**
     * 32 节点 schema 定义. 跟 NodeExecutor switch 32 case 一一对应.
     */
    private List<ComponentSchema> defaultSchemas() {
        List<ComponentSchema> list = new ArrayList<>();

        // ============== 数据准备 (4) ==============
        list.add(s("dataset_list", "数据集列表", "数据准备", "knowledge", "ES",
                "列出所有可用数据集, 用于下一步 data_loader",
                f("source", "数据源路径", "string", "/data/", null, null, null, null,
                        List.of("/data/", "/opt/data/", "/workspace/corpus/"), "数据根目录", true),
                f("format", "格式过滤", "select", "all", null, null, null,
                        List.of("all", "jsonl", "json", "csv", "txt"),
                        List.of("all", "jsonl"), "按扩展名过滤, all 不过滤", false)
        ));
        list.add(s("data_loader", "数据加载", "数据准备", "knowledge", null,
                "读取 jsonl/json/csv 原始语料",
                f("format", "文件格式", "select", "jsonl", null, null, null,
                        List.of("jsonl", "json", "csv", "txt"),
                        List.of("jsonl"), "jsonl 是大模型训练最常见格式", true),
                f("encoding", "编码", "select", "utf-8", null, null, null,
                        List.of("utf-8", "gbk"),
                        List.of("utf-8"), "默认 utf-8, 中文 windows 文件用 gbk", false),
                f("limit", "最大行数", "number", 10000, 1, 1000000, 1000,
                        null, List.of("1000", "10000"), "0 表示全部加载", false)
        ));
        list.add(s("data_clean", "数据清洗", "数据准备", "tool", null,
                "正则去噪 (HTML/URL/邮箱/特殊字符)",
                f("rules", "清洗规则", "textarea", "去除HTML/URL/邮箱; 保留中英文; 合并空白",
                        null, null, null, null,
                        List.of("去除HTML/URL/邮箱; 保留中英文", "去除HTML; 保留中英文+数字", "只保留中英文"),
                        "正则表达式或关键字描述", true),
                f("minLen", "最短长度", "number", 10, 1, 1000, 1,
                        null, List.of("10", "30"), "过滤太短无意义文本", false)
        ));
        list.add(s("data_split", "数据切分", "数据准备", "tool", null,
                "train/test 划分",
                f("ratio", "切分比例", "select", "8:2", null, null, null,
                        List.of("8:2", "9:1", "7:3", "6:4"),
                        List.of("8:2"), "经典 8:2 平衡, 大数据集 9:1 够用", true),
                f("shuffle", "是否打乱", "boolean", true, null, null, null, null,
                        List.of("true"), "保证 train/test 分布一致", false)
        ));

        // ============== 训练 (3) ==============
        list.add(s("train_lora", "LoRA 训练", "训练", "trainer", "minigpt",
                "轻量微调, 只调 adapter 参数, 显存省 90%",
                f("trainerId", "模型", "select", "minigpt", null, null, null,
                        List.of("minigpt", "minigpt2", "llama-mini"),
                        List.of("minigpt"), "minigpt 小巧实验, llama-mini 类 LLaMA", true),
                f("lr", "学习率", "number", 0.001, 0.0001, 0.01, 0.0001,
                        null, List.of("0.001", "0.0005"), "推荐 0.001, 太大 loss 震荡", true),
                f("maxIters", "迭代数", "number", 200, 10, 5000, 50,
                        null, List.of("200", "500"), "太少欠拟合, 太多过拟合", true),
                f("batchSize", "批大小", "number", 12, 1, 64, 1,
                        null, List.of("12", "8"), "越大越稳, 但吃显存", false)
        ));
        list.add(s("train_dpo", "DPO 训练", "训练", "trainer", "minigpt2",
                "偏好对齐训练, 需要 chosen/rejected pair 数据",
                f("trainerId", "模型", "select", "minigpt2", null, null, null,
                        List.of("minigpt2", "llama-mini"),
                        List.of("minigpt2"), "minigpt2 适合小数据 DPO", true),
                f("lr", "学习率", "number", 0.0005, 0.00001, 0.005, 0.0001,
                        null, List.of("0.0005"), "DPO 学习率要比 SFT 小一个量级", true),
                f("beta", "DPO beta", "number", 0.1, 0.01, 1.0, 0.05,
                        null, List.of("0.1", "0.3"), "越大越保守, 越小越激进", false)
        ));
        list.add(s("train_full", "全量微调", "训练", "trainer", "llama-mini",
                "全参数微调, 显存需求高, 效果上限高",
                f("trainerId", "模型", "select", "llama-mini", null, null, null,
                        List.of("llama-mini", "minigpt"),
                        List.of("llama-mini"), "LLaMA 风格模型适合全量微调", true),
                f("lr", "学习率", "number", 0.0003, 0.00005, 0.001, 0.0001,
                        null, List.of("0.0003"), "全量微调 lr 要比 LoRA 小", true),
                f("maxIters", "迭代数", "number", 500, 50, 5000, 100,
                        null, List.of("500", "1000"), "全量微调要更多迭代", true)
        ));

        // ============== 评估 (2) ==============
        list.add(s("eval_bleu", "BLEU 评估", "评估", "tool", null,
                "文本相似度评估, 跟参考答案对比",
                f("maxNgram", "最大 n-gram", "number", 4, 1, 6, 1,
                        null, List.of("4"), "经典 BLEU-4", false),
                f("reference", "参考答案", "string", "", null, null, null, null,
                        List.of(), "可选, 不填自动从上游取", false)
        ));
        list.add(s("eval_hallucination", "幻觉检测", "评估", "knowledge", "RAGAS",
                "RAGAS 风格幻觉评估, 给 factual/citation 分数",
                f("threshold", "幻觉阈值", "number", 0.7, 0.0, 1.0, 0.05,
                        null, List.of("0.7"), "≥ 0.7 视为高风险, 触发警报", true),
                f("model", "评估模型", "select", "BAAI/bge-small-zh-v1.5", null, null, null,
                        List.of("BAAI/bge-small-zh-v1.5", "BAAI/bge-large-zh-v1.5"),
                        List.of("BAAI/bge-small-zh-v1.5"), "BGE 评估器, 大模型更准但慢", false)
        ));

        // ============== 部署 (2) ==============
        list.add(s("model_register", "注册模型", "部署", "model", null,
                "注册到版本表, 记录 hyperparams + loss + bundle 路径",
                f("version", "版本号", "string", "v1", null, null, null, null,
                        List.of("v1", "v2", "v1.0", "v1.1"), "语义化版本号", true),
                f("stage", "阶段", "select", "staging", null, null, null,
                        List.of("staging", "prod", "canary"),
                        List.of("staging"), "staging 测试, prod 正式, canary 灰度", false)
        ));
        list.add(s("model_deploy", "部署上线", "部署", "inference", null,
                "ONNX 推理服务, 支持蓝绿/灰度",
                f("stage", "目标环境", "select", "prod", null, null, null,
                        List.of("staging", "prod", "canary"),
                        List.of("prod"), "prod 流量大, 建议先 canary", true),
                f("replicas", "副本数", "number", 2, 1, 10, 1,
                        null, List.of("2", "4"), "高可用 2 起步, 流量大 4+", false)
        ));

        // ============== Agent (2) ==============
        list.add(s("agent_think", "Agent 思考", "Agent", "agent", "minigpt2",
                "ReAct 思考循环, 自动调用工具",
                f("maxSteps", "最大步数", "number", 5, 1, 20, 1,
                        null, List.of("5", "8"), "5 步够用, 多了 LLM 跑偏", true),
                f("systemPrompt", "系统提示", "textarea", "你是 AI 助手, 可调用工具",
                        null, null, null, null,
                        List.of("你是 AI 助手, 可调用工具", "你是专业客服, 回答简明"),
                        "指导 LLM 行为, 越具体越好", true),
                f("toolWhitelist", "可用工具", "string", "*", null, null, null, null,
                        List.of("*", "web_search, kb_search"), "* 全部, 逗号分隔指定", false)
        ));
        list.add(s("agent_tool", "Agent 工具调用", "Agent", "agent", "minigpt2",
                "直接调用某个具体工具, 不做 ReAct",
                f("toolName", "工具名", "select", "web_search", null, null, null,
                        List.of("web_search", "kb_search", "tool_clean", "tool_chunk", "tool_http"),
                        List.of("web_search"), "工具必须已注册到 tool service", true),
                f("input", "输入", "textarea", "{{input}}", null, null, null, null,
                        List.of("{{input}}", "{{upstream.text}}"), "支持 {{upstream.xxx}} 占位符", true)
        ));

        // ============== 知识库 (4) ==============
        list.add(s("kb_ingest", "文档入库", "知识库", "knowledge", "BAAI/bge-small-zh-v1.5",
                "chunk + embed + 写 ES, 一步完成入库",
                f("kbId", "知识库 ID", "string", "default", null, null, null, null,
                        List.of("default", "kb_faq", "kb_doc"), "已存在的知识库", true),
                f("chunkSize", "切片大小", "number", 256, 16, 512, 32,
                        null, List.of("256", "512"), "256 token 适合 QA, 512 适合长文", true),
                f("overlap", "重叠", "number", 32, 0, 128, 8,
                        null, List.of("32"), "32 overlap 防止边界信息丢失", false),
                f("batchSize", "embedding 批", "number", 32, 1, 128, 8,
                        null, List.of("32"), "批越大越快, 但吃显存", false)
        ));
        list.add(s("kb_search", "知识检索", "知识库", "knowledge", "BAAI/bge-small-zh-v1.5",
                "向量 + BM25 混合检索",
                f("kbId", "知识库 ID", "string", "default", null, null, null, null,
                        List.of("default", "kb_faq"), "检索哪个知识库", true),
                f("topK", "Top K", "number", 3, 1, 20, 1,
                        null, List.of("3", "5"), "3-5 是甜点, 太多引入噪声", true),
                f("threshold", "相似度阈值", "number", 0.6, 0.0, 1.0, 0.05,
                        null, List.of("0.6", "0.7"), "低于此分数的结果过滤掉", false)
        ));
        list.add(s("kb_chunk", "文档切片", "知识库", "knowledge", null,
                "256 token 滑窗, 按句子边界",
                f("chunkSize", "切片大小", "number", 256, 16, 512, 32,
                        null, List.of("256"), "256 是大多数场景甜点", true),
                f("overlap", "重叠", "number", 32, 0, 128, 8,
                        null, List.of("32"), "32 是经典 overlap 值", false),
                f("by", "切分依据", "select", "sentence", null, null, null,
                        List.of("sentence", "paragraph", "char"),
                        List.of("sentence"), "按句子切最自然", false)
        ));
        list.add(s("kb_embed", "向量化", "知识库", "knowledge", "BAAI/bge-small-zh-v1.5",
                "BGE 中文小模型 512 维, 性价比高",
                f("model", "Embedding 模型", "select", "BAAI/bge-small-zh-v1.5", null, null, null,
                        List.of("BAAI/bge-small-zh-v1.5", "BAAI/bge-large-zh-v1.5", "text-embedding-3-small"),
                        List.of("BAAI/bge-small-zh-v1.5"), "BGE 中文最优, OpenAI 多语言", true),
                f("dim", "维度", "number", 512, 256, 3072, 256,
                        null, List.of("512", "1024"), "512 维够用, 1024 维更准", false)
        ));

        // ============== 工具 (3) ==============
        list.add(s("tool_clean", "文本清洗", "工具", "tool", null,
                "通用正则清洗工具",
                f("rules", "清洗规则", "textarea", "去除HTML/URL/邮箱",
                        null, null, null, null,
                        List.of("去除HTML/URL/邮箱", "只保留中英文"),
                        "正则表达式描述", true)
        ));
        list.add(s("tool_chunk", "文本切片", "工具", "tool", null,
                "通用滑窗切片工具",
                f("chunkSize", "切片大小", "number", 256, 16, 512, 32,
                        null, List.of("256"), null, true),
                f("overlap", "重叠", "number", 32, 0, 128, 8,
                        null, List.of("32"), null, false)
        ));
        list.add(s("tool_web_search", "Web 搜索", "工具", "tool", null,
                "DuckDuckGo 公开搜索, 无需 API key",
                f("query", "查询", "string", "{{input}}", null, null, null, null,
                        List.of("{{input}}", "{{upstream.question}}"), "支持 {{upstream}}", true),
                f("maxResults", "最大结果数", "number", 5, 1, 20, 1,
                        null, List.of("5", "10"), "5 个够用, 10 个更全", false)
        ));

        // ============== 推理 (3) ==============
        list.add(s("infer_generate", "文本生成", "推理", "inference", "minigpt",
                "ONNX 模型文本生成",
                f("model", "模型", "select", "minigpt", null, null, null,
                        List.of("minigpt", "minigpt2", "llama-mini"),
                        List.of("minigpt"), "选择已注册的推理模型", true),
                f("prompt", "提示词", "textarea", "{{input}}", null, null, null, null,
                        List.of("{{input}}"), "支持 {{upstream}}", true),
                f("maxTokens", "最大长度", "number", 100, 10, 2048, 10,
                        null, List.of("100", "500"), "对话 100 够, 长文 500+", true),
                f("temperature", "温度", "number", 0.7, 0.0, 2.0, 0.1,
                        null, List.of("0.7", "0.3"), "高=发散, 低=稳定", false)
        ));
        list.add(s("infer_embed", "Embedding 推理", "推理", "inference", "BAAI/bge-small-zh-v1.5",
                "文本向量化推理",
                f("model", "模型", "select", "BAAI/bge-small-zh-v1.5", null, null, null,
                        List.of("BAAI/bge-small-zh-v1.5", "BAAI/bge-large-zh-v1.5"),
                        List.of("BAAI/bge-small-zh-v1.5"), null, true),
                f("text", "输入文本", "textarea", "{{input}}", null, null, null, null,
                        List.of("{{input}}"), null, true)
        ));
        list.add(s("infer_chat", "对话", "推理", "inference", "minigpt2",
                "多轮 chat 推理, 带 history",
                f("model", "模型", "select", "minigpt2", null, null, null,
                        List.of("minigpt2", "llama-mini"),
                        List.of("minigpt2"), null, true),
                f("systemPrompt", "系统提示", "textarea", "你是友好的 AI 助手",
                        null, null, null, null,
                        List.of("你是友好的 AI 助手", "你是专业客服"),
                        null, false)
        ));

        return list;
    }

    /** 构造一个 schema 的辅助 */
    private static ComponentSchema s(String id, String name, String group, String backend, String defaultModel, String desc, ComponentSchema.Field... fields) {
        return new ComponentSchema(id, name, group, desc, backend, defaultModel, List.of(fields));
    }

    /** 构造一个 field 的辅助 */
    private static ComponentSchema.Field f(String key, String label, String type, Object def,
                                            Number min, Number max, Number step,
                                            List<String> options, List<String> examples, String description, boolean required) {
        return new ComponentSchema.Field(key, label, type, def, min, max, step, options, examples, description, required);
    }
}
