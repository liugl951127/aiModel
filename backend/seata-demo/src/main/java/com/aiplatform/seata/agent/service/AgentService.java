package com.aiplatform.seata.agent.service;

import com.aiplatform.seata.agent.entity.AgentInvokeLog;
import com.aiplatform.seata.agent.mapper.AgentInvokeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 智能体调用日志服务 — 写一行审计日志。
 *
 * <p>被 seata 协调时，{@code insert} 自动注册到全局事务；失败则被
 * {@code GlobalTransactional} 协调器触发回滚（undo_log 还原）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentInvokeLogMapper mapper;

    public Long log(Long userId, String agentCode, String prompt, String response, Long tokens, boolean success) {
        log.info("[agent-service] log invoke userId={} agentCode={} tokens={} success={}", userId, agentCode, tokens, success);
        AgentInvokeLog row = new AgentInvokeLog();
        row.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        row.setUserId(userId);
        row.setAgentCode(agentCode);
        row.setPrompt(prompt);
        row.setResponse(response);
        row.setTokens(tokens);
        row.setStatus(success ? 1 : 0);
        row.setCreateTime(LocalDateTime.now());
        mapper.insert(row);
        log.info("[agent-service] log inserted id={} traceId={}", row.getId(), row.getTraceId());
        return row.getId();
    }
}
