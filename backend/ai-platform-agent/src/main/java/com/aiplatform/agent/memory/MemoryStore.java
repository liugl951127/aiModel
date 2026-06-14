package com.aiplatform.agent.memory;

import com.aiplatform.agent.entity.Conversation;
import com.aiplatform.agent.entity.Message;
import com.aiplatform.agent.service.ConversationService;
import com.aiplatform.starter.redis.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two-tier memory:
 *  - short-term: rolling window kept in Redis (latest N turns)
 *  - long-term: persisted to MySQL via {@link ConversationService}
 */
@Component
@RequiredArgsConstructor
public class MemoryStore {

    private static final int SHORT_TERM_TURNS = 10;
    private static final long SHORT_TERM_TTL_SECONDS = 24 * 3600;

    private final RedisUtils redisUtils;
    private final ConversationService conversationService;

    public List<Message> shortTerm(String sessionId) {
        String key = "memory:st:" + sessionId;
        String json = redisUtils.get(key);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return MemoryCodec.decode(json);
    }

    public void pushShortTerm(String sessionId, Message m) {
        String key = "memory:st:" + sessionId;
        List<Message> existing = new ArrayList<>(shortTerm(sessionId));
        existing.add(m);
        if (existing.size() > SHORT_TERM_TURNS) {
            existing = existing.subList(existing.size() - SHORT_TERM_TURNS, existing.size());
        }
        redisUtils.set(key, MemoryCodec.encode(existing), SHORT_TERM_TTL_SECONDS);
    }

    public List<Message> fullHistory(String sessionId) {
        return conversationService.history(sessionId);
    }

    public void persist(Conversation c, Message m) {
        conversationService.append(m);
        pushShortTerm(c.getSessionId(), m);
    }
}
