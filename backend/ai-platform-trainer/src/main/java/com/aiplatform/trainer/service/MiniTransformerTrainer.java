package com.aiplatform.trainer.service;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import com.aiplatform.trainer.constraints.HallucinationGuardConfig;
import com.aiplatform.trainer.model.MiniTransformer;
import com.aiplatform.trainer.model.TransformerOps;
import com.aiplatform.trainer.preview.PreviewService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Byte-level Mini-Transformer trainer. Pure Java, single file.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>Load corpus from disk (UTF-8 bytes, padded if too small)</li>
 *   <li>Tokenize byte-level (vocab=256)</li>
 *   <li>Initialise model via {@link MiniTransformer#initialize}</li>
 *   <li>Run cross-entropy + approximate Adam steps on the embedding surface</li>
 *   <li>Export to JSON bundle identical to the Python exporter's format</li>
 * </ol>
 */
@Slf4j
public class MiniTransformerTrainer {

    /**
     * Result bundle returned by {@link #train}.
     */
    public static class TrainResult {
        public final MiniTransformer model;
        public final double finalLoss;
        public final int steps;
        public final Map<String, Double> metrics;
        public final HallucinationGuardConfig guard;

        public TrainResult(MiniTransformer m, double loss, int steps,
                           Map<String, Double> metrics, HallucinationGuardConfig guard) {
            this.model = m;
            this.finalLoss = loss;
            this.steps = steps;
            this.metrics = metrics;
            this.guard = guard;
        }
    }

    /**
     * Hyperparameters. {@code modelType} selects between transformer and lstm.
     */
    public static class Config {
        public String modelType = "transformer";
        public int vocabSize = 256;
        public int blockSize = 64;
        public int nLayer = 4;
        public int nHead = 4;
        public int nEmbd = 128;
        public int batchSize = 16;
        public int maxIters = 200;
        public int evalInterval = 50;
        public double learningRate = 3e-3;
        public HallucinationGuardConfig guard = HallucinationGuardConfig.defaults();
        public Long knowledgeKbId;
        public java.util.List<String> knowledgeSeedTopics;
        // 企业级训练增强
        public String lrSchedule = "warmup_cosine";   // constant / cosine / warmup_cosine / step
        public int lrWarmupSteps = 50;
        public double lrMin = 1e-5;
        public int earlyStopPatience = 50;             // 0=不启用
        public double earlyStopMinDelta = 0.001;
        public int checkpointInterval = 100;            // 0=不保存
        public boolean registerVersion = true;
    }

    private final PreviewService preview;
    private final com.aiplatform.trainer.checkpoint.CheckpointService checkpointService;
    private final com.aiplatform.trainer.version.ModelRegistry modelRegistry;
    private volatile String currentJobId;

    public MiniTransformerTrainer() { this.preview = null; this.checkpointService = null; this.modelRegistry = null; }
    public MiniTransformerTrainer(PreviewService preview) {
        this(preview, null, null);
    }
    public MiniTransformerTrainer(PreviewService preview,
                                  com.aiplatform.trainer.checkpoint.CheckpointService ckpt,
                                  com.aiplatform.trainer.version.ModelRegistry registry) {
        this.preview = preview;
        this.checkpointService = ckpt;
        this.modelRegistry = registry;
    }

    public void bindJobId(String jobId) { this.currentJobId = jobId; }

    public TrainResult train(Path corpusFile, Config cfg) throws IOException {
        log.info("[TRAIN] loading corpus: {}", corpusFile);
        byte[] raw = Files.readAllBytes(corpusFile);
        if (raw.length < cfg.blockSize * 4) {
            byte[] padded = new byte[cfg.blockSize * 8];
            for (int i = 0; i < padded.length; i++) padded[i] = raw[i % raw.length];
            raw = padded;
        }
        log.info("[TRAIN] corpus size: {} bytes", raw.length);

        NDManager manager = NDManager.newBaseManager();
        try {
            MiniTransformer model = new MiniTransformer(cfg.vocabSize, cfg.blockSize,
                    cfg.nLayer, cfg.nHead, cfg.nEmbd);
            model.initialize(manager);

            double[] mBuf = new double[cfg.vocabSize * cfg.nEmbd];
            double[] vBuf = new double[cfg.vocabSize * cfg.nEmbd];
            int t = 0;
            double lastLoss = 0;

            // LR schedule + early stop
            com.aiplatform.trainer.schedule.LRSchedule sched = buildSchedule(cfg);
            com.aiplatform.trainer.schedule.EarlyStopper early = cfg.earlyStopPatience > 0
                    ? new com.aiplatform.trainer.schedule.EarlyStopper(cfg.earlyStopPatience, cfg.earlyStopMinDelta)
                    : null;

            log.info("[TRAIN] start: model={} iters={} batch={} ctx={} lrSchedule={} earlyStop={} ckptInterval={}",
                    cfg.modelType, cfg.maxIters, cfg.batchSize, cfg.blockSize,
                    sched.getType(), early != null, cfg.checkpointInterval);
            for (int iter = 0; iter < cfg.maxIters; iter++) {
                try {
                    int B = cfg.batchSize;
                    int T = cfg.blockSize;
                    long[] xData = new long[B * T];
                    long[] yData = new long[B * T];
                    for (int b = 0; b < B; b++) {
                        int offset = (int) (Math.random() * (raw.length - T - 1));
                        for (int tIdx = 0; tIdx < T; tIdx++) {
                            xData[b * T + tIdx] = raw[offset + tIdx] & 0xffL;
                            yData[b * T + tIdx] = raw[offset + tIdx + 1] & 0xffL;
                        }
                    }
                    NDArray x = manager.create(ByteBuffer.wrap(toBytes(xData)),
                            new ai.djl.ndarray.types.Shape(B, T),
                            ai.djl.ndarray.types.DataType.INT64);
                    NDArray y = manager.create(ByteBuffer.wrap(toBytes(yData)),
                            new ai.djl.ndarray.types.Shape(B, T),
                            ai.djl.ndarray.types.DataType.INT64);

                    NDArray logits = TransformerOps.forward(model, x, manager);
                    lastLoss = TransformerOps.crossEntropyScalar(logits, y);
                    if ((iter + 1) % cfg.evalInterval == 0 || iter == 0) {
                        log.info("[TRAIN] iter {} loss={}", iter + 1, String.format("%.4f", lastLoss));
                        if (preview != null && currentJobId != null) {
                            preview.publishMetric(currentJobId, "loss", lastLoss);
                            preview.publishMetric(currentJobId, "iter", iter + 1);
                        }
                    }

                    // Approximate embedding gradient + Adam
                    float[] wteData = model.getWte().toFloatArray();
                    float[] gradData = new float[wteData.length];
                    for (int b = 0; b < Math.min(B, 4); b++) {
                        for (int tt = 0; tt < T; tt++) {
                            int ti = (int) xData[b * T + tt];
                            int tgt = (int) yData[b * T + tt];
                            if (ti == tgt) continue;
                            float[] row = new float[cfg.nEmbd];
                            System.arraycopy(wteData, ti * cfg.nEmbd, row, 0, cfg.nEmbd);
                            float max = -1e30f;
                            for (float v : row) if (v > max) max = v;
                            double sum = 0;
                            for (int i = 0; i < row.length; i++) {
                                row[i] = (float) Math.exp(row[i] - max);
                                sum += row[i];
                            }
                            for (int i = 0; i < row.length; i++) row[i] /= sum;
                            row[tgt] -= 1f;
                            for (int i = 0; i < row.length; i++) {
                                gradData[ti * cfg.nEmbd + i] += row[i] * 0.001f;
                            }
                        }
                    }
                    for (int i = 0; i < gradData.length; i++) gradData[i] /= Math.max(1, B);
                    t++;
                    double lr = sched.at(iter + 1, cfg.maxIters);
                    for (int i = 0; i < gradData.length; i++) {
                        double g = gradData[i];
                        mBuf[i] = 0.9 * mBuf[i] + 0.1 * g;
                        vBuf[i] = 0.95 * vBuf[i] + 0.05 * g * g;
                        double mHat = mBuf[i] / (1 - Math.pow(0.9, t));
                        double vHat = vBuf[i] / (1 - Math.pow(0.95, t));
                        wteData[i] -= lr * mHat / (Math.sqrt(vHat) + 1e-8);
                    }
                    model.setWte(TransformerOps.createFloat(manager, wteData,
                            new ai.djl.ndarray.types.Shape(cfg.vocabSize, cfg.nEmbd)));

                    // checkpoint
                    if (checkpointService != null && cfg.checkpointInterval > 0
                            && (iter + 1) % cfg.checkpointInterval == 0) {
                        try {
                            checkpointService.save(model, iter + 1, lastLoss, lr, "sched=" + sched.getType());
                        } catch (Exception ce) {
                            log.warn("[CKPT] save failed at iter {}: {}", iter + 1, ce.getMessage());
                        }
                    }
                    // early stop
                    if (early != null && early.observe(lastLoss)) {
                        log.info("[TRAIN] early-stopped at iter {} reason={}", iter + 1, early.reason());
                        break;
                    }
                } catch (Throwable th) {
                    log.error("[TRAIN] iter {} threw:", iter, th);
                    throw th;
                }
            }

            Map<String, Double> metrics = new HashMap<>();
            metrics.put("loss", lastLoss);
            return new TrainResult(model, lastLoss, cfg.maxIters, metrics, cfg.guard);
        } finally {
            manager.close();
        }
    }

    /**
     * Export a trained model to a self-contained JSON bundle.
     */
    public void exportBundle(MiniTransformer model, Path outDir, HallucinationGuardConfig guard) throws IOException {
        Files.createDirectories(outDir);
        com.alibaba.fastjson2.JSONObject cfg = new com.alibaba.fastjson2.JSONObject();
        cfg.put("model_type", "mini-gpt");
        cfg.put("framework", "djl-java");
        cfg.put("export_format", "onnx-bundle");
        cfg.put("vocab_size", model.getVocabSize());
        cfg.put("block_size", model.getBlockSize());
        cfg.put("n_embd", model.getNEmbd());
        cfg.put("n_layer", model.getNLayer());
        cfg.put("n_head", model.getNHead());
        cfg.put("head_dim", model.getHeadDim());
        cfg.put("tokenizer", "byte-level");
        cfg.put("guard", com.alibaba.fastjson2.JSON.parseObject(
                com.alibaba.fastjson2.JSON.toJSONString(guard)));

        com.alibaba.fastjson2.JSONObject weights = new com.alibaba.fastjson2.JSONObject();
        encodeParam(weights, model.getWte(), "wte");
        encodeParam(weights, model.getWpe(), "wpe");
        encodeParam(weights, model.getLnFg(), "ln_f_g");
        encodeParam(weights, model.getLnFb(), "ln_f_b");
        for (MiniTransformer.Parameter p : model.getBlockParams()) {
            encodeParam(weights, p.array(), p.name());
        }

        com.alibaba.fastjson2.JSONObject manifest = new com.alibaba.fastjson2.JSONObject();
        manifest.put("files", java.util.List.of("config.json", "weights.json", "guard.json", "tokenizer.json", "README.md"));

        com.alibaba.fastjson2.JSONObject tokenizer = new com.alibaba.fastjson2.JSONObject();
        tokenizer.put("type", "byte-level");
        tokenizer.put("vocab_size", 256);

        writeJson(outDir.resolve("config.json"), cfg.toJSONString());
        writeJson(outDir.resolve("weights.json"), weights.toJSONString());
        writeJson(outDir.resolve("guard.json"), com.alibaba.fastjson2.JSON.toJSONString(guard));
        writeJson(outDir.resolve("tokenizer.json"), tokenizer.toJSONString());
        writeJson(outDir.resolve("manifest.json"), manifest.toJSONString());
        writeText(outDir.resolve("README.md"),
                "# Mini-GPT Bundle (Java-trained via DJL)\n" +
                        "Includes guard config. Inference runtime should apply HallucinationGuardConfig.\n");
        log.info("[EXPORT] bundle written to {}", outDir);
    }

    private static void encodeParam(com.alibaba.fastjson2.JSONObject out, NDArray arr, String name) {
        float[] data = arr.toFloatArray();
        ByteBuffer bb = ByteBuffer.allocate(data.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.asFloatBuffer().put(data);
        out.put(name, Base64.getEncoder().encodeToString(bb.array()));
    }

    private static byte[] toBytes(long[] arr) {
        byte[] out = new byte[arr.length * 8];
        ByteBuffer bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        bb.asLongBuffer().put(arr);
        return out;
    }

    /**
     * 根据 config 构建 LR schedule。
     */
    private static com.aiplatform.trainer.schedule.LRSchedule buildSchedule(Config cfg) {
        com.aiplatform.trainer.schedule.LRSchedule.Type type;
        try {
            type = com.aiplatform.trainer.schedule.LRSchedule.Type.valueOf(
                    cfg.lrSchedule == null ? "warmup_cosine" : cfg.lrSchedule.toUpperCase());
        } catch (Exception e) {
            type = com.aiplatform.trainer.schedule.LRSchedule.Type.WARMUP_COSINE;
        }
        com.aiplatform.trainer.schedule.LRSchedule s = new com.aiplatform.trainer.schedule.LRSchedule();
        s.setType(type);
        s.setBase(cfg.learningRate);
        s.setMin(cfg.lrMin);
        s.setWarmupSteps(cfg.lrWarmupSteps);
        return s;
    }

    private static void writeJson(Path p, String content) throws IOException {
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    private static void writeText(Path p, String content) throws IOException {
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }
}
