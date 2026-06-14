package com.aiplatform.agent.engine;

import com.aiplatform.agent.entity.AgentEntity;
import com.aiplatform.agent.entity.Message;
import com.aiplatform.agent.feign.InferenceServiceClient;
import com.aiplatform.agent.memory.MemoryStore;
import com.aiplatform.agent.tool.AgentTool;
import com.aiplatform.agent.tool.ToolRegistry;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.common.util.RedisUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ReAct engine: the agent repeatedly asks the LLM to either
 *   - emit a tool call, or
 *   - produce the final answer.
 *
 * The loop is bounded by {@code maxSteps} so a runaway agent never spins forever.
 *
 * LLM call is delegated to the inference service via Feign, so the same engine
 * works with any model the platform exposes (local ONNX, future remote adapters).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActEngine {

    private final ToolRegistry toolRegistry;
    private final MemoryStore memoryStore;
    private final InferenceServiceClient inferenceClient;
    private final RedisUtils redisUtils;

    public AgentRunResult run(AgentEntity agent, String sessionId, String userInput) {
        log.info("[REACT] agent={} session={} input={}", agent.getId(), sessionId, userInput);

        int maxSteps = agent.getMaxSteps() == null ? 5 : agent.getMaxSteps();
        List<Message> trace = new ArrayList<>();
        List<AgentTool> tools = resolveTools(agent.getTools());

        String scratchKey = "react:" + sessionId + ":" + UUID.randomUUID();
        redisUtils.set(scratchKey, "running", 600);

        for (int step = 1; step <= maxSteps; step++) {
            String prompt = buildPrompt(agent, userInput, trace, tools);
            String llmOutput = callLlm(agent, prompt);

            JSONObject parsed = parseAction(llmOutput);
            if (parsed == null) {
                return finish(agent, sessionId, userInput, llmOutput, trace, step);
            }
            String action = parsed.getString("action");
            JSONObject args = parsed.getJSONObject("args");
            if (action == null || "final".equalsIgnoreCase(action)) {
                String answer = parsed.getString("answer");
                if (answer == null) answer = llmOutput;
                return finish(agent, sessionId, userInput, answer, trace, step);
            }

            AgentTool tool = toolRegistry.get(action);
            if (tool == null) {
                String errMsg = "未知工具: " + action;
                Message tm = newMessage(sessionId, "tool", errMsg, action, null, step);
                memoryStore.persist(null, tm);
                trace.add(tm);
                continue;
            }

            Message callMsg = newMessage(sessionId, "assistant", null, action, args == null ? null : args.toJSONString(), step);
            memoryStore.persist(null, callMsg);
            trace.add(callMsg);

            String out;
            try {
                out = tool.execute(args == null ? Map.of() : args.toJavaObject(Map.class));
            } catch (Exception e) {
                out = "tool error: " + e.getMessage();
            }
            Message outMsg = newMessage(sessionId, "tool", out, action, null, step);
            memoryStore.persist(null, outMsg);
            trace.add(outMsg);
        }

        redisUtils.delete(scratchKey);
        throw new BusinessException(ResultCode.AGENT_EXECUTE_ERROR, "智能体超过最大步数仍未给出最终答案");
    }

    private List<AgentTool> resolveTools(String toolsField) {
        if (toolsField == null || toolsField.isBlank()) return List.of();
        List<AgentTool> result = new ArrayList<>();
        for (String name : toolsField.split(",")) {
            AgentTool t = toolRegistry.get(name.trim());
            if (t != null) result.add(t);
        }
        return result;
    }

    private String callLlm(AgentEntity agent, String prompt) {
        Map<String, Object> body = Map.of(
                "modelCode", agent.getModelCode() == null ? "default" : agent.getModelCode(),
                "prompt", prompt,
                "temperature", agent.getTemperature() == null ? 0.7 : agent.getTemperature(),
                "maxTokens", 512
        );
        Result<Map<String, Object>> resp = inferenceClient.generate(body);
        if (resp == null || resp.getCode() == null || resp.getCode() != ResultCode.SUCCESS.getCode()) {
            // Fallback to a deterministic stub so the loop can still be exercised end-to-end
            // when the inference service is offline.
            return "{\"action\":\"final\",\"answer\":\"(本地模型未就绪) 已收到你的输入: " + escape(prompt) + "\"}";
        }
        Object text = resp.getData() == null ? null : resp.getData().get("text");
        return text == null ? "" : text.toString();
    }

    private String buildPrompt(AgentEntity agent, String userInput, List<Message> trace, List<AgentTool> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append(agent.getSystemPrompt() == null ? "你是一个乐于助人的智能助手。" : agent.getSystemPrompt()).append("\n\n");
        sb.append("可用工具:\n");
        if (tools.isEmpty()) {
            sb.append("- (无)\n");
        } else {
            for (AgentTool t : tools) {
                sb.append("- ").append(t.name()).append(": ").append(t.description()).append("\n");
                sb.append("  参数: ").append(t.parametersSchema()).append("\n");
            }
        }
        sb.append("\n请严格用 JSON 响应，格式为 {\"action\":\"<工具名|final>\",\"args\":{...},\"answer\":\"<最终回答>\"}。\n");
        sb.append("用户: ").append(userInput).append("\n");
        for (Message m : trace) {
            sb.append(m.getRole()).append(": ").append(m.getContent() == null ? "" : m.getContent()).append("\n");
        }
        sb.append("assistant:");
        return sb.toString();
    }

    private JSONObject parseAction(String llmOutput) {
        if (llmOutput == null) return null;
        String trimmed = llmOutput.trim();
        try {
            return JSON.parseObject(trimmed);
        } catch (Exception e) {
            int s = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (s >= 0 && end > s) {
                try {
                    return JSON.parseObject(trimmed.substring(s, end + 1));
                } catch (Exception ignore) {
                }
            }
        }
        JSONObject wrap = new JSONObject();
        wrap.put("action", "final");
        wrap.put("answer", llmOutput);
        return wrap;
    }

    private AgentRunResult finish(AgentEntity agent, String sessionId, String userInput,
                                  String answer, List<Message> trace, int steps) {
        Message finalMsg = newMessage(sessionId, "assistant", answer, null, null, steps);
        memoryStore.persist(null, finalMsg);
        trace.add(finalMsg);
        return new AgentRunResult(answer, steps, trace);
    }

    private Message newMessage(String sessionId, String role, String content,
                               String toolName, String toolCall, Integer step) {
        Message m = new Message();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setToolName(toolName);
        m.setToolCall(toolCall);
        m.setStep(step);
        m.setStatus(1);
        return m;
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\n", " ").replace("\"", "'");
    }
}
