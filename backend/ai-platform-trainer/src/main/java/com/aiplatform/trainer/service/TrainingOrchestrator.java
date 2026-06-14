package com.aiplatform.trainer.service;

import ai.djl.ndarray.NDManager;
import com.aiplatform.trainer.model.ModelTrainer;
import com.aiplatform.trainer.model.constraints.AntiHallucination;
import com.aiplatform.trainer.model.preview.PreviewBus;
import com.aiplatform.trainer.model.preview.PreviewBus.Event;
import com.aiplatform.trainer.model.preview.PreviewBus.EventType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates training jobs across all {@link ModelTrainer} implementations.
 * Keeps a per-job state object, dispatches the work asynchronously, and
 * publishes events to the {@link PreviewBus} so the SSE endpoint can push
 * them to the browser.
 *
 * <p>Anti-hallucination params (e.g. {@code hallucinationThreshold}) are
 * part of the per-job param map; the orchestrator checks the threshold
 * after each step and publishes a WARN event if the model is too
 * uncertain, letting the UI highlight the risk in real time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingOrchestrator {

    private final TrainerRegistry registry;

    /** Root directory for exported bundles. */
    @Value("${aiplatform.trainer.export-root:/opt/ai-platform/inference-bundles}")
    private String exportRoot;

    /** Per-job state. */
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

    /** Mutable parameter overrides pushed from the UI mid-run. */
    private final Map<String, Map<String, Object>> liveParams = new ConcurrentHashMap<>();

    /** Submit + start a new job. */
    public JobState submit(String trainerId, String corpusPath, Map<String, Object> params) {
        ModelTrainer trainer = registry.get(trainerId);
        if (trainer == null) throw new IllegalArgumentException("unknown trainer: " + trainerId);
        // merge defaults + overrides
        Map<String, Object> effective = new LinkedHashMap<>(trainer.defaultParams());
        if (params != null) effective.putAll(params);
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        JobState s = new JobState(jobId, trainerId, corpusPath, effective);
        s.status = "queued";
        jobs.put(jobId, s);
        liveParams.put(jobId, effective);
        runAsync(s);
        return s;
    }

    public JobState get(String id) { return jobs.get(id); }
    public Map<String, JobState> all() { return jobs; }

    /**
     * Update one or more parameters on a running job. The orchestrator
     * reads {@link #liveParams} on every step, so changes take effect on
     * the next step (typically within a few ms).
     */
    public boolean updateParams(String jobId, Map<String, Object> delta) {
        JobState s = jobs.get(jobId);
        if (s == null) return false;
        Map<String, Object> cur = liveParams.get(jobId);
        if (cur == null) return false;
        cur.putAll(delta);
        s.params.putAll(delta);
        log.info("[ORCH] job {} params updated: {}", jobId, delta.keySet());
        return true;
    }

    /**
     * Generate a live sample without interrupting the training loop.
     * Returns immediately; the result is published to the preview bus
     * as a SAMPLE event.
     */
    public void requestSample(String jobId, String prompt, int maxTokens) {
        JobState s = jobs.get(jobId);
        if (s == null) return;
        ModelTrainer trainer = registry.get(s.trainerId);
        if (trainer == null) return;
        PreviewBus.publish(jobId, new Event(EventType.SAMPLE, s.stepCount.get(),
                s.lastLoss, s.lastMetrics, s.lastAntiHallucination,
                trainer.generate(toContext(s), prompt, maxTokens)));
    }

    @Async
    public void runAsync(JobState s) {
        s.status = "running";
        s.startedAt = System.currentTimeMillis();
        s.progress = 5;
        PreviewBus.publish(s.jobId, new Event(EventType.STEP, 0, 0.0,
                Map.of("status", 1.0), Map.of(), null));

        ModelTrainer trainer = registry.get(s.trainerId);
        if (trainer == null) {
            fail(s, "trainer disappeared: " + s.trainerId);
            return;
        }
        NDManager mgr = NDManager.newBaseManager();
        try {
            int maxIters = ((Number) s.params.getOrDefault("maxIters", 50)).intValue();
            double threshold = ((Number) s.params.getOrDefault("hallucinationThreshold", 0.7)).doubleValue();
            for (int i = 0; i < maxIters; i++) {
                if ("cancelled".equals(s.status)) break;
                ModelTrainer.TrainContext ctx = toContext(s);
                ctx.stepIndex = i;
                ModelTrainer.StepResult r = trainer.step(ctx);
                s.lastLoss = r.loss();
                s.lastMetrics = r.metrics();
                s.lastAntiHallucination = ctx.antiHallucination;
                s.stepCount.set(i + 1);
                s.progress = 5 + (int) (90.0 * (i + 1) / Math.max(1, maxIters));
                // Anti-hallucination composite check
                double entropy = ctx.antiHallucination.getOrDefault("entropy", 0.5);
                double repetition = ctx.antiHallucination.getOrDefault("repetition", 0.0);
                double factual = ctx.antiHallucination.getOrDefault("factualSupport", 0.0);
                double hall = AntiHallucination.hallucinationScore(entropy, repetition, factual);
                if (hall >= threshold) {
                    PreviewBus.publish(s.jobId, new Event(EventType.WARN, i, r.loss(),
                            r.metrics(), ctx.antiHallucination,
                            "⚠ 幻觉分数 " + String.format("%.2f", hall) + " ≥ 阈值"));
                }
                try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            // Export
            Path outDir = Paths.get(exportRoot, "java-" + s.trainerId + "-" + s.jobId);
            Files.createDirectories(outDir);
            trainer.export(toContext(s), outDir);
            s.bundlePath = outDir.toAbsolutePath().toString();
            s.status = "succeeded";
            s.progress = 100;
            s.finishedAt = System.currentTimeMillis();
            PreviewBus.publish(s.jobId, new Event(EventType.DONE, s.stepCount.get(), s.lastLoss,
                    s.lastMetrics, s.lastAntiHallucination, null));
        } catch (Throwable th) {
            fail(s, th.getMessage() == null ? th.getClass().getName() : th.getMessage());
        } finally {
            mgr.close();
        }
    }

    private void fail(JobState s, String msg) {
        log.error("[ORCH] job {} failed: {}", s.jobId, msg);
        s.status = "failed";
        s.error = msg;
        s.finishedAt = System.currentTimeMillis();
        PreviewBus.publish(s.jobId, new Event(EventType.DONE, s.stepCount.get(),
                s.lastLoss, s.lastMetrics, s.lastAntiHallucination, "FAILED: " + msg));
    }

    private ModelTrainer.TrainContext toContext(JobState s) {
        Map<String, Object> p = liveParams.getOrDefault(s.jobId, s.params);
        return new ModelTrainer.TrainContext(s.jobId, null, p);
    }

    /**
     * Job snapshot exposed to the REST + SSE layers.
     */
    @Getter
    public static class JobState {
        private final String jobId;
        private final String trainerId;
        private final String corpusPath;
        private final Map<String, Object> params;
        private volatile String status;
        private volatile int progress;
        private volatile double lastLoss;
        private volatile Map<String, Double> lastMetrics = Map.of();
        private volatile Map<String, Double> lastAntiHallucination = Map.of();
        private final AtomicInteger stepCount = new AtomicInteger();
        private volatile String bundlePath;
        private volatile String error;
        private volatile long startedAt;
        private volatile long finishedAt;

        public JobState(String jobId, String trainerId, String corpusPath, Map<String, Object> params) {
            this.jobId = jobId;
            this.trainerId = trainerId;
            this.corpusPath = corpusPath;
            this.params = params;
        }
    }
}
