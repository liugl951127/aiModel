package com.aiplatform.workflow.engine;

import com.aiplatform.workflow.entity.WorkflowRunEntity;
import com.aiplatform.workflow.mapper.WorkflowRunMapper;
import com.aiplatform.workflow.model.WorkflowRun;
import com.aiplatform.workflow.model.WorkflowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WorkflowEngine DB 持久化测试 (mock mapper + handlers).
 *
 * <p>验证:
 * <ul>
 *   <li>run() 成功后 workflow_run 表有 SUCCEEDED 行</li>
 *   <li>run() 失败后 workflow_run 表有 FAILED 行 + failedNodeName</li>
 *   <li>listFromDb() 走 mapper</li>
 *   <li>getFromDb() 走 mapper</li>
 * </ul>
 * </p>
 */
class WorkflowEngineDbTest {

    private WorkflowRunMapper mapper;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        mapper = mock(WorkflowRunMapper.class);
        // mock 一个空的 handlers 列表 (不跑真实步骤, 用 dummy handler)
        java.util.List<StepHandler> handlers = new ArrayList<>();
        handlers.add(new StepHandler() {
            @Override public String type() { return "noop"; }
            @Override public void execute(WorkflowSpec.Step step, java.util.Map<String, Object> ctx, StepLog log) {
                // 不做事情
            }
        });
        engine = new WorkflowEngine(handlers, mapper);
        engine.init();
        doAnswer(inv -> {
            WorkflowRunEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(System.nanoTime());
            return 1;
        }).when(mapper).insert(any(WorkflowRunEntity.class));
    }

    @Test
    void testRunSucceeded_writesDb() {
        WorkflowSpec spec = new WorkflowSpec();
        spec.setName("test");
        spec.setId("999");
        WorkflowSpec.Step s = new WorkflowSpec.Step();
        s.setType("noop");
        s.setName("step1");
        s.setParams(Collections.emptyMap());
        spec.setSteps(Collections.singletonList(s));

        WorkflowRun run = engine.run(spec);
        assertEquals("SUCCEEDED", run.getStatus().name());
        // mapper.insert 必调, mapper.updateFinish 必调
        verify(mapper, atLeastOnce()).insert(any(WorkflowRunEntity.class));
        verify(mapper).updateFinish(eq(run.getRunId()), eq("SUCCEEDED"), eq(100),
                anyString(), isNull(), isNull(), isNull(),
                anyLong(), any(), any());
    }

    @Test
    void testRunFailed_writesDbWithFailedNode() {
        // 注册一个会抛异常的 handler
        java.util.List<StepHandler> handlers = new ArrayList<>();
        handlers.add(new StepHandler() {
            @Override public String type() { return "bad"; }
            @Override public void execute(WorkflowSpec.Step step, java.util.Map<String, Object> ctx, StepLog log) {
                throw new RuntimeException("simulated failure");
            }
        });
        WorkflowEngine eng = new WorkflowEngine(handlers, mapper);
        eng.init();

        WorkflowSpec spec = new WorkflowSpec();
        spec.setName("fail-test");
        spec.setId("999");
        WorkflowSpec.Step s = new WorkflowSpec.Step();
        s.setType("bad");
        s.setName("will-fail");
        spec.setSteps(Collections.singletonList(s));

        WorkflowRun run = eng.run(spec);
        assertEquals("FAILED", run.getStatus().name());
        // mapper.updateFinish 必调, 带 failedNodeName + failedReason
        verify(mapper).updateFinish(eq(run.getRunId()), eq("FAILED"), anyInt(),
                anyString(), eq("will-fail"), eq("will-fail"), anyString(),
                anyLong(), any(), any());
    }

    @Test
    void testListFromDb() {
        WorkflowRunEntity e1 = new WorkflowRunEntity();
        e1.setId(1L); e1.setRunId("r1"); e1.setSpecName("test");
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(e1));
        var list = engine.listFromDb(100);
        assertEquals(1, list.size());
        verify(mapper).selectList(any());
    }

    @Test
    void testListFromDb_empty() {
        when(mapper.selectList(any())).thenThrow(new RuntimeException("db down"));
        var list = engine.listFromDb(100);
        assertTrue(list.isEmpty());
    }

    @Test
    void testGetFromDb() {
        WorkflowRunEntity hit = new WorkflowRunEntity();
        hit.setId(5L); hit.setRunId("r5");
        when(mapper.selectOne(any())).thenReturn(hit);
        var e = engine.getFromDb("r5");
        assertNotNull(e);
        assertEquals("r5", e.getRunId());
    }

    @Test
    void testGetFromDb_nullOnMiss() {
        when(mapper.selectOne(any())).thenReturn(null);
        var e = engine.getFromDb("nope");
        assertNull(e);
    }

    @Test
    void testInitFromDb_doesNotThrow() {
        when(mapper.selectList(any())).thenThrow(new RuntimeException("table missing"));
        // 启动加载失败不能抛, 只 log warn
        assertDoesNotThrow(() -> engine.initFromDb());
    }
}