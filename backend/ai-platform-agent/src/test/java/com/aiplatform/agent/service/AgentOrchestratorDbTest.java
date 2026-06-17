package com.aiplatform.agent.service;

import com.aiplatform.agent.engine.AgentRunResult;
import com.aiplatform.agent.engine.ReActEngine;
import com.aiplatform.agent.entity.AgentEntity;
import com.aiplatform.agent.entity.AgentInvokeLogEntity;
import com.aiplatform.agent.entity.Conversation;
import com.aiplatform.agent.mapper.AgentInvokeLogMapper;
import com.aiplatform.agent.memory.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentOrchestrator DB 持久化测试 (mock mapper + memory + engine).
 *
 * <p>验证:
 * <ul>
 *   <li>chat() 写入 invoke_log 起始行 (status=RUNNING)</li>
 *   <li>成功完成后 updateById (status=OK, output, durationMs)</li>
 *   <li>失败时 updateById (status=ERROR, errorMessage)</li>
 *   <li>listInvokeLogs 走 mapper</li>
 * </ul>
 * </p>
 */
class AgentOrchestratorDbTest {

    private AgentService agentService;
    private ConversationService conversationService;
    private MemoryStore memoryStore;
    private ReActEngine reActEngine;
    private AgentInvokeLogMapper invokeLogMapper;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        conversationService = mock(ConversationService.class);
        memoryStore = mock(MemoryStore.class);
        reActEngine = mock(ReActEngine.class);
        invokeLogMapper = mock(AgentInvokeLogMapper.class);

        // insert 时回填 id
        doAnswer(inv -> {
            AgentInvokeLogEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(System.nanoTime());
            return 1;
        }).when(invokeLogMapper).insert(any(AgentInvokeLogEntity.class));
        // updateById 默认返 1
        when(invokeLogMapper.updateById(any())).thenReturn(1);
        // list 返 2 个
        AgentInvokeLogEntity a = new AgentInvokeLogEntity();
        a.setId(1L); a.setAgentId(1L); a.setSessionId("s1"); a.setStatus("OK");
        when(invokeLogMapper.selectList(any())).thenReturn(Collections.singletonList(a));

        orchestrator = new AgentOrchestrator(agentService, conversationService,
                memoryStore, reActEngine, invokeLogMapper);
    }

    @Test
    void testChat_writesInvokeLogStartAndFinish() {
        AgentEntity agent = new AgentEntity();
        agent.setId(1L);
        agent.setAgentName("test");
        when(agentService.getById(1L)).thenReturn(agent);
        AgentRunResult result = new AgentRunResult("hello", 1, Collections.emptyList());
        when(reActEngine.run(any(), anyString(), anyString())).thenReturn(result);

        AgentRunResult r = orchestrator.chat(1L, "session-1", "hi");
        assertNotNull(r);
        assertEquals("hello", r.getAnswer());
        // insert 调一次 (起始 RUNNING)
        verify(invokeLogMapper).insert(any(AgentInvokeLogEntity.class));
        // updateById 调一次 (完成 OK + output + durationMs)
        verify(invokeLogMapper).updateById(any(AgentInvokeLogEntity.class));
    }

    @Test
    void testChat_exceptionUpdatesLogAsError() {
        AgentEntity agent = new AgentEntity();
        agent.setId(2L);
        agent.setAgentName("err-test");
        when(agentService.getById(2L)).thenReturn(agent);
        when(reActEngine.run(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("simulated LLM failure"));

        assertThrows(RuntimeException.class,
                () -> orchestrator.chat(2L, "session-2", "boom"));
        // insert 调一次
        verify(invokeLogMapper).insert(any(AgentInvokeLogEntity.class));
        // updateById 调一次 (status=ERROR)
        verify(invokeLogMapper).updateById(any(AgentInvokeLogEntity.class));
    }

    @Test
    void testListInvokeLogs() {
        var list = orchestrator.listInvokeLogs(1L, 50);
        assertEquals(1, list.size());
        verify(invokeLogMapper).selectList(any());
    }

    @Test
    void testListInvokeLogs_emptyOnFailure() {
        when(invokeLogMapper.selectList(any())).thenThrow(new RuntimeException("DB down"));
        var list = orchestrator.listInvokeLogs(1L, 50);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void testChat_dbFailureDoesNotBreakFlow() {
        AgentEntity agent = new AgentEntity();
        agent.setId(3L);
        agent.setAgentName("db-down");
        when(agentService.getById(3L)).thenReturn(agent);
        when(reActEngine.run(any(), anyString(), anyString()))
                .thenReturn(new AgentRunResult("ok", 1, Collections.emptyList()));
        // insert 抛 (DB down)
        doThrow(new RuntimeException("DB down")).when(invokeLogMapper).insert(any(AgentInvokeLogEntity.class));
        // 不应抛, chat 仍正常返回
        AgentRunResult r = assertDoesNotThrow(() -> orchestrator.chat(3L, "s3", "test"));
        assertEquals("ok", r.getAnswer());
    }
}