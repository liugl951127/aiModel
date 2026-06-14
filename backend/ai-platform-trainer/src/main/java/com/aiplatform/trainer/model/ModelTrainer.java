package com.aiplatform.trainer.model;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;

import java.util.List;
import java.util.Map;

/**
 * Generic model trainer interface. Every concrete model (Mini-GPT, LSTM,
 * Retrieval-Augmented) implements this so the training orchestrator and
 * the live-preview pipeline can stay model-agnostic.
 *
 * <h2>Anti-hallucination hooks</h2>
 * Implementations may populate the {@link TrainContext#antiHallucination}
 * map with confidence/calibration info that the preview/UI layer can render
 * (e.g. top-k softmax entropy, retrieved-doc coverage, factual-support score).
 */
public interface ModelTrainer {

    /** Stable identifier used in REST + UI ("minigpt", "lstm", "rag"). */
    String id();

    /** Human-readable display name. */
    String displayName();

    /** Short blurb shown in the model picker. */
    String description();

    /** Hyperparameters the UI should surface as controls. */
    List<HyperParam> hyperParams();

    /**
     * One Adam / SGD step. Returns the new loss and (optionally) any
     * diagnostic metrics the caller wants to push to the live preview.
     */
    StepResult step(TrainContext ctx);

    /**
     * Run inference. Implementations decide whether to use greedy, sampling,
     * beam search, or constrained decoding based on {@code ctx.params}.
     */
    String generate(TrainContext ctx, String prompt, int maxNewTokens);

    /**
     * Export the trained parameters to a portable bundle on disk.
     */
    void export(TrainContext ctx, java.nio.file.Path outDir);

    /** Default hyperparameters bundled with the model. */
    Map<String, Object> defaultParams();

    /**
     * Hyperparameter descriptor — drives the dynamic form rendered in the UI.
     */
    record HyperParam(String key, String label, String type, Object min, Object max,
                      Object step, Object defaultValue, String hint) {
        public static HyperParam intParam(String key, String label, int dflt, int min, int max, String hint) {
            return new HyperParam(key, label, "int", min, max, 1, dflt, hint);
        }
        public static HyperParam floatParam(String key, String label, double dflt, double min, double max, String hint) {
            return new HyperParam(key, label, "float", min, max, 0.01, dflt, hint);
        }
        public static HyperParam boolParam(String key, String label, boolean dflt, String hint) {
            return new HyperParam(key, label, "bool", 0, 1, 1, dflt, hint);
        }
        public static HyperParam choice(String key, String label, String dflt, java.util.List<String> opts, String hint) {
            return new HyperParam(key, label, "choice", 0, opts.size() - 1, 1, dflt, hint);
        }
    }

    /** Result of one training step. */
    record StepResult(double loss, Map<String, Double> metrics, long elapsedMs) {}

    /**
     * Per-step / per-call state. The {@code params} map is mutated by the
     * orchestrator when the user changes a hyperparameter mid-run.
     */
    final class TrainContext {
        public final String jobId;
        public final NDManager manager;
        public final Map<String, Object> params;
        public final Map<String, Double> antiHallucination;
        public NDArray inputBatch;
        public NDArray targetBatch;
        public int stepIndex;

        public TrainContext(String jobId, NDManager manager, Map<String, Object> params) {
            this.jobId = jobId;
            this.manager = manager;
            this.params = params;
            this.antiHallucination = new java.util.LinkedHashMap<>();
        }
    }
}
