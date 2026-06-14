package com.aiplatform.trainer.constraints;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import com.aiplatform.trainer.rag.KnowledgeRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 约束引擎：对原始 logits 应用 {@link HallucinationGuardConfig}，决定下一个
 * token 是否允许输出。
 *
 * <p>本引擎是纯函数（除可选的 KB 抓取外），不依赖 Spring Bean 生命周期，
 * 由训练中的实时预览通道和训练后推理共享，保证两侧行为完全一致。</p>
 *
 * <h2>返回类型</h2>
 * <ul>
 *   <li>{@link Decision#allow} — 接受，附 tokenId + 概率向量 + 置信度差</li>
 *   <li>{@link Decision#reject} — 拒绝，附拒绝原因 + 哨兵 token
 *       （{@code 0xFE}=LOW_CONFIDENCE，{@code 0xFF}=UNSUPPORTED_TOKEN）</li>
 * </ul>
 *
 * <p>The engine is stateless — callers pass in the live logits, the history
 * of emitted token ids, and (optionally) a {@link KnowledgeRetriever} for
 * RAG grounding. The same engine is used by the live preview path during
 * training and by the post-training inference runtime, so the behaviour is
 * identical in both places.
 *
 * <p>Return type: {@link Decision} is either {@code ALLOW(tokenId)} or
 * {@code REJECT(reason, suggestedId)}. The {@code suggestedId} is the
 * special sentinel {@code 0xFE} for {@code LOW_CONFIDENCE} and
 * {@code 0xFF} for {@code UNSUPPORTED_TOKEN}, both of which are reserved
 * outside the byte-level training vocabulary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConstraintEngine {

    /** Reserved token ids outside the byte-level training distribution. */
    public static final int LOW_CONFIDENCE = 0xFE;
    public static final int UNSUPPORTED_TOKEN = 0xFF;
    /** Token-id threshold above which a token is considered OOD for byte-level models. */
    public static final int VOCAB_BYTES = 256;

    private final KnowledgeRetriever retriever;

    /**
     * 不启用 RAG 接地时的便捷重载。
     *
     * @param rawLogits shape {@code (V,)} 的单步 logits 向量
     * @param history   当前生成已输出的 token id 序列
     * @param cfg       防幻觉配置
     * @return 接受或拒绝的决策
     */
    public Decision apply(NDArray rawLogits, int[] history, HallucinationGuardConfig cfg) {
        return apply(rawLogits, history, new int[0], cfg);
    }

    /**
     * 应用约束（支持 RAG 接地）。
     *
     * <p>启用 {@code cfg.knowledgeGrounding} 且 {@code knowledgeAnchorIds} 非空时，
     * 任一 anchor byte 都不在最近抓取的 KB 段落里时本方法返回 UNSUPPORTED_TOKEN。</p>
     *
     * @param rawLogits          shape {@code (V,)} 的单步 logits 向量
     * @param history            已输出 token id 序列
     * @param knowledgeAnchorIds 用于 RAG 接地的 anchor token id
     * @param cfg                防幻觉配置
     * @return 接受或拒绝的决策
     */
    public Decision apply(NDArray rawLogits,
                          int[] history,
                          int[] knowledgeAnchorIds,
                          HallucinationGuardConfig cfg) {
        if (cfg == null) cfg = HallucinationGuardConfig.defaults();
        float[] logits = rawLogits.toFloatArray();

        // 1. Repetition penalty
        if (cfg.getRepetitionPenalty() > 1.0f && history != null && history.length > 0) {
            int window = Math.min(history.length, 64);
            for (int i = history.length - window; i < history.length; i++) {
                int t = history[i];
                if (t < 0 || t >= logits.length) continue;
                logits[t] = logits[t] < 0
                        ? logits[t] * cfg.getRepetitionPenalty()
                        : logits[t] / cfg.getRepetitionPenalty();
            }
        }

        // 2. Top-K + Top-P (nucleus) on a Java copy
        int topK = Math.max(1, cfg.getTopK());
        int[] topIdx = topKIndices(logits, topK);
        float[] masked = new float[logits.length];
        Arrays.fill(masked, Float.NEGATIVE_INFINITY);
        for (int idx : topIdx) masked[idx] = logits[idx];

        if (cfg.getTopP() > 0.0f && cfg.getTopP() < 1.0f) {
            applyTopP(masked, cfg.getTopP());
        }

        // 3. Temperature
        float t = Math.max(1e-3f, cfg.getTemperature());
        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float v : masked) if (v > maxLogit) maxLogit = v;
        double[] probs = new double[logits.length];
        double sum = 0.0;
        for (int i = 0; i < masked.length; i++) {
            if (masked[i] == Float.NEGATIVE_INFINITY) {
                probs[i] = 0.0;
            } else {
                probs[i] = Math.exp((masked[i] - maxLogit) / t);
                sum += probs[i];
            }
        }
        if (sum <= 0) {
            return Decision.reject("zero probability mass", UNSUPPORTED_TOKEN);
        }
        for (int i = 0; i < probs.length; i++) probs[i] /= sum;

        // 4. Confidence gating: gap between top1 and top2 in log space
        int top1 = argmax(probs);
        int top2 = -1;
        for (int i = 0; i < probs.length; i++) {
            if (i == top1) continue;
            if (top2 < 0 || probs[i] > probs[top2]) top2 = i;
        }
        double gap = (probs[top1] > 0 && probs[top2] > 0)
                ? Math.log(probs[top1]) - Math.log(probs[top2])
                : 0.0;
        if (gap < cfg.getMinConfidence()) {
            return Decision.reject("low_confidence gap=" + String.format("%.2f", gap), LOW_CONFIDENCE);
        }

        // 5. Knowledge grounding: require at least one anchor byte to be in the passage
        if (cfg.isKnowledgeGrounding() && knowledgeAnchorIds != null && knowledgeAnchorIds.length > 0) {
            boolean supported = false;
            for (int anchor : knowledgeAnchorIds) {
                if (anchor < 0 || anchor >= VOCAB_BYTES) continue;
                String anchorStr = new String(new byte[]{(byte) anchor}, StandardCharsets.UTF_8);
                if (lastKBContext != null && lastKBContext.contains(anchorStr)) {
                    supported = true;
                    break;
                }
            }
            if (!supported) {
                return Decision.reject("unsupported_token kb miss", UNSUPPORTED_TOKEN);
            }
        }

        // 6. OOD guard: refuse tokens that never appeared in byte-level training
        if (top1 >= VOCAB_BYTES) {
            return Decision.reject("ood token " + top1, UNSUPPORTED_TOKEN);
        }

        return Decision.allow(top1, probs, gap);
    }

    /* -------------- helpers -------------- */

    /**
     * Find the top-K largest entries of {@code arr}. Simple O(V log K) algorithm,
     * fine for vocab ≤ 65k.
     */
    private static int[] topKIndices(float[] arr, int k) {
        Integer[] idx = new Integer[arr.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Float.compare(arr[b], arr[a]));
        int n = Math.min(k, idx.length);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = idx[i];
        return out;
    }

    /** In-place top-P: zero everything not in the top cumulative-prob mass. */
    private static void applyTopP(float[] masked, float topP) {
        Integer[] order = new Integer[masked.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(masked[b], masked[a]));
        // softmax the survivors
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < masked.length; i++) if (masked[i] != Float.NEGATIVE_INFINITY && masked[i] > max) max = masked[i];
        double sum = 0.0;
        double[] probs = new double[masked.length];
        for (int i = 0; i < masked.length; i++) {
            if (masked[i] == Float.NEGATIVE_INFINITY) { probs[i] = 0; continue; }
            probs[i] = Math.exp(masked[i] - max);
            sum += probs[i];
        }
        if (sum <= 0) return;
        double cum = 0.0;
        Set<Integer> keep = new HashSet<>();
        for (int idxVal : order) {
            if (masked[idxVal] == Float.NEGATIVE_INFINITY) break;
            cum += probs[idxVal] / sum;
            keep.add(idxVal);
            if (cum >= topP) break;
        }
        for (int i = 0; i < masked.length; i++) {
            if (!keep.contains(i)) masked[i] = Float.NEGATIVE_INFINITY;
        }
    }

    private static int argmax(double[] a) {
        int best = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[best]) best = i;
        return best;
    }

    /* -------------- live state set by retriever before apply() -------------- */

    /** 最近一次抓取的 KB 段落文本，供接地检查使用。 */
    private volatile String lastKBContext;

    /**
     * 在 {@link #apply} 之前由 {@link com.aiplatform.trainer.preview.PreviewService}
     * 调用，注入当前 prompt 对应的 KB 上下文。
     */
    public void primeKBContext(String context) {
        this.lastKBContext = context;
    }

    /* -------------- result types -------------- */

    /**
     * 约束引擎的决策结果。
     * <p>{@code ALLOW} 携带 tokenId + 概率向量 + 置信度差；
     * {@code REJECT} 携带拒绝原因 + 哨兵 token（{@code 0xFE}/{@code 0xFF}）。</p>
     */
    public static final class Decision {
        public final boolean allowed;
        public final int tokenId;
        public final double[] probs;
        public final double confidenceGap;
        public final String reason;

        private Decision(boolean allowed, int tokenId, double[] probs, double gap, String reason) {
            this.allowed = allowed;
            this.tokenId = tokenId;
            this.probs = probs;
            this.confidenceGap = gap;
            this.reason = reason;
        }

        /**
         * 构造“接受”决策。
         */
        public static Decision allow(int t, double[] p, double gap) {
            return new Decision(true, t, p, gap, null);
        }

        /**
         * 构造“拒绝”决策。
         */
        public static Decision reject(String reason, int sentinel) {
            return new Decision(false, sentinel, null, 0.0, reason);
        }
    }
}
