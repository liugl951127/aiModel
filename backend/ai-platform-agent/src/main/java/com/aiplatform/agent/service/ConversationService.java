package com.aiplatform.agent.service;

import com.aiplatform.agent.entity.Conversation;
import com.aiplatform.agent.entity.Message;
import com.aiplatform.agent.mapper.ConversationMapper;
import com.aiplatform.agent.mapper.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public Conversation open(Long agentId, String title) {
        Conversation c = new Conversation();
        c.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        c.setAgentId(agentId);
        c.setTitle(title == null ? "新会话" : title);
        c.setStatus(1);
        conversationMapper.insert(c);
        return c;
    }

    public List<Message> history(String sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getSessionId, sessionId)
                .orderByAsc(Message::getCreateTime));
    }

    public void append(Message m) {
        messageMapper.insert(m);
    }

    public List<Conversation> list(Long agentId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getAgentId, agentId)
                .orderByDesc(Conversation::getCreateTime));
    }
}
