package com.aiplatform.agent.service;

import com.aiplatform.agent.engine.AgentRunResult;
import com.aiplatform.agent.engine.ReActEngine;
import com.aiplatform.agent.entity.AgentEntity;
import com.aiplatform.agent.entity.AgentInvokeLogEntity;
import com.aiplatform.agent.entity.Conversation;
import com.aiplatform.agent.entity.Message;
import com.aiplatform.agent.mapper.AgentInvokeLogMapper;
import com.aiplatform.agent.memory.MemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentService agentService;
    private final ConversationService conversationService;
    private final MemoryStore memoryStore;
    private final ReActEngine reActEngine;
    private final AgentInvokeLogMapper invokeLogMapper;  // ★ DB 持久化

    public AgentRunResult chat(Long agentId, String sessionId, String userInput) {
        AgentEntity agent = agentService.getById(agentId);
        Conversation conv = ensureConversation(sessionId, agentId, userInput);

        // Persist user turn
        Message userMsg = new Message();
        userMsg.setSessionId(conv.getSessionId());
        userMsg.setRole("user");
        userMsg.setContent(userInput);
        userMsg.setStatus(1);
        memoryStore.persist(conv, userMsg);

        // ★ DB: 调 invoke_log 起始行
        long t0 = System.currentTimeMillis();
        AgentInvokeLogEntity log0 = new AgentInvokeLogEntity();
        log0.setAgentId(agentId);
        log0.setSessionId(conv.getSessionId());
        log0.setUserId("anonymous");
        log0.setInput(userInput);
        log0.setStatus("RUNNING");
        log0.setStartedAt(LocalDateTime.now());
        try { invokeLogMapper.insert(log0); } catch (Exception ex) { log.warn("[AGENT-LOG] 写 DB 失败: {}", ex.getMessage()); }

        AgentRunResult result;
        String errorMessage = null;
        try {
            result = reActEngine.run(agent, conv.getSessionId(), userInput);
        } catch (Throwable t) {
            errorMessage = t.getMessage() == null ? t.getClass().getName() : t.getMessage();
            log.error("[AGENT] 异常: {}", errorMessage, t);
            // 写失败日志
            try {
                log0.setStatus("ERROR");
                log0.setErrorMessage(errorMessage);
                log0.setDurationMs(System.currentTimeMillis() - t0);
                log0.setFinishedAt(LocalDateTime.now());
                invokeLogMapper.updateById(log0);
            } catch (Exception ex) { /* 忽略 */ }
            throw new RuntimeException(errorMessage, t);
        }
        long dur = System.currentTimeMillis() - t0;

        // ★ DB: 写 invoke_log 完成行
        try {
            log0.setOutput(result.getAnswer());
            log0.setDurationMs(dur);
            log0.setStatus("OK");
            log0.setFinishedAt(LocalDateTime.now());
            // AgentRunResult 暂时只有 answer/steps/trace, tokens/knowledgeHits 后续可加
            invokeLogMapper.updateById(log0);
        } catch (Exception ex) { log.warn("[AGENT-LOG] updateById 失败: {}", ex.getMessage()); }

        log.info("[AGENT] done session={} steps={} answer.len={} dur={}ms",
                conv.getSessionId(), result.getSteps(),
                result.getAnswer() == null ? 0 : result.getAnswer().length(), dur);
        return result;
    }

    private Conversation ensureConversation(String sessionId, Long agentId, String firstInput) {
        if (sessionId != null && !sessionId.isBlank()) {
            // Trust the caller's session id
            Conversation c = new Conversation();
            c.setSessionId(sessionId);
            c.setAgentId(agentId);
            return c;
        }
        String title = firstInput == null ? "新会话" : firstInput.substring(0, Math.min(20, firstInput.length()));
        return conversationService.open(agentId, title);
    }

    public List<Message> history(String sessionId) {
        return memoryStore.fullHistory(sessionId);
    }

    /**
     * 查 invoke 历史 (DB), 重启服务不丢.
     */
    public List<AgentInvokeLogEntity> listInvokeLogs(Long agentId, int limit) {
        try {
            return invokeLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AgentInvokeLogEntity>()
                    .eq(agentId != null, "agent_id", agentId)
                    .eq("deleted", 0)
                    .orderByDesc("create_time")
                    .last("LIMIT " + Math.max(1, Math.min(500, limit)))
            );
        } catch (Exception e) {
            log.warn("[AGENT-LOG] listInvokeLogs 失败: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
