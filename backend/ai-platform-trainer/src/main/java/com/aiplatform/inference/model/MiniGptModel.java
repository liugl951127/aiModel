package com.aiplatform.inference.model;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.ResultCode;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * In-process Mini-GPT model. Loads a self-contained JSON bundle produced by
 * the Python {@code export_onnx.py} script. No ONNX Runtime dependency required
 * for the bundled model type (the heavy onnxruntime jar is still on the classpath
 * for future ONNX-format model support).
 */
@Slf4j
public class MiniGptModel {

    private final ModelConfig config;
    private final Map<String, float[]> weights;

    private MiniGptModel(ModelConfig cfg, Map<String, float[]> weights) {
        this.config = cfg;
        this.weights = weights;
    }

    public static MiniGptModel loadFromDirectory(Path dir) {
        try {
            String cfgJson = Files.readString(dir.resolve("config.json"), StandardCharsets.UTF_8);
            String wJson = Files.readString(dir.resolve("weights.json"), StandardCharsets.UTF_8);
            ModelConfig cfg = JSON.parseObject(cfgJson, ModelConfig.class);
            JSONObject wObj = JSON.parseObject(wJson);
            Map<String, float[]> weights = new HashMap<>();
            for (String name : wObj.keySet()) {
                String b64 = wObj.getString(name);
                weights.put(name, decode(b64));
            }
            log.info("[MODEL] loaded bundle vocab={} ctx={} embd={} layer={} head={}",
                    cfg.vocabSize, cfg.blockSize, cfg.nEmbd, cfg.nLayer, cfg.nHead);
            return new MiniGptModel(cfg, weights);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.MODEL_INFERENCE_ERROR, "加载模型失败: " + e.getMessage());
        }
    }

    private static float[] decode(String b64) {
        byte[] bytes = Base64.getDecoder().decode(b64);
        float[] f = new float[bytes.length / 4];
        for (int i = 0; i < f.length; i++) {
            int b = ((bytes[i * 4] & 0xff))
                    | ((bytes[i * 4 + 1] & 0xff) << 8)
                    | ((bytes[i * 4 + 2] & 0xff) << 16)
                    | ((bytes[i * 4 + 3] & 0xff) << 24);
            f[i] = Float.intBitsToFloat(b);
        }
        return f;
    }

    // ----------------------------------------------------------------------
    // Forward
    // ----------------------------------------------------------------------

    /**
     * Greedy/sampling next-token generation.
     */
    public String generate(String prompt, int maxTokens, float temperature) {
        byte[] promptBytes = prompt.getBytes(StandardCharsets.UTF_8);
        int[] ids = new int[promptBytes.length];
        for (int i = 0; i < promptBytes.length; i++) {
            ids[i] = promptBytes[i] & 0xff;
        }
        for (int step = 0; step < maxTokens; step++) {
            int[] ctx = takeLast(ids, config.blockSize);
            float[] logits = forwardLastToken(ctx);
            int nextId = sample(logits, temperature);
            int[] newIds = new int[ids.length + 1];
            System.arraycopy(ids, 0, newIds, 0, ids.length);
            newIds[ids.length] = nextId;
            ids = newIds;
        }
        byte[] out = new byte[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = (byte) (ids[i] & 0xff);
        return new String(out, StandardCharsets.UTF_8);
    }

    private int[] takeLast(int[] ids, int n) {
        if (ids.length <= n) return ids;
        int[] out = new int[n];
        System.arraycopy(ids, ids.length - n, out, 0, n);
        return out;
    }

    private float[] forwardLastToken(int[] ctx) {
        int T = ctx.length;
        int B = 1;
        int C = config.nEmbd;
        int nHead = config.nHead;
        int headDim = C / nHead;

        float[] x = new float[B * T * C];
        for (int t = 0; t < T; t++) {
            int id = ctx[t];
            float[] row = row("wte", id, C);
            float[] prow = row("wpe", t, C);
            for (int c = 0; c < C; c++) {
                x[(t * C) + c] = row[c] + prow[c];
            }
        }

        for (int layer = 0; layer < config.nLayer; layer++) {
            float[] xNorm = layerNorm(x, "blk" + layer + "_ln1_g", "blk" + layer + "_ln1_b", T, C);
            float[] q = matmul(xNorm, "blk" + layer + "_wq", T, C, C);
            float[] k = matmul(xNorm, "blk" + layer + "_wk", T, C, C);
            float[] v = matmul(xNorm, "blk" + layer + "_wv", T, C, C);

            float[] attnOut = selfAttention(q, k, v, T, nHead, headDim);
            float[] proj = matmul(attnOut, "blk" + layer + "_wo", T, C, C);
            x = addInPlace(x, proj, T * C);

            float[] xNorm2 = layerNorm(x, "blk" + layer + "_ln2_g", "blk" + layer + "_ln2_b", T, C);
            float[] h = matmulBias(xNorm2, "blk" + layer + "_fc1_w", "blk" + layer + "_fc1_b", T, C, 4 * C);
            for (int i = 0; i < h.length; i++) h[i] = gelu(h[i]);
            float[] mOut = matmulBias(h, "blk" + layer + "_fc2_w", "blk" + layer + "_fc2_b", T, 4 * C, C);
            x = addInPlace(x, mOut, T * C);
        }

        float[] xn = layerNorm(x, "ln_f_g", "ln_f_b", T, C);
        // logits over vocab via tied weights
        float[] wte = weights.get("wte");
        int V = config.vocabSize;
        float[] lastLogits = new float[V];
        int lastTokenOffset = (T - 1) * C;
        for (int v = 0; v < V; v++) {
            int rowStart = v * C;
            float s = 0f;
            for (int c = 0; c < C; c++) {
                s += xn[lastTokenOffset + c] * wte[rowStart + c];
            }
            lastLogits[v] = s;
        }
        return lastLogits;
    }

    private float[] selfAttention(float[] q, float[] k, float[] v, int T, int nHead, int headDim) {
        // q,k,v shape: (T, C) where C = nHead * headDim, laid out per token.
        // Return (T, C) where each token's slice is the head concat.
        float[] out = new float[T * nHead * headDim];
        float[] scores = new float[T * T];
        for (int h = 0; h < nHead; h++) {
            for (int tq = 0; tq < T; tq++) {
                for (int tk = 0; tk < T; tk++) {
                    float s = 0f;
                    for (int d = 0; d < headDim; d++) {
                        s += q[tq * nHead * headDim + h * headDim + d]
                                * k[tk * nHead * headDim + h * headDim + d];
                    }
                    scores[tq * T + tk] = s / (float) Math.sqrt(headDim);
                }
            }
            // causal mask
            for (int tq = 0; tq < T; tq++) {
                for (int tk = tq + 1; tk < T; tk++) {
                    scores[tq * T + tk] = -1e10f;
                }
            }
            // softmax per row
            for (int tq = 0; tq < T; tq++) {
                float max = -1e30f;
                for (int tk = 0; tk < T; tk++) max = Math.max(max, scores[tq * T + tk]);
                float sum = 0f;
                for (int tk = 0; tk < T; tk++) {
                    scores[tq * T + tk] = (float) Math.exp(scores[tq * T + tk] - max);
                    sum += scores[tq * T + tk];
                }
                for (int tk = 0; tk < T; tk++) {
                    scores[tq * T + tk] /= sum;
                }
            }
            // weighted sum
            for (int tq = 0; tq < T; tq++) {
                for (int d = 0; d < headDim; d++) {
                    float s = 0f;
                    for (int tk = 0; tk < T; tk++) {
                        s += scores[tq * T + tk] * v[tk * nHead * headDim + h * headDim + d];
                    }
                    out[tq * nHead * headDim + h * headDim + d] = s;
                }
            }
        }
        return out;
    }

    private float[] matmul(float[] x, String wKey, int T, int inDim, int outDim) {
        float[] w = weights.get(wKey);
        float[] out = new float[T * outDim];
        for (int t = 0; t < T; t++) {
            for (int o = 0; o < outDim; o++) {
                float s = 0f;
                for (int i = 0; i < inDim; i++) {
                    s += x[t * inDim + i] * w[i * outDim + o];
                }
                out[t * outDim + o] = s;
            }
        }
        return out;
    }

    private float[] matmulBias(float[] x, String wKey, String bKey, int T, int inDim, int outDim) {
        float[] w = weights.get(wKey);
        float[] b = weights.get(bKey);
        float[] out = new float[T * outDim];
        for (int t = 0; t < T; t++) {
            for (int o = 0; o < outDim; o++) {
                float s = b[o];
                for (int i = 0; i < inDim; i++) {
                    s += x[t * inDim + i] * w[i * outDim + o];
                }
                out[t * outDim + o] = s;
            }
        }
        return out;
    }

    private float[] layerNorm(float[] x, String gKey, String bKey, int T, int C) {
        float[] g = weights.get(gKey);
        float[] b = weights.get(bKey);
        float[] out = new float[T * C];
        for (int t = 0; t < T; t++) {
            float mean = 0f;
            for (int c = 0; c < C; c++) mean += x[t * C + c];
            mean /= C;
            float var = 0f;
            for (int c = 0; c < C; c++) {
                float d = x[t * C + c] - mean;
                var += d * d;
            }
            var /= C;
            float inv = (float) (1.0 / Math.sqrt(var + 1e-5));
            for (int c = 0; c < C; c++) {
                out[t * C + c] = ((x[t * C + c] - mean) * inv) * g[c] + b[c];
            }
        }
        return out;
    }

    private float[] addInPlace(float[] a, float[] b, int n) {
        for (int i = 0; i < n; i++) a[i] += b[i];
        return a;
    }

    private float gelu(float x) {
        return (float) (0.5 * x * (1.0 + Math.tanh(Math.sqrt(2.0 / Math.PI) * (x + 0.044715 * x * x * x))));
    }

    private float[] row(String tensor, int index, int cols) {
        float[] t = weights.get(tensor);
        float[] out = new float[cols];
        System.arraycopy(t, index * cols, out, 0, cols);
        return out;
    }

    private int sample(float[] logits, float temperature) {
        float t = Math.max(0.1f, temperature);
        for (int i = 0; i < logits.length; i++) logits[i] /= t;
        float max = -1e30f;
        for (float v : logits) if (v > max) max = v;
        float sum = 0f;
        for (int i = 0; i < logits.length; i++) {
            logits[i] = (float) Math.exp(logits[i] - max);
            sum += logits[i];
        }
        float r = (float) Math.random() * sum;
        float acc = 0f;
        for (int i = 0; i < logits.length; i++) {
            acc += logits[i];
            if (acc >= r) return i;
        }
        return logits.length - 1;
    }

    @Data
    public static class ModelConfig {
        private String modelType;
        private String framework;
        @com.alibaba.fastjson2.annotation.JSONField(name = "vocab_size")
        private Integer vocabSize;
        @com.alibaba.fastjson2.annotation.JSONField(name = "block_size")
        private Integer blockSize;
        @com.alibaba.fastjson2.annotation.JSONField(name = "n_embd")
        private Integer nEmbd;
        @com.alibaba.fastjson2.annotation.JSONField(name = "n_layer")
        private Integer nLayer;
        @com.alibaba.fastjson2.annotation.JSONField(name = "n_head")
        private Integer nHead;
        @com.alibaba.fastjson2.annotation.JSONField(name = "head_dim")
        private Integer headDim;
    }
}
