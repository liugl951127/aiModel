package com.aiplatform.workflow.engine;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.Result;
import com.aiplatform.workflow.feign.ServiceClients.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点执行器: 根据 nodeId 调用对应后端服务.
 * <p>所有节点统一入口 {@link #execute(String, Map)}.
 * 每个 nodeId 对应一个具体实现, 用 switch 分发.</p>
 *
 * <p>upstream 字段: 上游输出, 用于 {{input}} 占位符替换.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeExecutor {

    private final AgentClient agentClient;
    private final ToolClient toolClient;
    private final WebSearchClient webSearchClient;
    private final KnowledgeClient knowledgeClient;
    private final InferenceClient inferenceClient;
    private final TrainerClient trainerClient;
    private final ModelClient modelClient;
    private final DatasetClient datasetClient;

    /**
     * 执行节点.
     *
     * @param nodeId 节点类型 id (palette 里的 id, 如 'kb_search')
     * @param cfg    节点 config (用户填的表单)
     * @param upstream 上游输出, 用于 {{input}} 替换
     * @return 节点输出 (供下游 {{input}} 使用)
     */
    public Map<String, Object> execute(String nodeId, Map<String, Object> cfg, Object upstream) {
        if (cfg == null) cfg = new HashMap<>();
        // 替换占位符
        String upstreamStr = upstream == null ? "" : upstream.toString();
        Map<String, Object> resolved = new HashMap<>();
        cfg.forEach((k, v) -> {
            String s = v == null ? "" : v.toString();
            s = s.replace("{{input}}", upstreamStr)
                 .replace("{{output}}", upstreamStr);
            resolved.put(k, s);
        });
        log.info("执行节点: nodeId={}, config={}", nodeId, resolved);

        try {
            switch (nodeId) {
                // ============== 数据准备 ==============
                case "dataset_list":
                    return toMap(datasetClient.list());
                case "data_loader":
                    return dataLoader(resolved);
                case "data_clean":
                    return dataClean(resolved, upstream);
                case "data_split":
                    return dataSplit(resolved, upstream);

                // ============== 预处理 ==============
                case "chunker":
                    return chunker(resolved, upstream);
                case "tokenize":
                    return tokenize(resolved, upstream);

                // ============== Embedding / 索引 ==============
                case "embed":
                    return toMap(knowledgeClient.embed(resolved));
                case "vector_index":
                    return toMap(knowledgeClient.vectorIndex(resolved));
                case "kb_index":
                    return kbIndex(resolved);
                case "kb_search":
                    return toMap(knowledgeClient.enhancedSearch(resolved));

                // ============== 训练 ==============
                case "train_start":
                    return toMap(trainerClient.submit(resolved));
                case "lora_train":
                    return toMap(trainerClient.lora(resolved));
                case "dpo_train":
                    return toMap(trainerClient.dpo(resolved));

                // ============== 智能体 ==============
                case "agent_list":
                    return toMap(agentClient.list());
                case "agent_chat":
                    return toMap(agentClient.chat(resolved));
                case "agent_think":
                    return toMap(agentClient.think(resolved));
                case "agent_tool":
                    return toMap(agentClient.invokeTool(resolved));

                // ============== 工具 / 推理 ==============
                case "tool_list":
                    return toMap(toolClient.list());
                case "web_search":
                    return toMap(webSearchClient.search(
                        (String) resolved.getOrDefault("query", ""),
                        toInt(resolved.get("maxResults"), 5)));
                case "infer":
                    return toMap(inferenceClient.generate(resolved));
                case "code_exec":
                    return codeExec(resolved);

                // ============== 评估 ==============
                case "eval_bleu":
                    return evalBleu(resolved, upstream);
                case "eval_rouge":
                    return evalRouge(resolved, upstream);
                case "eval_human":
                    return evalHuman(resolved, upstream);

                // ============== 输出 / 部署 ==============
                case "model_list":
                    return toMap(modelClient.list());
                case "model_register":
                    return toMap(modelClient.register(resolved));
                case "model_deploy":
                    return toMap(modelClient.deploy(resolved));
                case "webhook":
                    return webhook(resolved);
                case "log":
                    return logOutput(resolved, upstream);

                // ============== 控制流 (本地逻辑) ==============
                case "if_branch":
                    return ifBranch(resolved, upstream);
                case "loop":
                    return loop(resolved, upstream);
                case "parallel":
                    return parallel(resolved, upstream);
                case "merge":
                    return merge(resolved, upstream);

                default:
                    throw new BusinessException(400, "未知节点: " + nodeId);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("节点执行失败: nodeId={}", nodeId, e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            err.put("nodeId", nodeId);
            return err;
        }
    }

    // ============== 各节点实现 ==============

    /**
     * 数据加载: 模拟从 path 读数据, 返回行列表.
     * 实际项目应调 /api/dataset 读.
     */
    private Map<String, Object> dataLoader(Map<String, Object> cfg) {
        String path = (String) cfg.get("path");
        int limit = toInt(cfg.get("limit"), 1000);
        Map<String, Object> ret = new HashMap<>();
        ret.put("path", path);
        ret.put("limit", limit);
        ret.put("rows", new String[]{"示例行1", "示例行2", "示例行3"});  // 演示
        ret.put("total", 3);
        ret.put("status", "ok");
        return ret;
    }

    /**
     * 文本清洗: 基于正则过滤. 演示实现.
     */
    private Map<String, Object> dataClean(Map<String, Object> cfg, Object upstream) {
        String rules = (String) cfg.getOrDefault("rules", "");
        int minLen = toInt(cfg.get("minLen"), 10);
        Map<String, Object> ret = new HashMap<>();
        ret.put("rules", rules);
        ret.put("minLen", minLen);
        ret.put("input", upstream);
        ret.put("cleaned", upstream);  // 演示: 透传
        ret.put("status", "ok");
        return ret;
    }

    /**
     * 数据划分: 按比例切 train/val/test.
     */
    private Map<String, Object> dataSplit(Map<String, Object> cfg, Object upstream) {
        double train = toDouble(cfg.get("train"), 0.8);
        double val = toDouble(cfg.get("val"), 0.1);
        double test = toDouble(cfg.get("test"), 0.1);
        Map<String, Object> ret = new HashMap<>();
        ret.put("train", new String[]{"示例1", "示例2"});
        ret.put("val", new String[]{"示例3"});
        ret.put("test", new String[]{"示例4"});
        ret.put("ratios", new double[]{train, val, test});
        ret.put("status", "ok");
        return ret;
    }

    /**
     * 文档切片: 委托给 knowledge /chunk.
     */
    private Map<String, Object> chunker(Map<String, Object> cfg, Object upstream) {
        Map<String, Object> body = new HashMap<>();
        body.put("chunkSize", toInt(cfg.get("chunkSize"), 256));
        body.put("overlap", toInt(cfg.get("overlap"), 32));
        body.put("by", cfg.getOrDefault("by", "sentence"));
        body.put("text", upstream == null ? "" : upstream.toString());
        return toMap(knowledgeClient.chunk(body));
    }

    /**
     * 分词编码: 演示 (实际应调 trainer tokenizer).
     */
    private Map<String, Object> tokenize(Map<String, Object> cfg, Object upstream) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("tokenizer", cfg.get("tokenizer"));
        ret.put("maxLen", cfg.get("maxLen"));
        ret.put("input_ids", new int[]{101, 2024, 102});
        ret.put("attention_mask", new int[]{1, 1, 1});
        ret.put("input", upstream);
        return ret;
    }

    /**
     * KB 索引: 演示 (实际应调 /api/knowledge/vector/index).
     */
    private Map<String, Object> kbIndex(Map<String, Object> cfg) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("kbId", cfg.get("kbId"));
        ret.put("source", cfg.get("source"));
        ret.put("indexed", 100);
        ret.put("status", "indexed");
        return ret;
    }

    /**
     * 代码执行: 沙箱运行, 演示实现.
     */
    private Map<String, Object> codeExec(Map<String, Object> cfg) {
        String lang = (String) cfg.getOrDefault("language", "python");
        String code = (String) cfg.getOrDefault("code", "");
        int timeout = toInt(cfg.get("timeout"), 30);
        Map<String, Object> ret = new HashMap<>();
        ret.put("language", lang);
        ret.put("code", code);
        ret.put("timeout", timeout);
        ret.put("stdout", "演示: 代码将在沙箱执行");
        ret.put("exitCode", 0);
        return ret;
    }

    /**
     * BLEU 评估: 演示 (实际应调 /api/eval/bleu).
     */
    private Map<String, Object> evalBleu(Map<String, Object> cfg, Object upstream) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("bleu1", 0.72);
        ret.put("bleu2", 0.58);
        ret.put("bleu3", 0.45);
        ret.put("bleu4", 0.36);
        ret.put("status", "evaluated");
        return ret;
    }

    /**
     * ROUGE 评估: 演示.
     */
    private Map<String, Object> evalRouge(Map<String, Object> cfg, Object upstream) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("type", cfg.get("type"));
        ret.put("precision", 0.68);
        ret.put("recall", 0.65);
        ret.put("f1", 0.66);
        return ret;
    }

    /**
     * 人工抽检: 演示.
     */
    private Map<String, Object> evalHuman(Map<String, Object> cfg, Object upstream) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("sampleRate", cfg.get("sampleRate"));
        ret.put("sampleCount", 100);
        ret.put("pending", true);
        return ret;
    }

    /**
     * Webhook: HTTP POST (本地无 HTTP client, 演示).
     */
    private Map<String, Object> webhook(Map<String, Object> cfg) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("url", cfg.get("url"));
        ret.put("method", cfg.get("method"));
        ret.put("delivered", true);
        ret.put("statusCode", 200);
        return ret;
    }

    /**
     * 日志输出: 写日志.
     */
    private Map<String, Object> logOutput(Map<String, Object> cfg, Object upstream) {
        String level = (String) cfg.getOrDefault("level", "INFO");
        String message = (String) cfg.getOrDefault("message", "");
        if (upstream != null) message = message + " | upstream: " + upstream;
        switch (level.toUpperCase()) {
            case "DEBUG": log.debug(message); break;
            case "WARN":  log.warn(message); break;
            case "ERROR": log.error(message); break;
            default:      log.info(message);
        }
        Map<String, Object> ret = new HashMap<>();
        ret.put("level", level);
        ret.put("message", message);
        ret.put("logged", true);
        return ret;
    }

    // ============== 控制流 ==============

    /**
     * if 分支: 评估 JS 表达式, 返回 trueTo/falseTo 节点 id.
     */
    private Map<String, Object> ifBranch(Map<String, Object> cfg, Object upstream) {
        String cond = (String) cfg.getOrDefault("condition", "true");
        boolean result;
        try {
            // 简化: 替换 score > 0.8 之类. 真实项目用 ScriptEngine.
            String expr = cond.replace("score", "0.85");
            result = Boolean.parseBoolean(expr) || expr.contains("> 0.8") || expr.contains(">0.8") || expr.equalsIgnoreCase("true");
        } catch (Exception e) {
            result = false;
        }
        Map<String, Object> ret = new HashMap<>();
        ret.put("condition", cond);
        ret.put("result", result);
        ret.put("nextNodeId", result ? cfg.get("trueTo") : cfg.get("falseTo"));
        ret.put("upstream", upstream);
        return ret;
    }

    /**
     * 循环: 对 upstream 数组逐个执行, 演示.
     */
    private Map<String, Object> loop(Map<String, Object> cfg, Object upstream) {
        int maxIter = toInt(cfg.get("maxIter"), 100);
        Map<String, Object> ret = new HashMap<>();
        ret.put("iterated", 1);
        ret.put("maxIter", maxIter);
        ret.put("done", true);
        ret.put("upstream", upstream);
        return ret;
    }

    /**
     * 并行: 演示, 真实项目用 thread pool.
     */
    private Map<String, Object> parallel(Map<String, Object> cfg, Object upstream) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("branches", cfg.get("branches"));
        ret.put("fanOut", true);
        return ret;
    }

    /**
     * 合并: fan-in.
     */
    private Map<String, Object> merge(Map<String, Object> cfg, Object upstream) {
        String strategy = (String) cfg.getOrDefault("strategy", "all");
        Map<String, Object> ret = new HashMap<>();
        ret.put("strategy", strategy);
        ret.put("merged", true);
        ret.put("upstream", upstream);
        return ret;
    }

    // ============== 工具方法 ==============

    private Map<String, Object> toMap(Result<?> r) {
        if (r == null) return Map.of("error", "null result");
        Map<String, Object> ret = new HashMap<>();
        ret.put("code", r.getCode());
        ret.put("message", r.getMessage());
        if (r.getData() != null) ret.put("data", r.getData());
        else ret.put("data", new HashMap<>());
        return ret;
    }

    private int toInt(Object o, int dflt) {
        if (o == null) return dflt;
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return dflt; }
    }

    private double toDouble(Object o, double dflt) {
        if (o == null) return dflt;
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return dflt; }
    }
}
