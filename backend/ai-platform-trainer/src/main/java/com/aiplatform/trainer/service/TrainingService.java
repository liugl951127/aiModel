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
import java.time.LocalDateTime;
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
    private final com.aiplatform.trainer.mapper.TrainJobMapper jobMapper;  // ★ DB 持久化

    public JobState submit(String corpusPath, MiniTransformerTrainer.Config cfg) {
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        JobState state = new JobState(jobId, corpusPath, cfg,
                cfg.guard == null ? HallucinationGuardConfig.defaults() : cfg.guard);
        jobs.put(jobId, state);
        // ★ 同步写 DB (重启服务不丢训练记录)
        try {
            com.aiplatform.trainer.entity.TrainJobEntity e = new com.aiplatform.trainer.entity.TrainJobEntity();
            e.setJobCode(jobId);
            e.setAlgorithm(cfg.modelType == null ? "minigpt" : cfg.modelType);
            e.setEpochs(cfg.maxIters / Math.max(1, cfg.evalInterval));
            e.setBatchSize(cfg.batchSize);
            e.setLearningRate(cfg.learningRate);
            e.setStatus("queued");
            e.setProgress(0);
            e.setConfig(com.alibaba.fastjson2.JSON.toJSONString(cfg));
            e.setLogPath(corpusPath);
            jobMapper.insert(e);
        } catch (Exception ex) {
            log.warn("[TRAIN-JOB] 写 DB 失败 (走内存): {}", ex.getMessage());
        }
        runAsync(state);
        return state;
    }

    public JobState get(String id) { return jobs.get(id); }
    public Map<String, JobState> all() { return jobs; }

    /**
     * 从 DB 列训练历史 (重启服务不丢).
     */
    public java.util.List<com.aiplatform.trainer.entity.TrainJobEntity> listFromDb(int limit) {
        try {
            return jobMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.aiplatform.trainer.entity.TrainJobEntity>()
                    .eq("deleted", 0)
                    .orderByDesc("create_time")
                    .last("LIMIT " + Math.max(1, Math.min(500, limit)))
            );
        } catch (Exception e) {
            log.warn("[TRAIN-DB] listFromDb 失败, 返空: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 从 DB 单查 (页面刷新也能拿到老任务状态).
     */
    public com.aiplatform.trainer.entity.TrainJobEntity getFromDb(String jobCode) {
        try {
            return jobMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.aiplatform.trainer.entity.TrainJobEntity>()
                    .eq("job_code", jobCode)
                    .eq("deleted", 0)
                    .last("LIMIT 1")
            );
        } catch (Exception e) {
            log.warn("[TRAIN-DB] getFromDb 失败: {}", e.getMessage());
            return null;
        }
    }

    @Async
    public void runAsync(JobState state) {
        try {
            // ★ v3.x fail-fast: 先校验 corpus 文件存在, 避免 NoSuchFileException 拖到训练主流程里
            Path corpusFile = Paths.get(state.corpusPath);
            if (!Files.exists(corpusFile)) {
                String msg = String.format(
                    "corpus file not found: %s (absolute: %s). 训练任务要求 corpusPath 指向已存在的文本文件, "
                  + "请检查路径拼写 (Windows 上 '\\opt\\...' 是错误写法, 应是 'C:\\opt\\...' 或 WSL/Linux 路径)",
                    state.corpusPath, corpusFile.toAbsolutePath());
                log.error("[TRAIN-JOB] {} failed: {}", state.jobId, msg);
                state.status = "failed";
                state.error = msg;
                updateJobDbOnFail(state.jobId, msg);
                return;
            }
            if (!Files.isReadable(corpusFile)) {
                String msg = String.format("corpus file not readable: %s", state.corpusPath);
                log.error("[TRAIN-JOB] {} failed: {}", state.jobId, msg);
                state.status = "failed";
                state.error = msg;
                updateJobDbOnFail(state.jobId, msg);
                return;
            }

            state.status = "running";
            state.startedAt = System.currentTimeMillis();
            state.progress = 5;
            // ★ DB: 记 started_at + status=running
            try { jobMapper.updateProgress(state.jobId, 5, "running"); } catch (Exception e) { log.debug("[TRAIN-DB] updateProgress 失败: {}", e.getMessage()); }
            try {
                com.aiplatform.trainer.entity.TrainJobEntity ue = new com.aiplatform.trainer.entity.TrainJobEntity();
                ue.setJobCode(state.jobId);
                ue.setStartedAt(LocalDateTime.now());
                // 用 queryWrapper update (entity 字段是 null 不更新)
                com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.aiplatform.trainer.entity.TrainJobEntity> uw =
                        new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
                uw.eq("job_code", state.jobId).eq("deleted", 0)
                  .set("status", "running")
                  .set("progress", 5)
                  .set("started_at", LocalDateTime.now());
                jobMapper.update(null, uw);
            } catch (Exception ex) { log.debug("[TRAIN-DB] update status=running 失败: {}", ex.getMessage()); }

            // Optional KB-grounded corpus augmentation
            byte[] raw;
            try {
                if (state.config.knowledgeKbId != null
                        && state.config.knowledgeSeedTopics != null
                        && !state.config.knowledgeSeedTopics.isEmpty()) {
                    raw = Files.readAllBytes(corpusFile);
                    raw = augmenter.build(raw, state.config.knowledgeKbId,
                            state.config.knowledgeSeedTopics, true);
                } else {
                    raw = Files.readAllBytes(corpusFile);
                }
            } catch (Exception e) {
                // ★ 之前这里 fall back 又 read 一次会再抛 NoSuchFileException, 现改为返 clear error
                String msg = "augment failed: " + e.getMessage();
                log.error("[TRAIN-JOB] {} failed: {}", state.jobId, msg);
                state.status = "failed";
                state.error = msg;
                updateJobDbOnFail(state.jobId, msg);
                return;
            }

            // Write augmented bytes to a tmp file so trainer can use the same code path
            Path tmpCorpus = Files.createTempFile("corpus-", ".txt");
            Files.write(tmpCorpus, raw);
            state.corpusPath = tmpCorpus.toAbsolutePath().toString();

            MiniTransformerTrainer trainer = new MiniTransformerTrainer(preview, checkpointService, modelRegistry);
            trainer.bindJobId(state.jobId);
            MiniTransformerTrainer.TrainResult result = trainer.train(tmpCorpus, state.config);
            state.progress = 90;
            try { jobMapper.updateProgress(state.jobId, 90, "running"); } catch (Exception e) { /* ignore */ }

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
            // ★ DB: 写最终状态
            try {
                String metrics = com.alibaba.fastjson2.JSON.toJSONString(java.util.Map.of(
                    "finalLoss", result.finalLoss,
                    "iters", result.steps,
                    "factualSupport", result.metrics == null ? 0.0 : result.metrics.getOrDefault("factual_support", 0.0)
                ));
                jobMapper.updateFinish(state.jobId, "succeeded", 100,
                        outDir.toAbsolutePath().toString(), null, metrics,
                        LocalDateTime.now(), LocalDateTime.now());
            } catch (Exception ex) { log.debug("[TRAIN-DB] updateFinish 失败: {}", ex.getMessage()); }
            log.info("[TRAIN-JOB] {} succeeded: loss={} -> {}",
                    state.jobId, String.format("%.4f", state.finalLoss), outDir);
        } catch (Throwable th) {
            log.error("[TRAIN-JOB] {} failed: {}", state.jobId, th.getMessage(), th);
            state.status = "failed";
            state.error = th.getMessage() == null ? th.getClass().getName() : th.getMessage();
            state.finishedAt = System.currentTimeMillis();
            // ★ DB: 写失败状态
            try {
                jobMapper.updateFinish(state.jobId, "failed", state.progress,
                        null, state.error, null, null, LocalDateTime.now());
            } catch (Exception ex) { log.debug("[TRAIN-DB] updateFinish(failed) 失败: {}", ex.getMessage()); }
        }
    }

    /**
     * ★ v3.x 失败状态快速写 DB (用于 runAsync 前校验路径阶段的失败, 不进 catch 大块).
     */
    private void updateJobDbOnFail(String jobId, String errorMsg) {
        try {
            jobMapper.updateFinish(jobId, "failed", 0,
                    null, errorMsg, null, null, LocalDateTime.now());
        } catch (Exception ex) {
            log.debug("[TRAIN-DB] updateFinish(failed pre-check) 失败: {}", ex.getMessage());
        }
    }
}
