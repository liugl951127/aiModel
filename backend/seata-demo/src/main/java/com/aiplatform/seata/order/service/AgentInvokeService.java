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
 * <h2>三层事务保障</h2>
 * <ol>
 *   <li>seata TC 在场：{@code @GlobalTransactional} 走 seata AT 模式（undo_log）</li>
 *   <li>seata TC 不可达：chained PTM 兜底，3 个 datasource 共享一个事务边界</li>
 *   <li>单 service 调用：{@code @Transactional} 让 spring 用 chained PTM 开启</li>
 * </ol>
 *
 * <p>也就是说：<strong>任意一个 service 方法被外部调用时</strong>，spring-tx
 * 会触发 chained PTM.begin，把 3 个 datasource 的事务绑在一起 —
 * 保证 3 步要么都成功要么都回滚。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInvokeService {

    private final UserService userService;
    private final AgentService agentService;
    private final StatsService statsService;

    @GlobalTransactional(name = "agent-invoke-success", rollbackFor = Exception.class)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public InvokeResult invokeSuccess(Long userId, String agentCode, String prompt, Long tokens) {
        return doInvoke(userId, agentCode, prompt, tokens, false);
    }

    @GlobalTransactional(name = "agent-invoke-rollback", rollbackFor = Exception.class)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public InvokeResult invokeRollback(Long userId, String agentCode, String prompt, Long tokens) {
        return doInvoke(userId, agentCode, prompt, tokens, true);
    }

    private InvokeResult doInvoke(Long userId, String agentCode, String prompt, Long tokens,
                                  boolean simulateFail) {
        log.info("[TM] BEGIN userId={} agentCode={} tokens={}", userId, agentCode, tokens);
        Long remaining = userService.deduct(userId, tokens);
        String response = simulateFail ? "[partial]" : "[mock response for: " + prompt + "]";
        Long logId = agentService.log(userId, agentCode, prompt, response, tokens, !simulateFail);
        Long statId = statsService.increment(agentCode, tokens);
        if (simulateFail) {
            log.info("[TM] simulating downstream LLM failure...");
            throw new IllegalStateException("downstream LLM call failed (simulated for rollback test)");
        }
        log.info("[TM] COMMIT remaining={} logId={} statId={}", remaining, logId, statId);
        return new InvokeResult(remaining, logId, statId);
    }

    public record InvokeResult(Long remainingCredits, Long logId, Long statId) {}
}
