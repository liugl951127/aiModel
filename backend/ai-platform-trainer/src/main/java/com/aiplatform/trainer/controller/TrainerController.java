package com.aiplatform.trainer.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.trainer.constraints.HallucinationGuardConfig;
import com.aiplatform.trainer.dataset.CorpusAugmenter;
import com.aiplatform.trainer.preview.PreviewService;
import com.aiplatform.trainer.service.MiniTransformerTrainer;
import com.aiplatform.trainer.service.TrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * REST + SSE surface for the trainer.
 */
@Slf4j
@RestController
@RequestMapping("/api/trainer")
@RequiredArgsConstructor
public class TrainerController {

    private final TrainingService trainingService;
    private final PreviewService previewService;
    private final CorpusAugmenter augmenter;

    /**
     * 可用的 trainer 模型列表 (静态). 供前端 Train.vue 选模型下拉用.
     *
     * <p>id      - trainerId (训练任务里标识)
     * <br>displayName - 展示名
     * <br>hyperParams - 调参表单</p>
     */
    @GetMapping("/models")
    public Result<List<Map<String, Object>>> models() {
        List<Map<String, Object>> models = new java.util.ArrayList<>();
        models.add(modelOf("minigpt",   "MiniGPT  (小型 Transformer, 6 层, 6 头)", "mini"));
        models.add(modelOf("minigpt2",  "MiniGPT2 (4 层, 4 头, 快速实验)",         "mini"));
        models.add(modelOf("llama-mini","LLaMA-Mini (LLaMA 风格, RMSNorm + RoPE)", "llama"));
        return Result.success(models);
    }

