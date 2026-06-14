package com.aiplatform.trainer.schedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 学习率调度策略。
 *
 * <p>训练时 {@code learningRate(t)} 实时计算当前步的 LR。支持的策略：</p>
 * <ul>
 *   <li>{@code constant} — 不变</li>
 *   <li>{@code cosine} — 余弦退火，从 {@code base} 衰减到 {@code min}</li>
 *   <li>{@code warmup_cosine} — 前 N 步线性 warmup 到 base，然后余弦退火</li>
 *   <li>{@code step} — 每隔 {@code stepSize} 步乘以 {@code gamma}</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 *   LRSchedule sched = LRSchedule.warmupCosine(0.003, 2000, 0.0001, 100);
 *   double lr = sched.at(step, baseLr);
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LRSchedule {

    public enum Type { CONSTANT, COSINE, WARMUP_COSINE, STEP }

    private Type type = Type.COSINE;
    private double base = 3e-3;
    private double min  = 1e-5;
    private int warmupSteps = 100;
    private int stepSize = 1000;
    private double gamma = 0.5;

    public static LRSchedule constant() { return new LRSchedule(Type.CONSTANT, 0, 0, 0, 0, 0); }
    public static LRSchedule cosine(double base) { return new LRSchedule(Type.COSINE, base, 1e-5, 0, 0, 0); }
    public static LRSchedule warmupCosine(double base, int total, double min, int warmup) {
        return new LRSchedule(Type.WARMUP_COSINE, base, min, warmup, 0, 0);
    }

    /**
     * 计算 step 步的实际学习率。
     *
     * @param step   当前步（1-based）
     * @param total  总步数（仅 WARMUP_COSINE / COSINE 使用）
     * @return 该步的学习率
     */
    public double at(int step, int total) {
        if (type == Type.CONSTANT) return base;
        if (step <= 0) return min;
        if (type == Type.STEP) {
            int k = (step - 1) / Math.max(1, stepSize);
            return base * Math.pow(gamma, k);
        }
        if (type == Type.WARMUP_COSINE) {
            if (step < warmupSteps) return base * step / Math.max(1, warmupSteps);
        }
        // cosine decay from base → min
        double progress;
        if (type == Type.WARMUP_COSINE) {
            progress = (double) (step - warmupSteps) / Math.max(1, total - warmupSteps);
        } else {
            progress = (double) step / Math.max(1, total);
        }
        progress = Math.max(0, Math.min(1, progress));
        return min + 0.5 * (base - min) * (1 + Math.cos(Math.PI * progress));
    }
}
