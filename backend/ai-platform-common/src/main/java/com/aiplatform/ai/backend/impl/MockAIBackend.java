package com.aiplatform.ai.backend.impl;

import com.aiplatform.ai.backend.AIBackend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * ★ Mock 后端 (默认) — 完全离线, 0 联网, 用于演示/CI/无模型环境.
 *
 * <p>特点:
 * <ul>
 *   <li>chat: 返回模板化回答 (基于输入 hash)</li>
 *   <li>embed: 用 MD5 哈希生成伪 512 维向量 (归一化后 cosine 相似度可用)</li>
 *   <li>rerank: 基于向量 cosine 相似度</li>
 *   <li>webSearch: 返回内置 corpus 的关键词匹配</li>
 * </ul>
 *
 * <p>激活: {@code aiplatform.ai.backend=mock} (默认就是这个)</p>
 */
@Slf4j

@ConditionalOnProperty(name = "aiplatform.ai.backend", havingValue = "mock", matchIfMissing = true)
public class MockAIBackend implements AIBackend {

    private static final int EMBED_DIM = 512;

    @Override
    public String name() { return "mock"; }

    @Override
    public String chat(String system, List<Map<String, String>> messages, Map<String, Object> options) {
        // 找最后一条 user
        String lastUser = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).get("role"))) {
                lastUser = messages.get(i).get("role") + ": " + messages.get(i).get("content");
                break;
            }
        }
        return "[Mock AI 后端 · 离线演示] 已收到 " + messages.size() + " 条消息, 最后一条: "
                + lastUser.substring(0, Math.min(80, lastUser.length()))
                + (lastUser.length() > 80 ? "..." : "")
                + "\n\n(配 aiplatform.ai.backend=onnx 切换到真本地 ONNX 模型, "
                + "或 ollama 切到本地 Ollama 服务)";
    }

    @Override
    public float[] embed(String text) {
        // 哈希到 512 维伪向量 (确定性: 同样输入 -> 同样输出, 可用 cosine 衡量相似度)
        float[] v = new float[EMBED_DIM];
        try {
            // 取多轮 md5 拿更多 bit
            String seed = text == null ? "" : text;
            for (int i = 0; i < 8; i++) {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] h = md.digest((seed + ":" + i).getBytes(StandardCharsets.UTF_8));
                for (int j = 0; j < 16 && (i * 16 + j) < EMBED_DIM; j++) {
                    v[i * 16 + j] = (h[j] - 128) / 128.0f;
                }
            }
            // 剩余维填 0
            // 归一化
            float norm = 0;
            for (float x : v) norm += x * x;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < v.length; i++) v[i] /= norm;
        } catch (Exception e) {
            // ignore
        }
        return v;
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> candidates, int topK) {
        float[] qv = embed(query);
        List<RerankResult> results = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            float[] cv = embed(candidates.get(i));
            float score = cosine(qv, cv);
            results.add(new RerankResult(i, score));
        }
        results.sort((a, b) -> Float.compare(b.score(), a.score()));
        return results.subList(0, Math.min(topK, results.size()));
    }

    @Override
    public List<WebSearchResult> webSearch(String query, int topK) {
        // 内置 corpus: 模拟离线搜索结果
        List<WebSearchResult> corpus = List.of(
            new WebSearchResult("Spring Cloud Alibaba 2023.0.1.0 官方文档",
                "local://docs/sca-2023",
                "Spring Cloud Alibaba 是 Spring Cloud 的 Alibaba 实现, 提供 Nacos / Sentinel / Seata 等组件...",
                0.0),
            new WebSearchResult("ReAct: Reasoning + Acting in LLMs",
                "local://docs/react-paper",
                "ReAct 是结合推理 (Reasoning) 和行动 (Acting) 的 LLM 框架, 论文 2022 年发表...",
                0.0),
            new WebSearchResult("向量数据库对比: Milvus / Qdrant / Elasticsearch",
                "local://docs/vector-db",
                "三大开源向量数据库对比: Milvus 适合大规模, Qdrant 适合 Rust 性能, ES 适合已有栈...",
                0.0),
            new WebSearchResult("ONNX Runtime 部署 LLM 实战",
                "local://docs/onnx-llm",
                "用 ONNX Runtime 部署大模型, CPU 可跑 7B 量化模型, 延迟 100ms 内...",
                0.0),
            new WebSearchResult("BGE 中文嵌入模型: bge-small-zh-v1.5",
                "local://docs/bge-zh",
                "BAAI 发布的 BGE 系列中文嵌入模型, small 版本 512 维, large 版本 1024 维...",
                0.0),
            new WebSearchResult("分布式锁: Redisson vs Curator",
                "local://docs/distributed-lock",
                "Java 生态分布式锁实现对比: Redisson 易用, ZooKeeper 强一致, etcd 跨云...",
                0.0),
            new WebSearchResult("Vue 3 Composition API 最佳实践",
                "local://docs/vue3",
                "Vue 3 的 setup script + ref/reactive 组合式 API, 适合大型项目...",
                0.0),
            new WebSearchResult("DJL (Deep Java Library) 训练入门",
                "local://docs/djl",
                "DJL 是 AWS 开源的 Java 深度学习库, 支持 PyTorch / MXNet / TensorFlow...",
                0.0)
        );
        // 简单关键词匹配
        String q = query == null ? "" : query.toLowerCase();
        return corpus.stream()
            .filter(r -> q.isBlank()
                || r.title().toLowerCase().contains(q)
                || r.snippet().toLowerCase().contains(q)
                || anyWordMatch(q, r.title().toLowerCase() + " " + r.snippet().toLowerCase()))
            .limit(Math.max(1, topK))
            .map(r -> new WebSearchResult(r.title(), r.url(), r.snippet(), scoreByMatch(r, q)))
            .toList();
    }

    private boolean anyWordMatch(String q, String text) {
        for (String w : q.split("\\s+")) {
            if (w.length() > 1 && text.contains(w)) return true;
        }
        return false;
    }

    private double scoreByMatch(WebSearchResult r, String q) {
        if (q.isBlank()) return 0.5;
        int hits = 0;
        for (String w : q.split("\\s+")) {
            if (w.length() > 1 && (r.title().toLowerCase().contains(w) || r.snippet().toLowerCase().contains(w))) hits++;
        }
        return Math.min(1.0, 0.4 + 0.2 * hits);
    }

    @Override
    public boolean isHealthy() { return true; }

    private static float cosine(float[] a, float[] b) {
        float dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0 || nb == 0) return 0;
        return dot / (float) (Math.sqrt(na) * Math.sqrt(nb));
    }
}
