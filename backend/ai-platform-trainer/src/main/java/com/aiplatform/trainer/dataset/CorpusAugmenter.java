package com.aiplatform.trainer.dataset;

import com.aiplatform.trainer.rag.KnowledgeRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 训练语料增强器。
 * <p>从一个基础 UTF-8 文件出发，拼接 KB 抓取的段落，生成 Q/A 训练对，并
 * 可选地加轻量字节级噪声。结果是 UTF-8 字节流，交给
 * {@link com.aiplatform.trainer.service.MiniTransformerTrainer} 读取。</p>
 *
 * <h2>三个能力</h2>
 * <ul>
 *   <li>{@link #build} — 基础语料 + KB 段落拼接（可选噪声）</li>
 *   <li>{@link #buildQAPairs} — 从 KB 生成 Q/A 训练对</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorpusAugmenter {

    private final KnowledgeRetriever retriever;

    /**
     * 构造训练语料。
     *
     * @param baseBytes  原始 UTF-8 语料字节
     * @param kbId       用于拉接地段落的 KB id（{@code null} = 跳过）
     * @param seedTopics 用作 RAG 种子的自然语言查询列表
     * @param augment    是否加字节级噪声（轻微翻转）
     * @return 基础语料 + KB 段落的拼接字节（可能含噪声）
     */
    public byte[] build(byte[] baseBytes, Long kbId, List<String> seedTopics, boolean augment) {
        StringBuilder sb = new StringBuilder();
        sb.append(new String(baseBytes, StandardCharsets.UTF_8));
        sb.append("\n\n# === Knowledge passages ===\n\n");

        if (kbId != null && seedTopics != null) {
            for (String topic : seedTopics) {
                String ctx = retriever.retrieve(kbId, topic, 3);
                if (ctx != null && !ctx.isBlank()) {
                    sb.append("## Topic: ").append(topic).append("\n");
                    sb.append(ctx).append("\n\n");
                }
            }
        }

        String out = sb.toString();
        if (!augment) return out.getBytes(StandardCharsets.UTF_8);

        // Light noise: 1% of bytes get a small random flip
        Random rng = new Random(42);
        byte[] data = out.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < data.length; i++) {
            if (rng.nextFloat() < 0.005f) {
                data[i] = (byte) (data[i] ^ (1 << rng.nextInt(8)));
            }
        }
        return data;
    }

    /**
     * 从 KB 生成简单的 "Q: ... A: ..." 训练对。
     * <p>每个段落产生一个 Q/A：问题 = 第一句话，答案 = 整段。</p>
     *
     * @param kbId   知识库 id
     * @param topics 主题列表
     * @return 训练对列表
     */
    public List<Pair> buildQAPairs(Long kbId, List<String> topics) {
        List<Pair> out = new ArrayList<>();
        if (kbId == null || topics == null) return out;
        for (String topic : topics) {
            String ctx = retriever.retrieve(kbId, topic, 5);
            if (ctx == null || ctx.isBlank()) continue;
            for (String passage : ctx.split("\n\n")) {
                if (passage.length() < 16) continue;
                int cut = passage.indexOf('.');
                if (cut < 4 || cut > 80) cut = Math.min(60, passage.length());
                String q = "Q: " + topic + " (" + passage.substring(0, cut).trim() + "?)";
                String a = "A: " + passage.trim();
                out.add(new Pair(q, a));
            }
        }
        return out;
    }

    /** 简单 Q/A 对。 */
    public record Pair(String question, String answer) {}
}
