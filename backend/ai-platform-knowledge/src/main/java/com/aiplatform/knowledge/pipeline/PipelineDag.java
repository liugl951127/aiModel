package com.aiplatform.knowledge.pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Pipeline DAG model. A pipeline is a list of nodes connected by named
 * ports. The orchestrator runs nodes in topological order; each node
 * receives a {@link PipelineContext} it can read/write.
 *
 * <p>Nodes the UI ships with:
 * <ul>
 *   <li>{@code chunker} - Tika + sliding-window chunking</li>
 *   <li>{@code retriever} - top-k similarity over ES</li>
 *   <li>{@code ranker} - re-rank by lexical overlap / length / freshness</li>
 *   <li>{@code llm-answer} - call the inference service</li>
 *   <li>{@code guard} - anti-hallucination pass (refuse, or rewrite with citations)</li>
 * </ul>
 */
public class PipelineDag {

    /** Single node in the DAG. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        /** Stable id used in {@link Edge#from}/{@link Edge#to}. */
        private String id;
        /** Node-type key, e.g. {@code "chunker"}, {@code "llm-answer"}. */
        private String type;
        /** Free-form config map (chunk size, top-k, model code, …). */
        private Map<String, Object> config;
        /** Human-readable label for the UI. */
        private String label;
    }

    /** Directed edge: output of {@code from} feeds into input of {@code to}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        private String from;
        private String to;
        /** Optional named port; if null, default port is used. */
        private String port;
    }

    /** Top-level pipeline descriptor. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pipeline {
        private String id;
        private String name;
        private String description;
        private List<Node> nodes;
        private List<Edge> edges;
    }

    /** Per-run mutable state shared between nodes. */
    public static class PipelineContext {
        public final String query;
        public final Map<String, Object> config;
        public final Map<String, Object> slots;

        public PipelineContext(String query, Map<String, Object> config) {
            this.query = query;
            this.config = config == null ? Map.of() : config;
            this.slots = new java.util.LinkedHashMap<>();
        }

        /** Get a typed slot. */
        @SuppressWarnings("unchecked")
        public <T> T get(String slot, Class<T> type) {
            Object v = slots.get(slot);
            return type.isInstance(v) ? (T) v : null;
        }

        /** Put a slot. */
        public PipelineContext put(String slot, Object value) {
            slots.put(slot, value);
            return this;
        }
    }
}
