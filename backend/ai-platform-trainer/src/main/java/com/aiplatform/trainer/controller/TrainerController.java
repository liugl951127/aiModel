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

    @PostMapping("/submit")
    public Result<TrainingService.JobState> submit(@RequestBody Map<String, Object> body) {
        String corpusPath = (String) body.get("corpusPath");
        if (corpusPath == null || corpusPath.isBlank()) {
            return Result.fail(400, "corpusPath is required");
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
    public Result<TrainingService.JobState> get(@PathVariable String id) {
        TrainingService.JobState job = trainingService.get(id);
        if (job == null) return Result.fail(404, "Job not found: " + id);
        return Result.success(job);
    }

    @GetMapping("/jobs")
    public Result<Map<String, TrainingService.JobState>> list() {
        return Result.success(trainingService.all());
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
