package com.aiplatform.agent.service;

import com.aiplatform.agent.entity.Conversation;
import com.aiplatform.agent.entity.Message;
import com.aiplatform.agent.mapper.ConversationMapper;
import com.aiplatform.agent.mapper.MessageMapper;
import com.aiplatform.redis.distributed.DistributedCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * ★ v3.x 性能优化: 会话列表缓存 (Chat 页面按 agent 列出历史会话).
 * <p>消息 history 不缓存 (用户频繁发消息, 缓存命中率低且 stale 风险大).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final String CACHE_KEY_PREFIX_LIST = "aiplatform:conv:list:";
    private static final int CACHE_TTL_SEC = 120;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final DistributedCache cache;

    public Conversation open(Long agentId, String title) {
        Conversation c = new Conversation();
        c.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        c.setAgentId(agentId);
        c.setTitle(title == null ? "新会话" : title);
        c.setStatus(1);
        conversationMapper.insert(c);
        cache.invalidate(CACHE_KEY_PREFIX_LIST + agentId);
        return c;
    }

    public List<Message> history(String sessionId) {
        // 不缓存: 用户频繁发消息, 缓存命中率低
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getSessionId, sessionId)
                .orderByAsc(Message::getCreateTime));
    }

    public void append(Message m) {
        messageMapper.insert(m);
        // 不清 list 缓存 (append 不影响会话列表)
    }

    public List<Conversation> list(Long agentId) {
        return cache.getOrLoadJson(CACHE_KEY_PREFIX_LIST + agentId, CACHE_TTL_SEC,
            () -> conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getAgentId, agentId)
                .orderByDesc(Conversation::getCreateTime)),
            new TypeReference<List<Conversation>>() {});
    }
}