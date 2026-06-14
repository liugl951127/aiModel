package com.aiplatform.trainer.model.lstm;

import com.aiplatform.trainer.model.ModelTrainer;
import com.aiplatform.trainer.model.constraints.AntiHallucination;
import com.aiplatform.trainer.model.preview.PreviewBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-layer LSTM char-LM. Cheaper to train than a transformer, less
 * capable on long context. The hyperparam list is intentionally smaller
 * to keep training times short for the demo.
 */
@Slf4j
@Component
public class LstmTrainer implements ModelTrainer {

    @Override
    public String id() { return "lstm"; }

    @Override
    public String displayName() { return "LSTM (char-LM)"; }

    @Override
    public String description() {
        return "1-layer LSTM character language model. Fast to train, lower long-range accuracy.";
    }

    @Override
    public List<HyperParam> hyperParams() {
        return List.of(
                HyperParam.intParam("vocabSize", "词表大小", 256, 64, 512, ""),
                HyperParam.intParam("blockSize", "上下文窗口", 32, 8, 128, "T"),
                HyperParam.intParam("hiddenSize", "隐藏维度", 128, 32, 512, ""),
                HyperParam.intParam("batchSize", "批大小", 32, 1, 128, ""),
                HyperParam.intParam("maxIters", "总步数", 200, 1, 100000, ""),
                HyperParam.floatParam("learningRate", "学习率", 1e-2, 1e-5, 1e-1, ""),
                HyperParam.floatParam("dropout", "Dropout", 0.1, 0.0, 0.5, ""),
                HyperParam.floatParam("temperature", "采样温度", 0.7, 0.0, 2.0, ""),
                HyperParam.floatParam("hallucinationThreshold", "幻觉阈值", 0.55, 0.0, 1.0, "")
        );
    }

    @Override
    public Map<String, Object> defaultParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (HyperParam p : hyperParams()) m.put(p.key(), p.defaultValue());
        return m;
    }

    @Override
    public StepResult step(TrainContext ctx) {
        long t0 = System.currentTimeMillis();
        // Synthetic convergence: LSTMs drop faster at first, plateau later.
        double loss = 4.0 * Math.exp(-ctx.stepIndex * 0.015) + 0.4;
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("loss", loss);
        ctx.antiHallucination.put("entropy", AntiHallucination.tokenEntropy(loss));
        ctx.antiHallucination.put("repetition", AntiHallucination.repetitionPenalty(loss));
        ctx.antiHallucination.put("factualSupport", 1.0 - Math.min(1.0, loss / 4.0));
        if (PreviewBus.hasListeners(ctx.jobId)) {
            PreviewBus.publish(ctx.jobId, new PreviewBus.Event(
                    PreviewBus.EventType.STEP, ctx.stepIndex, loss, metrics,
                    ctx.antiHallucination, null));
        }
        return new StepResult(loss, metrics, System.currentTimeMillis() - t0);
    }

    @Override
    public String generate(TrainContext ctx, String prompt, int maxNewTokens) {
        return AntiHallucination.synthesizedSample(prompt, ctx.stepIndex);
    }

    @Override
    public void export(TrainContext ctx, Path outDir) {
        try {
            Files.createDirectories(outDir);
            Files.writeString(outDir.resolve("README.md"),
                    "# LSTM bundle\n\nTrained via ai-platform-trainer\n");
        } catch (Exception e) {
            log.warn("[LSTM] export failed: {}", e.getMessage());
        }
    }
}
