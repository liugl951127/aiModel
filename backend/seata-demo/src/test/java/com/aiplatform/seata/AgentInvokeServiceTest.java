package com.aiplatform.seata;

import com.aiplatform.seata.agent.service.AgentService;
import com.aiplatform.seata.order.service.AgentInvokeService;
import com.aiplatform.seata.stats.service.StatsService;
import com.aiplatform.seata.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentInvokeService 单元测试 — 用 Mockito 模拟三个下游服务。
 *
 * <p>目的：验证编排逻辑（调用顺序、参数传递、异常传播），
 * 不依赖 Spring 上下文 / 数据库 / seata TC。</p>
 *
 * <p>为什么不用 @SpringBootTest：沙箱无 seata TC (8091)，
 * 真实的 @GlobalTransactional 调用会抛 {@code No available service}。
 * 想跑真实的分布式事务，请参考 README 起 seata-server 后跑
 * {@code SeataIntegrationTest}（带 @Disabled 标注）。</p>
 */
@DisplayName("AgentInvokeService 编排单元测试")
class AgentInvokeServiceTest {

    @Mock private UserService userService;
    @Mock private AgentService agentService;
    @Mock private StatsService statsService;

    @InjectMocks private AgentInvokeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("正常调用：3 个服务按序调用 1 次")
    void testInvokeSuccess_callsAllThreeServicesInOrder() {
        // given
        when(userService.deduct(1L, 50L)).thenReturn(9950L);
        when(agentService.log(eq(1L), eq("A-DEFAULT01"), anyString(), anyString(), eq(50L), eq(true)))
                .thenReturn(100L);
        when(statsService.increment("A-DEFAULT01", 50L)).thenReturn(200L);

        // when
        AgentInvokeService.InvokeResult result = service.invokeSuccess(1L, "A-DEFAULT01", "hi", 50L);

        // then
        assertNotNull(result);
        assertEquals(9950L, result.remainingCredits());
        assertEquals(100L, result.logId());
        assertEquals(200L, result.statId());

        // verify order: user -> agent -> stats
        var inOrder = inOrder(userService, agentService, statsService);
        inOrder.verify(userService).deduct(1L, 50L);
        inOrder.verify(agentService).log(eq(1L), eq("A-DEFAULT01"), anyString(), anyString(), eq(50L), eq(true));
        inOrder.verify(statsService).increment("A-DEFAULT01", 50L);
    }

    @Test
    @DisplayName("余额不足：user 抛异常后，agent 和 stats 都不应被调用")
    void testInvokeSuccess_shortCircuit_whenUserDeductFails() {
        // given
        when(userService.deduct(anyLong(), anyLong()))
                .thenThrow(new com.aiplatform.common.exception.BusinessException(
                        com.aiplatform.common.result.ResultCode.FAIL, "user_credits_not_enough"));

        // when + then
        assertThrows(com.aiplatform.common.exception.BusinessException.class, () ->
                service.invokeSuccess(2L, "A-DEFAULT01", "expensive", 99999L));

        // verify: agent / stats 都没被调用
        verifyNoInteractions(agentService);
        verifyNoInteractions(statsService);
    }

    @Test
    @DisplayName("agent 写日志抛异常后，stats 也不应被调用（编排是顺序的）")
    void testInvokeSuccess_shortCircuit_whenAgentLogFails() {
        when(userService.deduct(anyLong(), anyLong())).thenReturn(9999L);
        when(agentService.log(anyLong(), anyString(), anyString(), anyString(), anyLong(), anyBoolean()))
                .thenThrow(new IllegalStateException("agent log failed"));

        assertThrows(IllegalStateException.class, () ->
                service.invokeSuccess(1L, "A-DEFAULT01", "test", 10L));

        // user 扣了费，agent 失败，stats 还没轮到
        verify(userService, times(1)).deduct(1L, 10L);
        verify(agentService, times(1)).log(eq(1L), eq("A-DEFAULT01"), eq("test"), anyString(), eq(10L), eq(true));
        verifyNoInteractions(statsService);
    }

    @Test
    @DisplayName("rollback 路径：3 步全跑后主动抛异常")
    void testInvokeRollback_executesAllThenThrows() {
        when(userService.deduct(2L, 30L)).thenReturn(70L);
        when(agentService.log(anyLong(), anyString(), anyString(), anyString(), anyLong(), anyBoolean()))
                .thenReturn(123L);
        when(statsService.increment(anyString(), anyLong())).thenReturn(456L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.invokeRollback(2L, "A-DEFAULT01", "hello", 30L));
        assertTrue(ex.getMessage().contains("downstream LLM"));

        // 3 步都跑了（最后才抛）
        verify(userService).deduct(2L, 30L);
        verify(agentService).log(eq(2L), eq("A-DEFAULT01"), eq("hello"), anyString(), eq(30L), eq(true));
        verify(statsService).increment("A-DEFAULT01", 30L);
    }
}
