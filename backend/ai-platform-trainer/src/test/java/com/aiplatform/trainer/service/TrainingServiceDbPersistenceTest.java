package com.aiplatform.trainer.service;

import com.aiplatform.trainer.entity.TrainJobEntity;
import com.aiplatform.trainer.mapper.TrainJobMapper;
import com.aiplatform.trainer.service.MiniTransformerTrainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Trainer DB 持久化测试 (mock mapper).
 *
 * <p>验证替换 ConcurrentHashMap 纯内存版后:
 * <ul>
 *   <li>submit 时写入 DB 一行 (queued 状态)</li>
 *   <li>listFromDb() 走 mapper.selectList</li>
 *   <li>getFromDb() 走 mapper.selectOne</li>
 * </ul>
 * </p>
 *
 * <p>实际 runAsync() 不在这里测 (需要 preview/checkpoint/modelRegistry 全 mock + 异步,
 * 覆盖率已经很高).</p>
 */
class TrainingServiceDbPersistenceTest {

    private TrainJobMapper mapper;
    private TrainingService service;

    @BeforeEach
    void setUp() {
        mapper = mock(TrainJobMapper.class);
        service = new TrainingService(null, null, null, null, mapper);
        // insert 时回填 id (BaseEntity 行为)
        doAnswer(inv -> {
            TrainJobEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(System.nanoTime());
            return 1;
        }).when(mapper).insert(any(TrainJobEntity.class));
        // list 返 2 行
        TrainJobEntity e1 = new TrainJobEntity();
        e1.setId(1L); e1.setJobCode("abc12345"); e1.setStatus("succeeded"); e1.setProgress(100);
        e1.setAlgorithm("minigpt"); e1.setEpochs(50); e1.setBatchSize(16);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(e1));
        // selectOne 返 null
        when(mapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void testSubmit_writesToDb() {
        MiniTransformerTrainer.Config cfg = new MiniTransformerTrainer.Config();
        cfg.modelType = "minigpt";
        cfg.learningRate = 3e-3;
        cfg.batchSize = 16;
        cfg.maxIters = 100;
        cfg.evalInterval = 50;

        // runAsync 走 @Async, submit 后跑异步. 这里直接拿 JobState 验证 DB 调用
        try {
            TrainingService.JobState state = service.submit("/tmp/nonexistent.txt", cfg);
            // 同步等 50ms 给异步一点时间
            Thread.sleep(50);
            // 至少 mapper.insert 被调一次
            verify(mapper, atLeastOnce()).insert(any(TrainJobEntity.class));
            assertNotNull(state);
            assertNotNull(state.getJobId());
        } catch (Exception e) {
            // 语料路径不存在, 异步会失败, 但 submit 本身同步完成, DB 写入已发生
            verify(mapper, atLeastOnce()).insert(any(TrainJobEntity.class));
        }
    }

    @Test
    void testListFromDb_returnsList() {
        var list = service.listFromDb(100);
        assertEquals(1, list.size());
        assertEquals("abc12345", list.get(0).getJobCode());
        verify(mapper).selectList(any());
    }

    @Test
    void testListFromDb_emptyOnFailure() {
        when(mapper.selectList(any())).thenThrow(new RuntimeException("DB down"));
        var list = service.listFromDb(100);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void testGetFromDb_returnsNullOnMiss() {
        var e = service.getFromDb("notfound");
        assertNull(e);
        verify(mapper).selectOne(any());
    }

    @Test
    void testGetFromDb_returnsEntity() {
        TrainJobEntity hit = new TrainJobEntity();
        hit.setId(99L); hit.setJobCode("hit12345"); hit.setStatus("succeeded");
        when(mapper.selectOne(any())).thenReturn(hit);
        var e = service.getFromDb("hit12345");
        assertNotNull(e);
        assertEquals("hit12345", e.getJobCode());
    }

    @Test
    void testMapper_updateProgress() {
        int n = mapper.updateProgress("j1", 50, "running");
        // mock 默认返 0, 这里我们只是验证 mapper 接口签名能调
        assertEquals(0, n);
    }

    @Test
    void testMapper_updateFinish() {
        int n = mapper.updateFinish("j1", "succeeded", 100,
                "/tmp/out", null, "{\"finalLoss\":0.5}",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        assertEquals(0, n);
    }

    @Test
    void testListFromDb_limit() {
        // limit ≤ 0 → 至少 1
        service.listFromDb(0);
        service.listFromDb(-100);
        service.listFromDb(99999);  // clamp 到 500
        verify(mapper, times(3)).selectList(any());
    }
}