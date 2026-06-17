package com.aiplatform.workflow.service;

import com.aiplatform.workflow.entity.WorkflowSpecEntity;
import com.aiplatform.workflow.mapper.WorkflowSpecMapper;
import com.aiplatform.workflow.model.WorkflowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WorkflowSpecRepository DB 版单元测试 (mock mapper, 不连真 DB).
 *
 * <p>验证替换 ConcurrentHashMap 内存版后:
 * <ul>
 *   <li>save / getById / list / delete / duplicate 接口正常</li>
 *   <li>前端 JSON 格式 ({name,nodes,edges,x,y}) 能正确解析为 WorkflowSpec (steps)</li>
 *   <li>incrRunCount 走 mapper 原子 SQL</li>
 * </ul>
 * </p>
 */
class WorkflowSpecRepositoryTest {

    private WorkflowSpecMapper mapper;
    private WorkflowSpecRepository repo;

    @BeforeEach
    void setUp() {
        mapper = mock(WorkflowSpecMapper.class);
        repo = new WorkflowSpecRepository(mapper);
        // insert 时回填 id (模拟自增)
        doAnswer(inv -> {
            WorkflowSpecEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(System.nanoTime());
            return 1;
        }).when(mapper).insert(any(WorkflowSpecEntity.class));
        // incrRunCount 默认返 1
        when(mapper.incrRunCount(any())).thenReturn(1);
    }

    @Test
    void testSave_createsNewEntity() {
        String json = "{\"nodes\":[{\"id\":\"n1\"}],\"edges\":[]}";
        WorkflowSpecEntity saved = repo.save("LoRA 训练", "admin", "test", json, 1, 0);
        assertNotNull(saved.getId());
        assertEquals("LoRA 训练", saved.getName());
        assertEquals("admin", saved.getAuthor());
        assertEquals(json, saved.getSpecJson());
        verify(mapper).insert(any(WorkflowSpecEntity.class));
    }

    @Test
    void testList_callsMapper() {
        WorkflowSpecEntity e = new WorkflowSpecEntity();
        e.setId(1L); e.setName("test");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(e));
        List<WorkflowSpecEntity> list = repo.list();
        assertEquals(1, list.size());
        assertEquals("test", list.get(0).getName());
    }

    @Test
    void testDelete_callsMapper() {
        repo.delete(123L);
        verify(mapper).deleteById(123L);
    }

    @Test
    void testIncrRunCount() {
        repo.incrRunCount(456L);
        verify(mapper).incrRunCount(456L);
    }

    @Test
    void testParseJsonToLegacySpec() {
        // 前端 Workflow.vue 发的真实格式
        String json = "{\n" +
            "  \"name\": \"RAG 测试\",\n" +
            "  \"nodes\": [\n" +
            "    {\"id\":\"n1\",\"type\":\"kb_search\",\"name\":\"搜索知识\",\"x\":60,\"y\":60,\"params\":{\"topK\":5}},\n" +
            "    {\"id\":\"n2\",\"type\":\"infer_chat\",\"name\":\"AI 回答\",\"x\":300,\"y\":60,\"params\":{\"model\":\"qwen\"}}\n" +
            "  ],\n" +
            "  \"edges\": [\n" +
            "    {\"from\":\"n1\",\"to\":\"n2\",\"fromPort\":\"out\",\"toPort\":\"in\"}\n" +
            "  ]\n" +
            "}";
        WorkflowSpecEntity e = new WorkflowSpecEntity();
        e.setId(100L); e.setName("RAG 测试"); e.setSpecJson(json);

        WorkflowSpec spec = repo.toLegacySpec(e);
        assertEquals("100", spec.getId());
        assertEquals("RAG 测试", spec.getName());
        assertEquals(2, spec.getSteps().size());

        WorkflowSpec.Step s1 = spec.getSteps().get(0);
        assertEquals("kb_search", s1.getType());
        assertEquals("搜索知识", s1.getName());
        assertTrue(s1.getDependsOn() == null || s1.getDependsOn().isEmpty());

        WorkflowSpec.Step s2 = spec.getSteps().get(1);
        assertEquals("infer_chat", s2.getType());
        assertEquals(1, s2.getDependsOn().size());
        assertEquals("n1", s2.getDependsOn().get(0));
    }

    @Test
    void testListSimple_format() {
        WorkflowSpecEntity e = new WorkflowSpecEntity();
        e.setId(1L); e.setName("X"); e.setAuthor("admin");
        e.setNodeCount(3); e.setEdgeCount(2); e.setRunCount(5);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(e));

        List<Map<String, Object>> simple = repo.listSimple();
        assertEquals(1, simple.size());
        Map<String, Object> m = simple.get(0);
        assertEquals(1L, m.get("id"));
        assertEquals("X", m.get("name"));
        assertEquals(3, m.get("nodeCount"));
    }

    @Test
    void testDuplicate_preservesJson() {
        WorkflowSpecEntity src = new WorkflowSpecEntity();
        src.setId(10L); src.setName("原");
        src.setSpecJson("{\"k\":1}");
        src.setNodeCount(3); src.setEdgeCount(2);
        when(mapper.selectById(10L)).thenReturn(src);

        WorkflowSpecEntity copy = repo.duplicate(10L, "user2");
        assertNotNull(copy);
        assertEquals("原 (副本)", copy.getName());
        assertEquals("user2", copy.getAuthor());
        assertEquals("{\"k\":1}", copy.getSpecJson());
        assertEquals(0, copy.getRunCount());  // 复制后从 0 开始
    }
}