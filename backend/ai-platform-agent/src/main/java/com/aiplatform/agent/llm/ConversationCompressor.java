package com.aiplatform.agent.llm;

import com.aiplatform.agent.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 对话压缩器。
 *
 * <p>当 ReAct 循环里历史消息累计 token 超过阈值时，保留：
 * <ol>
 *   <li>系统 prompt（第一条）</li>
 *   <li>最近 K 条 user/assistant 消息</li>
 *   <li>中间历史被压缩成一条 "Summary of N earlier turns" 占位消息</li>
 * </ol>
 *
 * <h2>压缩策略</h2>
 * <p>启发式压缩：把每条 message 头部"目标/动作/观察"截断到 200 字以内；
 * 对过长的 JSON 输出截到 200 字符，标 "..."。这样 token 数能稳定收敛
 * 在 windowSize × 400 字符以内。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationCompressor {

    private final TokenCounter counter;
    @Value("${aiplatform.agent.compress.max-tokens:4000}")
    private long maxTokens;
    @Value("${aiplatform.agent.compress.keep-last:8}")
    private int keepLast;
    @Value("${aiplatform.agent.compress.max-msg-chars:200}")
    private int maxMsgChars;

    private static final Pattern NEWLINE = Pattern.compile("\\s*\\n\\s*");

    /**
     * 压缩历史消息，返回精简后的列表。
     *
     * @param messages 全部历史（按时间升序）
     * @return 压缩后历史（system + 摘要 + 最近 N 条）
     */
    public CompressionResult compress(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return new CompressionResult(List.of(), 0, 0);
        long total = messages.stream().mapToLong(m -> counter.estimate(m.getContent())).sum();
        if (total <= maxTokens) return new CompressionResult(messages, total, 0);

        List<Message> out = new ArrayList<>();
        // 1) 第一条 system 提示
        if (!messages.isEmpty() && "system".equalsIgnoreCase(messages.get(0).getRole())) {
            out.add(messages.get(0));
        }
        // 2) 中间历史摘要
        int sysCount = out.size();
        int tailStart = Math.max(sysCount, messages.size() - keepLast);
        if (tailStart > sysCount) {
            int folded = tailStart - sysCount;
            Message summary = new Message();
            summary.setRole("system");
            summary.setContent(
                    "[ConversationCompressor] 以下 " + folded + " 条历史已被压缩以节省 token。"
                  + "完整历史在数据库，sessionId=" + (messages.get(0).getSessionId()) + "。");
            out.add(summary);
        }
        // 3) 最近 K 条：每条裁剪到 maxMsgChars
        long saved = 0;
        for (int i = tailStart; i < messages.size(); i++) {
            Message orig = messages.get(i);
            if (orig.getContent() != null && orig.getContent().length() > maxMsgChars) {
                long before = counter.estimate(orig.getContent());
                Message trimmed = new Message();
                trimmed.setRole(orig.getRole());
                trimmed.setToolName(orig.getToolName());
                trimmed.setStep(orig.getStep());
                trimmed.setContent(orig.getContent().substring(0, maxMsgChars) + "...");
                out.add(trimmed);
                saved += before - counter.estimate(trimmed.getContent());
            } else {
                out.add(orig);
            }
        }

        long newTotal = out.stream().mapToLong(m -> counter.estimate(m.getContent())).sum();
        log.info("[COMPRESS] {} -> {} tokens, saved {}", total, newTotal, saved);
        return new CompressionResult(out, newTotal, saved);
    }

    /**
     * 单条历史格式化：把多行 JSON / 数组转单行、缩到 maxMsgChars。
     */
    public String singleTruncate(String content) {
        if (content == null) return null;
        String flat = NEWLINE.matcher(content).replaceAll(" ");
        if (flat.length() <= maxMsgChars) return flat;
        return flat.substring(0, maxMsgChars) + "...";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompressionResult {
        private List<Message> messages;
        private long totalTokens;
        private long savedTokens;
    }
}
