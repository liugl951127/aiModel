package com.aiplatform.trainer.lstm;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight character-level LSTM, written as plain NDArrays (no Block).
 *
 * <p>Why a second model: the small demo trainer needs something that
 * converges fast on a single CPU for the "live preview" UX, where the
 * transformer is overkill. The LSTM is a fallback / baseline; the
 * transformer remains the production-quality model.
 *
 * <h2>Layout</h2>
 * <pre>
 *   wte : (V, H)         token embedding
 *   wxg : (H, 4H)        input -> gates  (i, f, g, o concatenated)
 *   whg : (H, 4H)        hidden -> gates
 *   bg  : (4H,)          gate bias
 *   who : (H, V)         hidden -> output
 *   bo  : (V,)           output bias
 * </pre>
 */
@Slf4j
@Getter
public class CharLstm {

    private final int vocabSize;
    private final int hiddenSize;

    private NDArray wte;
    private NDArray wxg;
    private NDArray whg;
    private NDArray bg;
    private NDArray who;
    private NDArray bo;

    public CharLstm(int vocabSize, int hiddenSize) {
        this.vocabSize = vocabSize;
        this.hiddenSize = hiddenSize;
    }

    /** Allocate parameters with small random init. */
    public void initialize(NDManager m) {
        float s = (float) Math.sqrt(1.0 / hiddenSize);
        this.wte = m.randomNormal(new Shape(vocabSize, hiddenSize)).mul(s);
        this.wxg = m.randomNormal(new Shape(hiddenSize, 4 * hiddenSize)).mul(s);
        this.whg = m.randomNormal(new Shape(hiddenSize, 4 * hiddenSize)).mul(s);
        this.bg = m.zeros(new Shape(4 * hiddenSize));
        this.who = m.randomNormal(new Shape(hiddenSize, vocabSize)).mul(s);
        this.bo = m.zeros(new Shape(vocabSize));
        log.info("[LSTM] init: V={} H={}", vocabSize, hiddenSize);
    }

    /**
     * One forward pass over a single token id at a time, returning the
     * logits and updated (h, c) state.
     *
     * @param m     NDManager
     * @param token int token id in [0, V)
     * @param h     previous hidden state, shape (H,)
     * @param c     previous cell state,   shape (H,)
     * @return [logits (V,), h' (H,), c' (H,)]
     */
    public NDArray[] step(NDManager m, int token, NDArray h, NDArray c) {
        NDArray emb = wte.get(token);                              // (H,)
        NDArray gates = emb.dot(wxg).add(h.dot(whg)).add(bg);      // (4H,)
        int H = hiddenSize;
        NDArray i = sigmoid(gates.get(":" + H));
        NDArray f = sigmoid(gates.get(H + ":" + (2 * H)));
        NDArray g = tanh(gates.get(":" + (4 * H)).get((2 * H) + ":" + (3 * H)));
        // NOTE: ai.djl's slice API is start:stop; we approximate via get(":n")
        // For brevity we re-derive g, o via the same one-hot split:
        NDArray o = sigmoid(gates.get((3 * H) + ":"));
        NDArray cNew = f.mul(c).add(i.mul(g));
        NDArray hNew = o.mul(tanh(cNew));
        NDArray logits = hNew.dot(who).add(bo);
        return new NDArray[]{logits, hNew, cNew};
    }

    /**
     * Full forward over a (B, T) int sequence. For each step we run the
     * recurrent step. Returns logits of shape (B, T, V).
     */
    public NDArray forward(NDManager m, NDArray ids) {
        long B = ids.getShape().get(0);
        long T = ids.getShape().get(1);
        int H = hiddenSize;
        int V = vocabSize;
        NDArray h = m.zeros(new Shape(B, H));
        NDArray c = m.zeros(new Shape(B, H));
        List<NDArray> all = new ArrayList<>();
        long[] idsData = ids.toLongArray();
        for (int t = 0; t < (int) T; t++) {
            // gather wte[ids[b, t]] for each b
            float[][][] emb = new float[(int) B][1][H];
            for (int b = 0; b < (int) B; b++) {
                int tok = (int) idsData[b * (int) T + t];
                // slice wte row via direct float copy
                float[] wteRow = new float[H];
                float[] wteAll = wte.toFloatArray();
                System.arraycopy(wteAll, tok * H, wteRow, 0, H);
                emb[b][0] = wteRow;
            }
            // Build a (B, H) tensor from emb (small B*H, so this is fine)
            float[] flat = new float[(int) B * H];
            for (int b = 0; b < (int) B; b++) System.arraycopy(emb[b][0], 0, flat, b * H, H);
            NDArray x = m.create(flat, new Shape(B, H));
            // gates
            NDArray gates = x.dot(wxg).add(h.dot(whg)).add(bg);
            // crude gate split: use full-slice get with start:stop
            NDArray i = sigmoid(gates.get(":" + H));
            NDArray f = sigmoid(gates.get(H + ":" + (2 * H)));
            NDArray g = tanh(gates.get((2 * H) + ":" + (3 * H)));
            NDArray o = sigmoid(gates.get((3 * H) + ":"));
            c = f.mul(c).add(i.mul(g));
            h = o.mul(tanh(c));
            all.add(h.dot(who).add(bo));  // (B, V)
        }
        // stack to (B, T, V)
        int Vloc = vocabSize;
        float[] out = new float[(int) B * (int) T * Vloc];
        for (int t = 0; t < (int) T; t++) {
            float[] logitsT = all.get(t).toFloatArray();
            for (int b = 0; b < (int) B; b++) {
                System.arraycopy(logitsT, b * Vloc, out, (b * (int) T + t) * Vloc, Vloc);
            }
        }
        return m.create(out, new Shape(B, T, Vloc));
    }

    /* ----- tiny activations ----- */
    private static NDArray sigmoid(NDArray x) {
        return x.mul(-1f).exp().add(1f).pow(-1f);
    }
    private static NDArray tanh(NDArray x) {
        return x.mul(2f).exp().sub(1f).div(x.mul(2f).exp().add(1f));
    }

    /** Named-parameter accessor for the exporter. */
    public List<Param> params() {
        return List.of(
                new Param("wte", wte),
                new Param("wxg", wxg),
                new Param("whg", whg),
                new Param("bg", bg),
                new Param("who", who),
                new Param("bo", bo)
        );
    }
    public record Param(String name, NDArray array) {}
}
