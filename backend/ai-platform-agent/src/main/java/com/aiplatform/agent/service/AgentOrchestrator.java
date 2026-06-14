package com.aiplatform.agent.service;

import com.aiplatform.agent.engine.AgentRunResult;
import com.aiplatform.agent.engine.ReActEngine;
import com.aiplatform.agent.entity.AgentEntity;
import com.aiplatform.agent.entity.Conversation;
import com.aiplatform.agent.entity.Message;
import com.aiplatform.agent.memory.MemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentService agentService;
    private final ConversationService conversationService;
    private final MemoryStore memoryStore;
    private final ReActEngine reActEngine;

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

        AgentRunResult result = reActEngine.run(agent, conv.getSessionId(), userInput);
        log.info("[AGENT] done session={} steps={} answer.len={}",
                conv.getSessionId(), result.getSteps(),
                result.getAnswer() == null ? 0 : result.getAnswer().length());
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
}
