package com.aiplatform.seata.order.service;

import com.aiplatform.seata.agent.service.AgentService;
import com.aiplatform.seata.stats.service.StatsService;
import com.aiplatform.seata.user.service.UserService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ReAct 任务执行的全局事务入口。
 *
 * <p>封装"扣费 + 记日志 + 累计数"三步为一个 seata 全局事务。任意一步抛
 * 业务异常，全部回滚（用户的额度、agent 审计行、stats 计数都不变）。</p>
 *
 * <h2>AT 模式原理</h2>
 * <ol>
 *   <li>TM (本方法所在 Bean) 向 TC 注册全局事务，拿到 XID</li>
 *   <li>三个 RM (UserService/AgentService/StatsService) 在自己 DB 上执行本地事务，
 *       同时把"前镜像"和"后镜像"写 {@code undo_log}</li>
 *   <li>TM 抛异常 → TC 让所有 RM 用 undo_log 回滚到 step 2 之前</li>
 *   <li>TM 正常返回 → TC 让所有 RM 删 undo_log（异步）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInvokeService {

    private final UserService userService;
    private final AgentService agentService;
    private final StatsService statsService;

    /**
     * 正常调用 — 三个子事务全部成功，seata 协调 commit。
     */
    @GlobalTransactional(name = "agent-invoke-success", rollbackFor = Exception.class)
    public InvokeResult invokeSuccess(Long userId, String agentCode, String prompt, Long tokens) {
        log.info("[TM] BEGIN agent-invoke-success userId={} agentCode={} tokens={}", userId, agentCode, tokens);
        Long remaining = userService.deduct(userId, tokens);
        String response = "[mock response for: " + prompt + "]";
        Long logId = agentService.log(userId, agentCode, prompt, response, tokens, true);
        Long statId = statsService.increment(agentCode, tokens);
        log.info("[TM] COMMIT agent-invoke-success logId={} statId={} remaining={}", logId, statId, remaining);
        return new InvokeResult(remaining, logId, statId);
    }

    /**
     * 失败调用 — 用户扣费成功，但 agent log 写完后**主动抛**异常，seata 应该把
     * 用户的扣费也回滚（看到余额不变）。
     */
    @GlobalTransactional(name = "agent-invoke-rollback", rollbackFor = Exception.class)
    public InvokeResult invokeRollback(Long userId, String agentCode, String prompt, Long tokens) {
        log.info("[TM] BEGIN agent-invoke-rollback userId={} agentCode={} tokens={}", userId, agentCode, tokens);
        // 1. 扣费（成功）
        Long remaining = userService.deduct(userId, tokens);
        // 2. 写日志（成功）
        agentService.log(userId, agentCode, prompt, "[partial]", tokens, true);
        // 3. 累加 stats（成功）
        statsService.increment(agentCode, tokens);
        // 4. 模拟下游 LLM 调用失败
        log.info("[TM] simulating downstream LLM failure...");
        throw new IllegalStateException("downstream LLM call failed (simulated for rollback test)");
    }

    /** 统一结果。 */
    public record InvokeResult(Long remainingCredits, Long logId, Long statId) {}
}
