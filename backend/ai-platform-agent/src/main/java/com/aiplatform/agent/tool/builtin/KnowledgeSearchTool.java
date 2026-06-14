package com.aiplatform.agent.tool.builtin;

import com.aiplatform.agent.feign.KnowledgeServiceClient;
import com.aiplatform.agent.tool.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RAG tool: queries the knowledge base via Feign and returns the top hits
 * as a string the LLM can quote.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool implements AgentTool {

    private final KnowledgeServiceClient knowledgeClient;

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String description() {
        return "Search the local knowledge base. Args: {\"query\": \"...\", \"top_k\": 3}";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"top_k\":{\"type\":\"integer\"}},\"required\":[\"query\"]}";
    }

    @Override
    public String execute(Map<String, Object> args) {
        try {
            Object q = args.get("query");
            if (q == null) return "error: query is required";
            int topK = args.get("top_k") == null ? 3 : Integer.parseInt(args.get("top_k").toString());
            return knowledgeClient.search(q.toString(), topK);
        } catch (Exception e) {
            log.warn("[TOOL knowledge_search] {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }
}
