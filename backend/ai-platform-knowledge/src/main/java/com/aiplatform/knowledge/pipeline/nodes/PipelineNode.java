package com.aiplatform.knowledge.pipeline.nodes;

import com.aiplatform.knowledge.pipeline.PipelineDag.Node;
import com.aiplatform.knowledge.pipeline.PipelineDag.PipelineContext;

/**
 * A node in a knowledge-base pipeline. Implementations are stateless and
 * thread-safe; the orchestrator instantiates one per execution and passes
 * the per-node config in via the constructor.
 */
public interface PipelineNode {

    /** Stable type key — must match what the UI sends in {@link Node#type}. */
    String type();

    /** Human-readable display name. */
    String displayName();

    /**
     * Run the node against {@code ctx}, mutating slots in place. The node
     * may also read per-node config from {@code ctx.config} or from
     * {@code node.config()}.
     */
    void execute(Node node, PipelineContext ctx);

    /** Default config (used by the UI to seed the form). */
    default java.util.Map<String, Object> defaultConfig() {
        return java.util.Map.of();
    }

    /** Free-form descriptor of the inputs/outputs (for the DAG visualizer). */
    default java.util.List<Port> inputs() { return java.util.List.of(); }
    default java.util.List<Port> outputs() { return java.util.List.of(); }

    /** Named port. */
    record Port(String name, String label, String type) {}
}