    private static Map<String, Object> modelOf(String id, String displayName, String family) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("displayName", displayName);
        m.put("family", family);
        m.put("hyperParams", java.util.List.of(
                Map.of("key", "nLayer",   "label", "层数 (n_layer)",   "type", "number", "default", 6,  "min", 1,  "max", 24),
                Map.of("key", "nHead",    "label", "头数 (n_head)",    "type", "number", "default", 6,  "min", 1,  "max", 24),
                Map.of("key", "nEmbd",    "label", "嵌入维度 (n_embd)", "type", "number", "default", 192,"min", 64, "max", 768),
                Map.of("key", "blockSize","label", "上下文 (block_size)","type","number","default", 64, "min", 16, "max", 512),
                Map.of("key", "maxIters", "label", "迭代数 (max_iters)","type", "number", "default", 200,"min", 10, "max", 5000),
                Map.of("key", "batchSize","label", "批大小 (batch_size)","type","number", "default", 12, "min", 1,  "max", 64),
                Map.of("key", "lr",       "label", "学习率 (lr)",       "type", "number", "default", 0.001,"step", 0.0001)
        ));
        return m;
    }

    @PostMapping("/submit")
    public Result<TrainingService.JobState> submit(@RequestBody Map<String, Object> body) {
        String corpusPath = (String) body.get("corpusPath");
        if (corpusPath == null || corpusPath.isBlank()) {
            return Result.fail(400, "corpusPath is required");
        }
        // ★ v3.x 预校验: 路径必须存在且可读, 避免异步训练时报 NoSuchFileException 难查
        java.nio.file.Path p = java.nio.file.Paths.get(corpusPath);
        if (!java.nio.file.Files.exists(p)) {
            return Result.fail(400, "corpusPath not found: " + corpusPath
                    + " (absolute: " + p.toAbsolutePath()
                    + "). Windows 上 '\\opt\\...' 是错误写法, 应是 'C:\\...' 或 WSL 路径");
        }
        if (!java.nio.file.Files.isReadable(p)) {
            return Result.fail(400, "corpusPath not readable: " + corpusPath);
        }
        MiniTransformerTrainer.Config cfg = new MiniTransformerTrainer.Config();
        if (body.get("modelType") != null) cfg.modelType = (String) body.get("modelType");
        if (body.get("maxIters") != null) cfg.maxIters = ((Number) body.get("maxIters")).intValue();
        if (body.get("blockSize") != null) cfg.blockSize = ((Number) body.get("blockSize")).intValue();
        if (body.get("nLayer") != null) cfg.nLayer = ((Number) body.get("nLayer")).intValue();
        if (body.get("nHead") != null) cfg.nHead = ((Number) body.get("nHead")).intValue();
        if (body.get("nEmbd") != null) cfg.nEmbd = ((Number) body.get("nEmbd")).intValue();
        if (body.get("batchSize") != null) cfg.batchSize = ((Number) body.get("batchSize")).intValue();
        if (body.get("learningRate") != null) cfg.learningRate = ((Number) body.get("learningRate")).doubleValue();
        if (body.get("knowledgeKbId") != null) cfg.knowledgeKbId = ((Number) body.get("knowledgeKbId")).longValue();
        if (body.get("knowledgeSeedTopics") instanceof List<?> topics) {
            cfg.knowledgeSeedTopics = topics.stream().map(Object::toString).toList();
        }
        // guard
        if (body.get("guard") instanceof Map<?, ?> gmap) {
            cfg.guard = parseGuard(gmap);
        } else if (Boolean.TRUE.equals(body.get("strictGuard"))) {
            cfg.guard = HallucinationGuardConfig.strict();
        }
        return Result.success(trainingService.submit(corpusPath, cfg));
    }

    /**
     * Subscribe to live preview events for a job. The training loop will
     * push loss/iter metrics, and a separate {@code POST /preview/generate}
     * can be called to ask the model (in its current state) to produce a
     * sample under the job's guard config.
     */
    /**
     * LoRA 微调: 演示启动 LoRA 训练任务.
     */
    @PostMapping("/lora")
    public Result<Map<String, Object>> lora(@RequestBody Map<String, Object> body) {
        String base = (String) body.getOrDefault("baseModel", "llama-3-8b");
        int rank = body.get("rank") == null ? 16 : ((Number) body.get("rank")).intValue();
        int alpha = body.get("alpha") == null ? 32 : ((Number) body.get("alpha")).intValue();
        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("jobId", "lora-" + System.currentTimeMillis());
        ret.put("baseModel", base);
        ret.put("rank", rank);
        ret.put("alpha", alpha);
        ret.put("status", "submitted");
        log.info("[TRAINER] LoRA submit: base={}, rank={}, alpha={}", base, rank, alpha);
        return Result.success(ret);
    }

    /**
     * DPO 训练: 直接偏好优化.
     */
    @PostMapping("/dpo")
    public Result<Map<String, Object>> dpo(@RequestBody Map<String, Object> body) {
        double beta = body.get("beta") == null ? 0.1 : ((Number) body.get("beta")).doubleValue();
        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("jobId", "dpo-" + System.currentTimeMillis());
        ret.put("beta", beta);
        ret.put("status", "submitted");
        log.info("[TRAINER] DPO submit: beta={}", beta);
        return Result.success(ret);
    }

    @GetMapping("/preview/{jobId}/subscribe")
    public SseEmitter subscribe(@PathVariable("jobId") String jobId) {
        log.info("[TRAINER] SSE subscribe jobId={}", jobId);
        return previewService.subscribe(jobId);
    }

    /**
     * Trigger a live generation against the model's current state. The
     * trainer snapshots the model at submit-time; here we run a generation
     * using the same model held in memory.
     */
    @PostMapping("/preview/{jobId}/generate")
    public Result<String> preview(@PathVariable("jobId") String jobId,
                                  @RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "");
        int maxTokens = body.get("maxTokens") == null ? 80 : ((Number) body.get("maxTokens")).intValue();
        // we can't access the model directly from the service (it's local
        // to the worker thread); emit a "preview scheduled" event and let
        // the worker pick it up if it supports live replay.
        previewService.publishMetric(jobId, "preview_requested", maxTokens);
        return Result.success("preview scheduled");
    }

    /** Default vs strict guard presets. */
    @GetMapping("/guard/presets")
    public Result<Map<String, Object>> presets() {
        return Result.success(Map.of(
                "default", HallucinationGuardConfig.defaults(),
                "strict", HallucinationGuardConfig.strict()
        ));
    }

    /** Build Q/A pairs from a knowledge base — used by the dataset tab. */
    @PostMapping("/dataset/qa")
    public Result<List<CorpusAugmenter.Pair>> buildQA(@RequestBody Map<String, Object> body) {
        Long kbId = body.get("kbId") == null ? null : ((Number) body.get("kbId")).longValue();
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) body.getOrDefault("topics", List.of());
        return Result.success(augmenter.buildQAPairs(kbId, topics));
    }

    @GetMapping("/job/{id}")
    public Result<Map<String, Object>> get(@PathVariable String id) {
        // 优先取内存 (实时 SSE), 没取 DB (历史快照)
        TrainingService.JobState live = trainingService.get(id);
        if (live != null) {
            Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("source", "memory");
            r.put("jobId", live.getJobId());
            r.put("status", live.getStatus());
            r.put("progress", live.getProgress());
            r.put("finalLoss", live.getFinalLoss());
            r.put("error", live.getError());
            r.put("startedAt", live.getStartedAt());
            r.put("finishedAt", live.getFinishedAt());
            return Result.success(r);
        }
        // DB 快照 (服务重启后)
        com.aiplatform.trainer.entity.TrainJobEntity dbJob = trainingService.getFromDb(id);
        if (dbJob == null) return Result.fail(404, "Job not found: " + id);
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("source", "db");
        r.put("jobId", dbJob.getJobCode());
        r.put("status", dbJob.getStatus());
        r.put("progress", dbJob.getProgress());
        r.put("metrics", dbJob.getMetrics());
        r.put("errorMessage", dbJob.getErrorMessage());
        r.put("startedAt", dbJob.getStartedAt());
        r.put("finishedAt", dbJob.getFinishedAt());
        r.put("outputPath", dbJob.getOutputPath());
        return Result.success(r);
    }

    /**
     * 训练任务列表 — 优先 DB (重启不丢), 叠加内存 (实时状态).
     */
    @GetMapping("/jobs")
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        // 1. DB 历史
        for (com.aiplatform.trainer.entity.TrainJobEntity e : trainingService.listFromDb(limit)) {
            Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("id", e.getJobCode());
            r.put("modelCode", e.getModelCode());
            r.put("algorithm", e.getAlgorithm());
            r.put("status", e.getStatus());
            r.put("progress", e.getProgress());
            r.put("epochs", e.getEpochs());
            r.put("batchSize", e.getBatchSize());
            r.put("learningRate", e.getLearningRate());
            r.put("startedAt", e.getStartedAt());
            r.put("finishedAt", e.getFinishedAt());
            r.put("outputPath", e.getOutputPath());
            r.put("errorMessage", e.getErrorMessage());
            // metrics JSON 提取 finalLoss
            if (e.getMetrics() != null) {
                try {
                    com.alibaba.fastjson2.JSONObject m = com.alibaba.fastjson2.JSON.parseObject(e.getMetrics());
                    if (m != null) r.put("finalLoss", m.getDoubleValue("finalLoss"));
                } catch (Exception ignore) { /* not json */ }
            }
            r.put("source", "db");
            rows.add(r);
        }
        // 2. 内存 (实时, 覆盖 DB 同 jobCode)
        Map<String, TrainingService.JobState> live = trainingService.all();
        for (Map.Entry<String, TrainingService.JobState> en : live.entrySet()) {
            TrainingService.JobState j = en.getValue();
            Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("id", j.getJobId());
            r.put("status", j.getStatus());
            r.put("progress", j.getProgress());
            r.put("finalLoss", j.getFinalLoss());
            r.put("startedAt", j.getStartedAt());
            r.put("finishedAt", j.getFinishedAt());
            r.put("source", "memory");
            // 覆盖 DB 行
            rows.removeIf(x -> id_equals(x, j.getJobId()));
            rows.add(0, r);  // 实时状态放最前
        }
        return Result.success(rows);
    }

    private static boolean id_equals(Map<String, Object> row, String id) {
        return id.equals(row.get("id"));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("trainer OK");
    }

    /* --------- helpers --------- */
    @SuppressWarnings("unchecked")
    private HallucinationGuardConfig parseGuard(Map<?, ?> m) {
        HallucinationGuardConfig c = HallucinationGuardConfig.defaults();
        if (m.get("topK") != null) c.setTopK(((Number) m.get("topK")).intValue());
        if (m.get("topP") != null) c.setTopP(((Number) m.get("topP")).floatValue());
        if (m.get("temperature") != null) c.setTemperature(((Number) m.get("temperature")).floatValue());
        if (m.get("repetitionPenalty") != null) c.setRepetitionPenalty(((Number) m.get("repetitionPenalty")).floatValue());
        if (m.get("maxConsecutiveUnknowns") != null) c.setMaxConsecutiveUnknowns(((Number) m.get("maxConsecutiveUnknowns")).intValue());
        if (m.get("knowledgeGrounding") != null) c.setKnowledgeGrounding((Boolean) m.get("knowledgeGrounding"));
        if (m.get("knowledgeKbId") != null) c.setKnowledgeKbId(((Number) m.get("knowledgeKbId")).longValue());
        if (m.get("knowledgeTopK") != null) c.setKnowledgeTopK(((Number) m.get("knowledgeTopK")).intValue());
        if (m.get("minConfidence") != null) c.setMinConfidence(((Number) m.get("minConfidence")).floatValue());
        if (m.get("maxAnswerTokens") != null) c.setMaxAnswerTokens(((Number) m.get("maxAnswerTokens")).intValue());
        if (m.get("factCheck") != null) c.setFactCheck((Boolean) m.get("factCheck"));
        if (m.get("minFactOverlap") != null) c.setMinFactOverlap(((Number) m.get("minFactOverlap")).floatValue());
        if (m.get("knowledgeDocIds") instanceof List<?> ids) {
            c.setKnowledgeDocIds(ids.stream().map(o -> ((Number) o).longValue()).toList());
        }
        return c;
    }
}
