package com.aiplatform.agent.tool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves tool names to {@link AgentTool} instances.
 * Discovers every {@code AgentTool} bean on startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<AgentTool> tools;
    private final Map<String, AgentTool> index = new HashMap<>();

    @PostConstruct
    public void init() {
        for (AgentTool t : tools) {
            index.put(t.name(), t);
        }
        log.info("[TOOL] registered {} tools: {}", index.size(), index.keySet());
    }

    public AgentTool get(String name) {
        return index.get(name);
    }

    public List<AgentTool> all() {
        return tools;
    }
}
