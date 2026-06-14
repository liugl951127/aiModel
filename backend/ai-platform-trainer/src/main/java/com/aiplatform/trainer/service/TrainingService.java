package com.aiplatform.trainer.service;

import com.aiplatform.trainer.constraints.HallucinationGuardConfig;
import com.aiplatform.trainer.dataset.CorpusAugmenter;
import com.aiplatform.trainer.model.MiniTransformer;
import com.aiplatform.trainer.preview.PreviewService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Async job orchestrator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    @Getter
    public static class JobState {
        private final String jobId;
        private volatile String corpusPath;
        private final MiniTransformerTrainer.Config config;
        private final HallucinationGuardConfig guard;
        private volatile String status = "queued";
        private volatile int progress;
        private volatile String outputPath;
        private volatile String bundleName;
        private volatile double finalLoss;
        private volatile String error;
        private volatile long startedAt;
        private volatile long finishedAt;

        public JobState(String jobId, String corpusPath,
                        MiniTransformerTrainer.Config config,
                        HallucinationGuardConfig guard) {
            this.jobId = jobId;
            this.corpusPath = corpusPath;
            this.config = config;
            this.guard = guard;
        }
    }

    @Value("${aiplatform.trainer.export-root:/opt/ai-platform/inference-bundles}")
    private String exportRoot;

    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final CorpusAugmenter augmenter;
    private final PreviewService preview;
    private final com.aiplatform.trainer.checkpoint.CheckpointService checkpointService;
    private final com.aiplatform.trainer.version.ModelRegistry modelRegistry;

    public JobState submit(String corpusPath, MiniTransformerTrainer.Config cfg) {
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        JobState state = new JobState(jobId, corpusPath, cfg,
                cfg.guard == null ? HallucinationGuardConfig.defaults() : cfg.guard);
        jobs.put(jobId, state);
        runAsync(state);
        return state;
    }

    public JobState get(String id) { return jobs.get(id); }
    public Map<String, JobState> all() { return jobs; }

    @Async
    public void runAsync(JobState state) {
        try {
            state.status = "running";
            state.startedAt = System.currentTimeMillis();
            state.progress = 5;

            // Optional KB-grounded corpus augmentation
            byte[] raw;
            try {
                if (state.config.knowledgeKbId != null
                        && state.config.knowledgeSeedTopics != null
                        && !state.config.knowledgeSeedTopics.isEmpty()) {
                    raw = Files.readAllBytes(Paths.get(state.corpusPath));
                    raw = augmenter.build(raw, state.config.knowledgeKbId,
                            state.config.knowledgeSeedTopics, true);
                } else {
                    raw = Files.readAllBytes(Paths.get(state.corpusPath));
                }
            } catch (Exception e) {
                log.warn("[TRAIN-JOB] augment failed, falling back: {}", e.getMessage());
                raw = Files.readAllBytes(Paths.get(state.corpusPath));
            }

            // Write augmented bytes to a tmp file so trainer can use the same code path
            Path tmpCorpus = Files.createTempFile("corpus-", ".txt");
            Files.write(tmpCorpus, raw);
            state.corpusPath = tmpCorpus.toAbsolutePath().toString();

            MiniTransformerTrainer trainer = new MiniTransformerTrainer(preview, checkpointService, modelRegistry);
            trainer.bindJobId(state.jobId);
            MiniTransformerTrainer.TrainResult result = trainer.train(tmpCorpus, state.config);
            state.progress = 90;

            Path outDir = Paths.get(exportRoot, "java-" + state.jobId);
            Files.createDirectories(outDir);
            trainer.exportBundle(result.model, outDir, state.guard);

            // 注册模型版本
            if (state.config.registerVersion && modelRegistry != null) {
                com.aiplatform.trainer.version.ModelRegistry.Version v =
                        new com.aiplatform.trainer.version.ModelRegistry.Version();
                v.setName(state.config.knowledgeSeedTopics == null ? "auto" : String.join("-", state.config.knowledgeSeedTopics));
                v.setModelType(state.config.modelType);
                v.setNLayer(state.config.nLayer);
                v.setNHead(state.config.nHead);
                v.setNEmbd(state.config.nEmbd);
                v.setBlockSize(state.config.blockSize);
                v.setVocabSize(state.config.vocabSize);
                v.setFinalLoss(result.finalLoss);
                v.setIters(result.steps);
                v.setGuardJson(com.alibaba.fastjson2.JSON.toJSONString(state.guard));
                v.setBundlePath(outDir.toAbsolutePath().toString());
                modelRegistry.register(v);
            }

            state.status = "succeeded";
            state.progress = 100;
            state.outputPath = outDir.toAbsolutePath().toString();
            state.bundleName = "java-" + state.jobId;
            state.finalLoss = result.finalLoss;
            state.finishedAt = System.currentTimeMillis();
            log.info("[TRAIN-JOB] {} succeeded: loss={} -> {}",
                    state.jobId, String.format("%.4f", state.finalLoss), outDir);
        } catch (Throwable th) {
            log.error("[TRAIN-JOB] {} failed: {}", state.jobId, th.getMessage(), th);
            state.status = "failed";
            state.error = th.getMessage() == null ? th.getClass().getName() : th.getMessage();
            state.finishedAt = System.currentTimeMillis();
        }
    }
}
