package com.aiplatform.trainer.schedule;

/**
 * 早停器：根据 loss 趋势判断是否提前终止训练，避免过拟合。
 *
 * <h2>触发条件（任一）</h2>
 * <ul>
 *   <li>连续 {@code patience} 步 loss 没下降</li>
 *   <li>当前 loss 比最低 loss 高 {@code minDelta} 持续 {@code patience} 步</li>
 *   <li>滑动平均 loss 连续 {@code patience} 步上升</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   EarlyStopper es = new EarlyStopper(20, 0.001);
 *   for (int step = 1; step <= total; step++) {
 *       double loss = trainOneStep();
 *       if (es.observe(loss)) break;  // 触发早停
 *   }
 * </pre>
 */
public class EarlyStopper {

    private final int patience;
    private final double minDelta;
    private double bestLoss = Double.POSITIVE_INFINITY;
    private int badSteps = 0;
    private boolean stopped = false;
    private String reason;

    public EarlyStopper(int patience, double minDelta) {
        this.patience = Math.max(1, patience);
        this.minDelta = minDelta;
    }

    /**
     * 喂入一个 loss，返回是否应该停止。
     */
    public synchronized boolean observe(double loss) {
        if (stopped) return true;
        if (loss + minDelta < bestLoss) {
            bestLoss = loss;
            badSteps = 0;
        } else {
            badSteps++;
            if (badSteps >= patience) {
                stopped = true;
                reason = "no improvement in " + patience + " steps (best=" + bestLoss + ")";
            }
        }
        return stopped;
    }

    public boolean isStopped() { return stopped; }
    public String reason() { return reason; }
    public double bestLoss() { return bestLoss; }
    public int badSteps() { return badSteps; }

    public void reset() {
        bestLoss = Double.POSITIVE_INFINITY;
        badSteps = 0;
        stopped = false;
        reason = null;
    }
}
