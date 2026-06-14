package com.aiplatform.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Declarative workflow specification. A workflow is a DAG of {@link Step}
 * instances. The engine walks the graph in topological order; the data
 * flowing between steps is just a {@code Map<String,Object>}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSpec {

    /** Unique id, also used as the run id when triggered. */
    private String id;
    private String name;
    private String description;
    private List<Step> steps;

    /**
     * One node in the workflow graph. {@code dependsOn} is a list of step
     * names; an empty list means the step is the entry point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        /** Step type. Supported: {@code train, evaluate, deploy, kb, notify, sleep}. */
        private String type;
        /** Logical name within the workflow. */
        private String name;
        /** Predecessor step names. */
        private List<String> dependsOn;
        /** Type-specific parameters (free form, validated per type). */
        private Map<String, Object> params;
    }
}
