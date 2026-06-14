package com.aiplatform.agent.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Token 用量计量器。
 *
 * <p>按模型统计每个会话/请求的输入 + 输出 token 数。可用于：
 * <ul>
 *   <li>成本核算（按模型单价）</li>
 *   <li>限流：每个租户每天 token 配额</li>
 *   <li>ReAct 循环里"判断是否还能再来一轮"</li>
 * </ul>
 *
 * <p>估算策略：
 * <ul>
 *   <li>GPT/Claude 系：{@code tokens ≈ chars/4}（英文）/ {@code chars/1.5}（中文）</li>
 *   <li>国产模型：{@code tokens ≈ chars/1.6}（更精确的用模型 tokenizer）</li>
 * </ul>
 *
 * <p>本实现采用 char-based 估算，足够 ReAct 循环 budget 控制；精确 tokenize
 * 留给 tokenizer-aware 的 LLM router。</p>
 */
@Slf4j
@Component
public class TokenCounter {

    private final Map<String, Long> inputByModel = new HashMap<>();
    private final Map<String, Long> outputByModel = new HashMap<>();

    /**
     * 估算一段文本的 token 数。
     */
    public long estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        long ascii = 0, cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) ascii++;
            else cjk++;
        }
        // ASCII 约每 4 字符 1 token；CJK 约每 1.5 字符 1 token
        return Math.max(1, (ascii / 4) + (long) Math.ceil(cjk / 1.5));
    }

    /**
     * 累加一次调用的用量。
     */
    public synchronized void record(String model, String input, String output) {
        inputByModel.merge(model, estimate(input), Long::sum);
        outputByModel.merge(model, estimate(output), Long::sum);
    }

    /** 全局统计快照。 */
    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> s = new HashMap<>();
        Map<String, Long> in = new HashMap<>(inputByModel);
        Map<String, Long> out = new HashMap<>(outputByModel);
        s.put("input", in);
        s.put("output", out);
        long totalIn = in.values().stream().mapToLong(Long::longValue).sum();
        long totalOut = out.values().stream().mapToLong(Long::longValue).sum();
        s.put("total", totalIn + totalOut);
        return s;
    }

    /** 重置（测试用）。 */
    public synchronized void reset() {
        inputByModel.clear();
        outputByModel.clear();
    }
}
