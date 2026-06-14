package com.aiplatform.agent.tool;

import java.util.Map;

/**
 * Generic tool contract. Implementations are registered through Spring and
 * discovered by the ReAct engine via the {@code handler} field on the tool entity.
 */
public interface AgentTool {

    /** Stable tool name used in the ReAct prompt. */
    String name();

    /** Human description used in the LLM prompt. */
    String description();

    /** JSON schema of input parameters. */
    String parametersSchema();

    /**
     * Execute the tool.
     *
     * @param args parsed arguments
     * @return string result (will be put back into the prompt as tool output)
     */
    String execute(Map<String, Object> args);
}
