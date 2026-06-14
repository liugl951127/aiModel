package com.aiplatform.trainer.model;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure NDArray operations for the Mini-Transformer.
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@link #forward}    - one transformer pass returning logits (B, T, V)</li>
 *   <li>{@link #crossEntropy}      - loss NDArray (1,) version</li>
 *   <li>{@link #crossEntropyScalar}- loss float version (skips pytorch scalar bug)</li>
 *   <li>{@link #adamStep}   - in-place Adam update returning {@link AdamResult}</li>
 *   <li>{@link #generate}   - byte-level autoregressive sampling</li>
 *   <li>{@link #layerNorm}  - (B, T, C) layer-norm with affine</li>
 *   <li>{@link #linear} / {@link #linearBias} - matMul(+bias) helpers</li>
 *   <li>{@link #gelu}       - tanh-approximation GELU</li>
 *   <li>{@link #softmax}    - numerically-stable softmax along an axis</li>
 * </ul>
 *
 * <h2>DJL 0.36 pytorch engine caveats</h2>
 * The 0.36 pytorch engine has a known regression where
 * {@code NDManager.create(byte[], Shape, DataType)} and several
 * {@code NDArray.reshape(...)} paths throw
 * {@code ArrayIndexOutOfBoundsException: Index N out of bounds for length 32}.
 * We work around this by always going through
 * {@link #createFloat(NDManager, float[], Shape)} which uses a heap
 * {@link ByteBuffer} with {@code LITTLE_ENDIAN} ordering and an explicit
 * {@code position(0)} before handing off to the engine.
 *
 * <h2>Backprop approximation</h2>
 * This is a single-GPU demo trainer — full backprop through every parameter
 * is out of scope. We approximate by sending the cross-entropy gradient only
 * into the token embedding surface, matching the Python reference trainer
 * shipped with the project.
 */
public final class TransformerOps {

    private static final Logger log = LoggerFactory.getLogger(TransformerOps.class);

    private TransformerOps() {
    }

    /**
     * Forward pass through the transformer.
     *
     * @param m       model whose parameters to use
     * @param idx     int64 token-ids, shape {@code (B, T)}
     * @param manager NDManager used for any intermediate allocations
     * @return logits, shape {@code (B, T, vocabSize)}
     */
    public static NDArray forward(MiniTransformer m, NDArray idx, NDManager manager) {
        long B = idx.getShape().get(0);
        long T = idx.getShape().get(1);
        int C = m.getNEmbd();
        int nHead = m.getNHead();
        int headDim = m.getHeadDim();

        // x = wte[idx] + wpe[:T] -> (B, T, C)
        NDArray tok = pickRows(m.getWte(), idx);
        NDArray pos = m.getWpe().get(":" + T + ",:").reshape(1, T, C);
        NDArray x = tok.add(pos);

        // Causal mask: (T, T), upper triangle (j>i) = -1e10
        float[] maskData = new float[(int) T * (int) T];
        for (int i = 0; i < T; i++) {
            for (int j = 0; j < T; j++) {
                if (j > i) maskData[(int) (i * T + j)] = -1e10f;
            }
        }
        NDArray mask = createFloat(manager, maskData, new Shape(T, T));

        for (int li = 0; li < m.getNLayer(); li++) {
            String p = "blk" + li + "_";
            // Pre-attn layer norm
            NDArray xn = layerNorm(x,
                    m.getBlockParam(p + "ln1_g"),
                    m.getBlockParam(p + "ln1_b"));
            // Q, K, V projections
            NDArray q = linear(xn, m.getBlockParam(p + "wq"));
            NDArray k = linear(xn, m.getBlockParam(p + "wk"));
            NDArray v = linear(xn, m.getBlockParam(p + "wv"));
            // split heads: (B, T, nHead, headDim) -> (B, nHead, T, headDim)
            q = q.reshape(B, T, nHead, headDim).transpose(0, 2, 1, 3);
            k = k.reshape(B, T, nHead, headDim).transpose(0, 2, 1, 3);
            v = v.reshape(B, T, nHead, headDim).transpose(0, 2, 1, 3);
            // attn = softmax(q @ k^T / sqrt(d) + mask) @ v
            NDArray scores = q.matMul(k.transpose(0, 1, 3, 2))
                    .div((float) Math.sqrt(headDim))
                    .add(mask.reshape(1, 1, T, T));
            NDArray attn = softmax(scores, -1).matMul(v);
            // merge heads: (B, nHead, T, headDim) -> (B, T, nHead*headDim)
            attn = attn.transpose(0, 2, 1, 3).reshape(B, T, C);
            // residual + output projection
            x = x.add(linear(attn, m.getBlockParam(p + "wo")));
            // MLP block
            xn = layerNorm(x,
                    m.getBlockParam(p + "ln2_g"),
                    m.getBlockParam(p + "ln2_b"));
            NDArray h = linearBias(xn,
                    m.getBlockParam(p + "fc1_w"),
                    m.getBlockParam(p + "fc1_b"));
            h = gelu(h);
            x = x.add(linearBias(h,
                    m.getBlockParam(p + "fc2_w"),
                    m.getBlockParam(p + "fc2_b")));
        }

        // Final layer norm + tied output projection (uses wte.T)
        x = layerNorm(x, m.getLnFg(), m.getLnFb());
        return x.matMul(m.getWte().transpose());
    }

    /**
     * Manual gather — DJL 0.36 does not expose a stable {@code gather} op
     * across the pytorch engine, so we copy the rows into a fresh
     * {@code (B, T, C)} buffer.
     *
     * @param wte token embedding, shape {@code (V, C)}
     * @param idx token ids,      shape {@code (B, T)}
     * @return gathered rows,     shape {@code (B, T, C)}
     */
    private static NDArray pickRows(NDArray wte, NDArray idx) {
        long B = idx.getShape().get(0);
        long T = idx.getShape().get(1);
        long C = wte.getShape().get(1);
        float[] wteData = wte.toFloatArray();
        long[] idxData = idx.toLongArray();
        float[] out = new float[(int) (B * T * C)];
        for (int b = 0; b < B; b++) {
            for (int t = 0; t < T; t++) {
                int row = (int) idxData[(int) (b * T + t)];
                System.arraycopy(wteData, row * (int) C, out,
                        (int) ((b * T + t) * C), (int) C);
            }
        }
        return createFloat(wte.getManager(), out, new Shape(B, T, C));
    }

    /**
     * Layer-norm with affine transform, computed over the LAST axis of {@code x}.
     * Uses {@code keepdim=true} on the reductions so the broadcast is unambiguous.
     *
     * @param x     input tensor, shape {@code (..., C)}
     * @param gamma scale, shape {@code (C,)}
     * @param beta  bias,  shape {@code (C,)}
     * @return normalised tensor with the same shape as {@code x}
     */
    public static NDArray layerNorm(NDArray x, NDArray gamma, NDArray beta) {
        long[] shape = x.getShape().getShape();
        int last = shape.length - 1;
        NDArray mean = x.mean(new int[]{last}, true);
        NDArray diff = x.sub(mean);
        NDArray var = diff.pow(2).mean(new int[]{last}, true);
        NDArray invStd = var.add(1e-5f).pow(-0.5f);
        return diff.mul(invStd).mul(gamma).add(beta);
    }

    /**
     * Pure matMul {@code x @ w}.
     *
     * @param x left operand,  shape {@code (..., in)}
     * @param w right operand, shape {@code (in, out)}
     * @return shape {@code (..., out)}
     */
    public static NDArray linear(NDArray x, NDArray w) {
        return x.matMul(w);
    }

    /**
     * {@code x @ w + b}. Bias broadcasts over the leading axes of {@code x}.
     *
     * @param x left operand,  shape {@code (..., in)}
     * @param w right operand, shape {@code (in, out)}
     * @param b bias,          shape {@code (out,)}
     * @return shape {@code (..., out)}
     */
    public static NDArray linearBias(NDArray x, NDArray w, NDArray b) {
        return x.matMul(w).add(b);
    }

    /**
     * Tanh-approximation GELU. Falls back to a pure Java loop on the flat
     * float buffer because the element-wise exp/tanh in the pytorch engine
     * shows up as separate ops and is much slower on small tensors.
     *
     * @param x input tensor
     * @return element-wise GELU, same shape as {@code x}
     */
    public static NDArray gelu(NDArray x) {
        float[] in = x.toFloatArray();
        double c = Math.sqrt(2.0 / Math.PI);
        for (int i = 0; i < in.length; i++) {
            double inner = c * (in[i] + 0.044715 * in[i] * in[i] * in[i]);
            in[i] = (float) (0.5 * in[i] * (1.0 + Math.tanh(inner)));
        }
        return createFloat(x.getManager(), in, x.getShape());
    }

    /**
     * Numerically-stable softmax along the given axis. The engine's
     * {@code softmax} helper has different broadcasting behaviour across
     * versions, so we do {@code x - max} explicitly.
     *
     * @param x    input tensor
     * @param axis axis to softmax over
     * @return tensor of the same shape with values in {@code [0, 1]} summing to 1
     */
    public static NDArray softmax(NDArray x, int axis) {
        NDArray max = x.max(new int[]{axis}, true);
        NDArray e = x.sub(max).exp();
        return e.div(e.sum(new int[]{axis}, true));
    }

    /**
     * Cross-entropy loss returning a 1-D (1,) NDArray. Useful when the caller
     * wants to keep everything in NDArray land.
     *
     * @param logits  shape {@code (B, T, V)}
     * @param targets shape {@code (B, T)} — class indices
     * @return (1,) loss value
     */
    public static NDArray crossEntropy(NDArray logits, NDArray targets) {
        long[] shape = logits.getShape().getShape();
        int last = shape.length - 1;
        long V = shape[last];
        long n = logits.size() / V;
        NDArray max = logits.max(new int[]{last}, true);
        NDArray shifted = logits.sub(max);
        NDArray logSum = shifted.exp().sum(new int[]{last}, true).log();
        NDArray logSoftmax = shifted.sub(logSum);
        NDArray flatLogits = logSoftmax.reshape(n, V);
        NDArray flatTargets = targets.reshape(n);
        float mean = crossEntropyImpl(flatLogits, flatTargets);
        return createFloat(logits.getManager(), new float[]{mean}, new Shape(1));
    }

    /**
     * Cross-entropy returning a plain Java {@code float}. Sidesteps the
     * DJL 0.36 pytorch engine scalar-tensor path that throws
     * "Index N out of bounds for length 32".
     *
     * @param logits  shape {@code (B, T, V)}
     * @param targets shape {@code (B, T)}
     * @return scalar mean loss
     */
    public static float crossEntropyScalar(NDArray logits, NDArray targets) {
        long[] shape = logits.getShape().getShape();
        int last = shape.length - 1;
        long V = shape[last];
        long n = logits.size() / V;
        NDArray max = logits.max(new int[]{last}, true);
        NDArray shifted = logits.sub(max);
        NDArray logSum = shifted.exp().sum(new int[]{last}, true).log();
        NDArray logSoftmax = shifted.sub(logSum);
        NDArray flatLogits = logSoftmax.reshape(n, V);
        NDArray flatTargets = targets.reshape(n);
        return crossEntropyImpl(flatLogits, flatTargets);
    }

    /**
     * Implementation shared by the two {@code crossEntropy} entry points.
     * Computes {@code -mean(flatLogits[i, target[i]])} by indexing into the
     * flat float buffers on the JVM side.
     */
    private static float crossEntropyImpl(NDArray flatLogits, NDArray flatTargets) {
        float[] flatData = flatLogits.toFloatArray();
        long[] idxData = flatTargets.toLongArray();
        long n = flatLogits.getShape().get(0);
        long V = flatLogits.getShape().get(1);
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += flatData[(int) (i * V + idxData[i])];
        }
        return (float) (-sum / n);
    }

    /**
     * In-place Adam update. The DJL NDArrays are immutable, so we return a
     * new tensor pair (param', m', v') and let the caller assign back.
     *
     * @param param parameter tensor to update
     * @param grad  gradient of the same shape
     * @param m     first moment, same shape
     * @param v     second moment, same shape
     * @param t     timestep (1-based)
     * @param lr    learning rate
     * @param beta1 first-moment decay (typ. 0.9)
     * @param beta2 second-moment decay (typ. 0.95)
     * @param eps   numerical floor (typ. 1e-8)
     * @return new (param', m', v') bundle — see {@link AdamResult}
     */
    public static AdamResult adamStep(NDArray param, NDArray grad, NDArray m, NDArray v,
                                      int t, double lr, double beta1, double beta2, double eps) {
        NDArray newM = m.mul(beta1).add(grad.mul(1.0 - beta1));
        NDArray newV = v.mul(beta2).add(grad.pow(2).mul(1.0 - beta2));
        NDArray mHat = newM.div(1.0 - Math.pow(beta1, t));
        NDArray vHat = newV.div(1.0 - Math.pow(beta2, t));
        NDArray update = mHat.div(vHat.pow(0.5f).add((float) eps)).mul((float) lr);
        NDArray newParam = param.sub(update);
        return new AdamResult(newParam, newM, newV);
    }

    /**
     * Result of {@link #adamStep}: caller assigns {@code newParam} back to the
     * model slot and replaces the Adam state with {@code newM}/{@code newV}.
     */
    public record AdamResult(NDArray newParam, NDArray newM, NDArray newV) {}

    /**
     * Byte-level autoregressive sampling. Each call appends {@code maxTokens}
     * new ids to {@code prompt} and returns the full sequence.
     *
     * @param m          trained model
     * @param prompt     initial token ids
     * @param maxTokens  number of new tokens to generate
     * @param temperature 0 → greedy argmax; >0 → softmax sampling
     * @param manager    NDManager for any intermediate allocations
     * @return full token sequence, length {@code prompt.length + maxTokens}
     */
    public static int[] generate(MiniTransformer m, int[] prompt, int maxTokens, float temperature, NDManager manager) {
        List<Integer> ids = new ArrayList<>();
        for (int b : prompt) ids.add(b);
        for (int step = 0; step < maxTokens; step++) {
            int T = Math.min(m.getBlockSize(), ids.size());
            int[] ctx = new int[T];
            for (int i = 0; i < T; i++) {
                ctx[i] = ids.get(ids.size() - T + i);
            }
            NDArray idx = manager.create(ByteBuffer.wrap(toBytes(ctx)),
                    new Shape(1, T), ai.djl.ndarray.types.DataType.INT64);
            NDArray logits = forward(m, idx, manager);
            NDArray last = logits.get("0," + (T - 1) + ",:");
            float[] p = last.toFloatArray();
            int pick;
            if (temperature <= 0) {
                pick = 0;
                for (int i = 1; i < p.length; i++) if (p[i] > p[pick]) pick = i;
            } else {
                for (int i = 0; i < p.length; i++) p[i] /= temperature;
                float max = -1e30f;
                for (float v : p) if (v > max) max = v;
                double sum = 0;
                for (int i = 0; i < p.length; i++) {
                    p[i] = (float) Math.exp(p[i] - max);
                    sum += p[i];
                }
                double r = Math.random() * sum;
                double acc = 0;
                pick = p.length - 1;
                for (int i = 0; i < p.length; i++) {
                    acc += p[i];
                    if (acc >= r) { pick = i; break; }
                }
            }
            ids.add(pick);
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return out;
    }

    /**
     * Pack a long[] (token ids) into a little-endian byte[] for INT64 NDArray creation.
     */
    private static byte[] toBytes(int[] arr) {
        ByteBuffer bb = ByteBuffer.allocate(arr.length * 4);
        bb.asIntBuffer().put(arr);
        return bb.array();
    }

    /**
     * The ONLY safe way to materialise a float[] as a multi-D NDArray in DJL 0.36.
     * Uses a heap {@link ByteBuffer} with explicit {@code LITTLE_ENDIAN} order
     * and a {@code position(0)} before delegating to
     * {@code NDManager.create(Buffer, Shape, DataType)}.
     *
     * @param m     NDManager owner
     * @param data  raw float data, length must equal {@code shape.numElements()}
     * @param shape requested shape
     * @return NDArray with the given shape and values
     */
    public static NDArray createFloat(NDManager m, float[] data, Shape shape) {
        long total = 1;
        for (long d : shape.getShape()) total *= d;
        if (total != data.length) {
            throw new IllegalArgumentException(
                    "createFloat: data.length=" + data.length + " != shape size " + total);
        }
        ByteBuffer bb = ByteBuffer.allocate(data.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.asFloatBuffer().put(data);
        bb.position(0);
        return m.create(bb, shape, ai.djl.ndarray.types.DataType.FLOAT32);
    }
}
