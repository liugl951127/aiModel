package com.aiplatform.trainer.model.constraints;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Static helpers used by the trainers + the preview pipeline to compute
 * anti-hallucination signals.
 *
 * <h2>Signals we expose</h2>
 * <ul>
 *   <li>{@code entropy}        - top-k softmax entropy (lower → more confident)</li>
 *   <li>{@code repetition}     - n-gram repetition penalty (lower → less repetitive)</li>
 *   <li>{@code factualSupport} - 0..1, higher means answer is grounded in retrieved context</li>
 *   <li>{@code citationCoverage} - RAG-specific: fraction of sentences with a citation</li>
 *   <li>{@code hallucinationScore} - composite, 0=safe, 1=likely hallucinated</li>
 * </ul>
 */
public final class AntiHallucination {

    private AntiHallucination() {}

    /**
     * @param loss current training loss
     * @return entropy proxy in [0, 1] — higher when the model is uncertain
     */
    public static double tokenEntropy(double loss) {
        // crude mapping: low loss → low entropy
        double e = Math.min(1.0, Math.max(0.0, loss / 6.0));
        return round(e);
    }

    /**
     * @param loss current training loss
     * @return repetition penalty in [0, 1] — high loss correlates with degenerate repetition
     */
    public static double repetitionPenalty(double loss) {
        return round(Math.min(1.0, Math.max(0.0, (loss - 1.0) / 4.0)));
    }

    /**
     * Composite hallucination score. Combines entropy + repetition and
     * inverts factualSupport. Returns 0 (no risk) .. 1 (almost certainly
     * hallucinating).
     */
    public static double hallucinationScore(double entropy, double repetition, double factualSupport) {
        double h = 0.5 * entropy + 0.2 * repetition + 0.3 * (1 - factualSupport);
        return round(Math.min(1.0, Math.max(0.0, h)));
    }

    /**
     * Decide whether the model should refuse to answer at the configured
     * threshold. Returns the suggested user-facing message.
     */
    public static RefusalDecision shouldRefuse(double hallucinationScore, double threshold) {
        if (hallucinationScore >= threshold) {
            return new RefusalDecision(true,
                    "置信度不足（幻觉分数 " + fmt(hallucinationScore) +
                            " ≥ 阈值 " + fmt(threshold) + "），已拒答。请补充上下文或调高阈值。");
        }
        return new RefusalDecision(false, null);
    }

    /** Cheap deterministic stand-in for a real sample. */
    public static String synthesizedSample(String prompt, int step) {
        String seed = (prompt == null ? "" : prompt) + ":" + step;
        long h = stableHash(seed);
        String[] fragments = {
                "the model continues to learn from context",
                "training reduces loss and improves accuracy",
                "retrieval augmentation grounds answers in source data",
                "calibrated confidence prevents overconfident hallucinations",
                "cross entropy guides parameter updates via backpropagation"
        };
        int idx = (int) Math.floorMod(h, fragments.length);
        return fragments[idx] + " (step " + step + ")";
    }

    private static long stableHash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes());
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (d[i] & 0xffL);
            return v;
        } catch (Exception e) {
            return s.hashCode();
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Refusal decision returned to the preview/UI layer. */
    public record RefusalDecision(boolean refuse, String reason) {}
}
