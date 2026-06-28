package com.aiplatform.model.service;

import cn.hutool.core.util.StrUtil;
import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.model.entity.ModelRegistry;
import com.aiplatform.model.entity.TrainJob;
import com.aiplatform.model.mapper.TrainJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainJobService {

    private final TrainJobMapper trainJobMapper;
    private final ModelRegistryService modelService;
    private final com.aiplatform.redis.distributed.DistributedCache cache;

    public TrainJob submit(TrainJob job) {
        if (job.getModelId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "modelId 必填");
        }
        ModelRegistry m = modelService.getById(job.getModelId());
        if ("training".equals(m.getStatus())) {
            throw new BusinessException(ResultCode.MODEL_TRAINING);
        }
        if (job.getJobCode() == null) {
            job.setJobCode("J-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        job.setStatus("queued");
        job.setProgress(0);
        if (job.getEpochs() == null) job.setEpochs(3);
        if (job.getBatchSize() == null) job.setBatchSize(8);
        if (job.getLearningRate() == null) job.setLearningRate(5e-5);
        if (job.getAlgorithm() == null) job.setAlgorithm("causal-lm-finetune");
        trainJobMapper.insert(job);
        cache.invalidate(CACHE_KEY_PREFIX + job.getId());

        modelService.updateStatus(job.getModelId(), "training");

        // Fire-and-forget training; in a real cluster this dispatches to k8s / python worker.
        runAsync(job.getId());

        return job;
    }

    @Async
    public void runAsync(Long jobId) {
        TrainJob job = trainJobMapper.selectById(jobId);
        if (job == null) return;
        try {
            job.setStatus("running");
            job.setStartedAt(LocalDateTime.now());
            job.setProgress(5);
            trainJobMapper.updateById(job);

            int totalEpochs = job.getEpochs() == null ? 3 : job.getEpochs();
            for (int epoch = 1; epoch <= totalEpochs; epoch++) {
                Thread.sleep(800);
                job.setProgress(5 + (epoch * 90 / totalEpochs));
                job.setMetrics(StrUtil.format("{{\"epoch\":{},\"loss\":{}}}", epoch, 5.0 / epoch));
                trainJobMapper.updateById(job);
            }

            job.setStatus("succeeded");
            job.setProgress(100);
            job.setFinishedAt(LocalDateTime.now());
            job.setOutputPath("/opt/ai-platform/models/" + jobId);
            trainJobMapper.updateById(job);
            cache.invalidate(CACHE_KEY_PREFIX + job.getId());
            modelService.updateStatus(job.getModelId(), "ready");
        } catch (Exception e) {
            log.error("[TRAIN] job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus("failed");
            trainJobMapper.updateById(job);
            cache.invalidate(CACHE_KEY_PREFIX + job.getId());
            modelService.updateStatus(job.getModelId(), "failed");
        }
    }

    public PageResult<TrainJob> page(PageQuery q) {
        Page<TrainJob> page = q.toPage();
        LambdaQueryWrapper<TrainJob> w = new LambdaQueryWrapper<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            w.like(TrainJob::getJobCode, q.getKeyword());
        }
        w.orderByDesc(TrainJob::getCreateTime);
        return PageAdapter.of(trainJobMapper.selectPage(page, w));
    }

    public TrainJob get(Long id) {
        // ★ v3.x 优化: 训练任务查后端轮询频繁, 缓存 10s (避免 stale 但减 DB 压力)
        // 状态变化时 (runAsync/updateStatus) 主动 invalidate
        return cache.getOrLoad(CACHE_KEY_PREFIX + id, CACHE_TTL_SEC,
            () -> trainJobMapper.selectById(id), TrainJob.class);
    }

    private static final String CACHE_KEY_PREFIX = "aiplatform:trainjob:";
    private static final int CACHE_TTL_SEC = 10;
}
