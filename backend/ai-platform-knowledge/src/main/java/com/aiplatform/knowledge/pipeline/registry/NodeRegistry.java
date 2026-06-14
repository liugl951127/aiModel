package com.aiplatform.knowledge.pipeline.registry;

import com.aiplatform.knowledge.pipeline.nodes.PipelineNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registry of all {@link PipelineNode} beans. Populated by Spring component
 * scanning; the UI calls {@link #summaries()} to render the node palette.
 */
@Component
@RequiredArgsConstructor
public class NodeRegistry {

    private final List<PipelineNode> nodes;

    public List<PipelineNode> all() {
        return nodes.stream()
                .sorted(Comparator.comparing(PipelineNode::displayName))
                .collect(Collectors.toList());
    }

    public PipelineNode get(String type) {
        return nodes.stream().filter(n -> n.type().equals(type)).findFirst().orElse(null);
    }

    public List<Summary> summaries() {
        return all().stream().map(n -> new Summary(
                n.type(), n.displayName(), n.defaultConfig(), n.inputs(), n.outputs()))
                .collect(Collectors.toList());
    }

    public record Summary(String type, String displayName, java.util.Map<String, Object> defaultConfig,
                          List<PipelineNode.Port> inputs, List<PipelineNode.Port> outputs) {}
}
