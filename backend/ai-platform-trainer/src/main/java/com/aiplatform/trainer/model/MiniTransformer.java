package com.aiplatform.trainer.model;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoder-only Transformer model ("Mini-GPT").  Plain POJO with named {@link NDArray}
 * parameter slots — does NOT extend {@code AbstractBlock} because the DJL 0.36 API
 * is unstable across engine versions and we want the trainer to stay portable.
 *
 * <p>Forward pass: see {@link TransformerOps#forward}.
 *
 * <h2>Layout</h2>
 * <ul>
 *   <li>wte   : (vocab, n_embd)   - token embedding</li>
 *   <li>wpe   : (blockSize, n_embd) - positional embedding</li>
 *   <li>lnFg : (n_embd,)  - final layer-norm scale</li>
 *   <li>lnFb : (n_embd,)  - final layer-norm bias</li>
 *   <li>blockParams : per-block ln1/ln2, q/k/v/o, fc1/fc2 weights+biases</li>
 * </ul>
 *
 * <h2>Mutation</h2>
 * NDArray is immutable in DJL 0.36; {@link #setWte}/{@link #setWpe}/
 * {@link #setBlockParam} all REPLACE the slot with a fresh NDArray returned
 * from the trainer's update step (e.g. Adam).
 */
@Slf4j
@Getter
public class MiniTransformer {

    /** Final-layer layer-norm scale, shape {@code (n_embd,)}. */
    private final int vocabSize;
    /** Max context window (positions). */
    private final int blockSize;
    /** Number of stacked transformer blocks. */
    private final int nLayer;
    /** Number of attention heads per block. */
    private final int nHead;
    /** Embedding dimension. Must be divisible by {@code nHead}. */
    private final int nEmbd;
    /** {@code nEmbd / nHead}. */
    private final int headDim;
    /** Whether input and output embeddings share the same matrix. */
    private final boolean tiedWeights = true;

    /** Token embedding, shape {@code (vocab, n_embd)}. */
    private NDArray wte;
    /** Positional embedding, shape {@code (blockSize, n_embd)}. */
    private NDArray wpe;
    /** Final layer-norm scale, shape {@code (n_embd,)}. */
    private NDArray lnFg;
    /** Final layer-norm bias, shape {@code (n_embd,)}. */
    private NDArray lnFb;
    /** All per-block named parameters, see {@link #initialize}. */
    private final List<Parameter> blockParams = new ArrayList<>();

    /**
     * Construct an uninitialized model. Call {@link #initialize} before use.
     *
     * @param vocabSize vocabulary size (e.g. 256 for byte-level)
     * @param blockSize max context length
     * @param nLayer    number of transformer blocks
     * @param nHead     number of attention heads
     * @param nEmbd     embedding dimension
     * @throws IllegalArgumentException if {@code nEmbd % nHead != 0}
     */
    public MiniTransformer(int vocabSize, int blockSize, int nLayer, int nHead, int nEmbd) {
        if (nEmbd % nHead != 0) {
            throw new IllegalArgumentException("nEmbd (" + nEmbd + ") must be divisible by nHead (" + nHead + ")");
        }
        this.vocabSize = vocabSize;
        this.blockSize = blockSize;
        this.nLayer = nLayer;
        this.nHead = nHead;
        this.nEmbd = nEmbd;
        this.headDim = nEmbd / nHead;
    }

    /**
     * Allocate all parameters with small Gaussian init (std=0.02) for weights
     * and ones/zeros for layer-norm scales/biases — matches the GPT reference.
     *
     * @param manager NDManager that owns the parameter NDArrays
     */
    public void initialize(NDManager manager) {
        this.wte = manager.randomNormal(new Shape(vocabSize, nEmbd)).mul(0.02f);
        this.wpe = manager.randomNormal(new Shape(blockSize, nEmbd)).mul(0.02f);
        this.lnFg = manager.ones(new Shape(nEmbd));
        this.lnFb = manager.zeros(new Shape(nEmbd));
        blockParams.clear();
        for (int i = 0; i < nLayer; i++) {
            String p = "blk" + i + "_";
            // Layer-norm 1 (pre-attn)
            addParam(manager, p + "ln1_g", new Shape(nEmbd), true);
            addParam(manager, p + "ln1_b", new Shape(nEmbd), false);
            // Layer-norm 2 (pre-mlp)
            addParam(manager, p + "ln2_g", new Shape(nEmbd), true);
            addParam(manager, p + "ln2_b", new Shape(nEmbd), false);
            // Attention projections
            addParam(manager, p + "wq", new Shape(nEmbd, nEmbd), true);
            addParam(manager, p + "wk", new Shape(nEmbd, nEmbd), true);
            addParam(manager, p + "wv", new Shape(nEmbd, nEmbd), true);
            addParam(manager, p + "wo", new Shape(nEmbd, nEmbd), true);
            // MLP (4x expansion)
            addParam(manager, p + "fc1_w", new Shape(nEmbd, 4 * nEmbd), true);
            addParam(manager, p + "fc1_b", new Shape(4 * nEmbd), false);
            addParam(manager, p + "fc2_w", new Shape(4 * nEmbd, nEmbd), true);
            addParam(manager, p + "fc2_b", new Shape(nEmbd), false);
        }
        log.info("[MODEL] init: vocab={} ctx={} embd={} layer={} head={} | {} params",
                vocabSize, blockSize, nEmbd, nLayer, nHead, blockParams.size() + 4);
    }

    /**
     * Append a new named parameter to {@link #blockParams}.
     *
     * @param m      NDManager owner
     * @param name   parameter name (unique within the model)
     * @param s      parameter shape
     * @param random if true, init from N(0, 0.02); else init to zero
     */
    private void addParam(NDManager m, String name, Shape s, boolean random) {
        NDArray init = random ? m.randomNormal(s).mul(0.02f) : m.zeros(s);
        blockParams.add(new Parameter(name, init));
    }

    /** Look up a block parameter by name (e.g. {@code "blk0_wq"}). */
    public NDArray getBlockParam(String name) {
        for (Parameter p : blockParams) {
            if (p.name().equals(name)) return p.array();
        }
        return null;
    }

    /**
     * Replace the wte slot. Required after every optimizer step because NDArray
     * is immutable in DJL 0.36.
     */
    public void setWte(NDArray a) { this.wte = a; }
    /** Replace the wpe slot. */
    public void setWpe(NDArray a) { this.wpe = a; }
    /** Replace the final layer-norm scale slot. */
    public void setLnFg(NDArray a) { this.lnFg = a; }
    /** Replace the final layer-norm bias slot. */
    public void setLnFb(NDArray a) { this.lnFb = a; }

    /**
     * Replace a single block parameter in-place.
     *
     * @param name parameter name (e.g. {@code "blk2_fc1_w"})
     * @param a    new value
     */
    public void setBlockParam(String name, NDArray a) {
        for (int i = 0; i < blockParams.size(); i++) {
            if (blockParams.get(i).name().equals(name)) {
                blockParams.set(i, new Parameter(name, a));
                return;
            }
        }
    }

    /**
     * Named parameter wrapper holding the parameter name and its current NDArray.
     * The trainer keeps a flat list of these for export.
     */
    public record Parameter(String name, NDArray array) {
    }
}
