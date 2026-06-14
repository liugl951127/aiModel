package com.aiplatform.trainer.constraints;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 防幻觉 / 生成质量控制参数。
 * <p>所有字段从 {@code POST /api/trainer/submit} 请求体绑定，同时下发到
 * 训练中的实时预览通道和训练后推理运行时。两边的生成行为完全一致。</p>
 *
 * <h2>每个参数的作用</h2>
 * <ul>
 *   <li>{@code topK} / {@code topP} / {@code temperature} — 标准采样约束（值越小越保守）</li>
 *   <li>{@code repetitionPenalty} — 对已生成 token 的乘法抑制，减少小模型填充式幻觉</li>
 *   <li>{@code maxConsecutiveUnknowns} — 连续 OOD token 超限则中止生成</li>
 *   <li>{@code knowledgeGrounding} — 是否要求生成内容由知识库支撑</li>
 *   <li>{@code knowledgeKbId} / {@code knowledgeDocIds} / {@code knowledgeTopK} — RAG 检索配置</li>
 *   <li>{@code minConfidence} — top1-top2 log-prob 阈值，低于则拒绝回答</li>
 *   <li>{@code factCheck} / {@code minFactOverlap} — 事实核对开关和词重叠阈值</li>
 *   <li>{@code maxAnswerTokens} — 硬截断</li>
 * </ul>
 *
 * <h2>幂等预设</h2>
 * <ul>
 *   <li>{@link #defaults()} — 生产环境默认参数</li>
 *   <li>{@link #strict()} — 严格模式（开启所有防幻觉）</li>
 * </ul>
 *
 * <h2>What each knob does</h2>
 * <ul>
 *   <li>{@code topK} / {@code topP} / {@code temperature} — standard sampling constraints
 *       (lower = more conservative, less hallucination).</li>
 *   <li>{@code repetitionPenalty} — multiplicative penalty on tokens already
 *       produced in the current window. Prevents looping and reduces the
 *       "filler" hallucinations the small model would otherwise emit.</li>
 *   <li>{@code maxConsecutiveUnknowns} — kills the generation if more than
 *       N consecutive token ids are out-of-distribution (e.g. private-use
 *       unicode bytes the corpus never contained). Catches a frequent
 *       failure mode of byte-level models.</li>
 *   <li>{@code knowledgeGrounding} — when true, the preview path and
 *       inference path require the next-token to be supported by a
 *       passage fetched from the knowledge base; otherwise the model
 *       returns the {@code UNSUPPORTED_TOKEN} sentinel.</li>
 *   <li>{@code knowledgeKbId} / {@code knowledgeDocIds} / {@code knowledgeTopK}
 *       — controls the RAG lookup performed before each step.</li>
 *   <li>{@code minConfidence} — logit-margin threshold. If the gap between
 *       the top-1 and top-2 probability is below this number the model
 *       declines to answer (returns the {@code LOW_CONFIDENCE} sentinel).</li>
 *   <li>{@code factCheck} — when true, runs a lightweight lexical overlap
 *       check between the produced span and the source knowledge passages;
 *       if overlap is below {@code minFactOverlap} the span is rejected.</li>
 *   <li>{@code maxAnswerTokens} — hard cap on the generated span, prevents
 *       the model from drifting past its training distribution.</li>
 * </ul>
 *
 * <h2>Sentinels</h2>
 * Sentinels are encoded as a single token id ({@code 0xFE}) so downstream
 * consumers can branch on them without extra plumbing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HallucinationGuardConfig {

    /* ------- 采样约束 ------- */
    /** 候选集大小，保留概率最高的 K 个 token，{@code 1} = 贪心。 */
    private int topK = 40;
    /** nucleus 采样阈值（累积概率截止），{@code 1.0} = 关闭。 */
    private float topP = 0.9f;
    /** 采样温度，{@code 0.0} = 贪心 / 确定性。 */
    private float temperature = 0.7f;
    /** 已生成 token 的乘法惩罚（{@code 1.0} = 关闭）。 */
    private float repetitionPenalty = 1.15f;
    /** 连续 OOD token 超过该值则中止生成。 */
    private int maxConsecutiveUnknowns = 4;

    /* ------- 知识接地（RAG）------- */
    /** 是否要求每个输出片段都有知识库支撑。 */
    private boolean knowledgeGrounding = false;
    /** 目标知识库 id（{@code null} = 第一个可用）。 */
    private Long knowledgeKbId;
    /** 限制 RAG 检索的文档集（可选）。 */
    private java.util.List<Long> knowledgeDocIds;
    /** 每次检索的段落数。 */
    private int knowledgeTopK = 3;

    /* ------- 置信度闸门 ------- */
    /** top1-top2 log-prob 最小差距，低于该值返回 LOW_CONFIDENCE。 */
    private float minConfidence = 0.0f;
    /** 生成片段的最大长度（硬截断）。 */
    private int maxAnswerTokens = 256;

    /* ------- 事实核对 ------- */
    /** 是否要求答案与 KB 段落词重叠。 */
    private boolean factCheck = false;
    /** 答案与源段落的最小 Jaccard 重叠（0~1）。 */
    private float minFactOverlap = 0.15f;

    /* ------- 运行时预设 ------- */
    /**
     * 生产环境默认值。
     * <p>适用：模型对数据分布有把握时，给生成更多自由度。</p>
     */
    public static HallucinationGuardConfig defaults() {
        return new HallucinationGuardConfig();
    }
    /**
     * 严格模式：所有防幻觉机制都开。
     * <p>适用：高风险场景（医疗/金融/法务/对外应答），优先选择"拒绝回答"
     * 而不是"猜一个可能错的答案"。</p>
     */
    public static HallucinationGuardConfig strict() {
        HallucinationGuardConfig c = new HallucinationGuardConfig();
        c.topK = 8;
        c.topP = 0.6f;
        c.temperature = 0.2f;
        c.repetitionPenalty = 1.4f;
        c.maxConsecutiveUnknowns = 2;
        c.knowledgeGrounding = true;
        c.minConfidence = 1.5f;
        c.factCheck = true;
        c.minFactOverlap = 0.35f;
        return c;
    }
}
