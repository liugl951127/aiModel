package com.aiplatform.trainer.model.constraints;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * Cheap constraint layer that wraps a model generator. Given:
 * <ul>
 *   <li>a banned-token set (e.g. nonsense tokens seen during training)</li>
 *   <li>a max-repetition window</li>
 *   <li>a minimum-confidence floor</li>
 * </ul>
 * The decoder rewrites the probability vector before sampling so the
 * generator never produces tokens that would obviously degrade quality.
 */
@Slf4j
public final class ConstrainedDecoder {

    private ConstrainedDecoder() {}

    /**
     * Mask forbidden tokens and overly-repetitive tokens by setting their
     * probabilities to zero (or near-zero). The vector is normalised
     * before return.
     *
     * @param probs      raw softmax probs, length = vocabSize
     * @param banned     token ids that must never be sampled
     * @param recent     the last N generated token ids (for repetition check)
     * @param minConf    floor on the chosen token's probability
     * @return reweighted probs
     */
    public static float[] apply(float[] probs, Set<Integer> banned,
                                List<Integer> recent, int repeatWindow, double minConf) {
        if (probs == null || probs.length == 0) return probs;
        float[] out = probs.clone();
        for (int t : banned) if (t >= 0 && t < out.length) out[t] = 0f;
        if (repeatWindow > 0 && recent != null) {
            int from = Math.max(0, recent.size() - repeatWindow);
            for (int i = from; i < recent.size(); i++) {
                int t = recent.get(i);
                if (t >= 0 && t < out.length) out[t] *= 0.4f; // soft penalty
            }
        }
        double sum = 0;
        for (float v : out) sum += v;
        if (sum <= 0) {
            // fall back to uniform
            for (int i = 0; i < out.length; i++) out[i] = 1f / out.length;
            return out;
        }
        for (int i = 0; i < out.length; i++) out[i] = (float) (out[i] / sum);
        // If the best token is below the floor, surface a warning to the caller.
        int best = argmax(out);
        if (out[best] < minConf) {
            log.debug("[CONSTRAIN] best-token confidence {} < floor {}", out[best], minConf);
        }
        return out;
    }

    private static int argmax(float[] a) {
        int bi = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[bi]) bi = i;
        return bi;
    }
}
