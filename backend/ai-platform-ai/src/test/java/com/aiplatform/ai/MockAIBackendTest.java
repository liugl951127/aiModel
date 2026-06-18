package com.aiplatform.ai;

import com.aiplatform.ai.backend.AIBackend;
import com.aiplatform.ai.backend.impl.MockAIBackend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ★ MockAIBackend 单元测试
 */
class MockAIBackendTest {

    private final MockAIBackend backend = new MockAIBackend();

    @Test
    void testName() {
        assertEquals("mock", backend.name());
    }

    @Test
    void testChatReturnsNonEmpty() {
        String r = backend.chat("你是助手", List.of(Map.of("role", "user", "content", "你好")), Map.of());
        assertNotNull(r);
        assertFalse(r.isEmpty());
        assertTrue(r.contains("Mock AI"));
    }

    @Test
    void testEmbedDeterministic() {
        float[] v1 = backend.embed("你好世界");
        float[] v2 = backend.embed("你好世界");
        assertEquals(v1.length, v2.length);
        assertEquals(512, v1.length);
        // 同样输入, 同样输出
        for (int i = 0; i < v1.length; i++) {
            assertEquals(v1[i], v2[i], 0.0001f, "向量应该确定性");
        }
    }

    @Test
    void testEmbedNormalized() {
        float[] v = backend.embed("测试文本");
        float norm = 0;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        assertEquals(1.0f, norm, 0.01f, "向量应该归一化");
    }

    @Test
    void testEmbedDifferentTextsDiffer() {
        float[] v1 = backend.embed("人工智能");
        float[] v2 = backend.embed("美食");
        // 至少有一个分量不同
        boolean diff = false;
        for (int i = 0; i < v1.length; i++) {
            if (Math.abs(v1[i] - v2[i]) > 0.001f) { diff = true; break; }
        }
        assertTrue(diff, "不同文本应该产生不同向量");
    }

    @Test
    void testRerankReturnsSortedByScore() {
        List<String> candidates = List.of("人工智能大模型", "Java 编程", "BGE 嵌入模型", "其他");
        List<AIBackend.RerankResult> r = backend.rerank("AI 模型", candidates, 3);
        assertEquals(3, r.size());
        // 分数应该降序
        for (int i = 0; i < r.size() - 1; i++) {
            assertTrue(r.get(i).score() >= r.get(i + 1).score());
        }
    }

    @Test
    void testWebSearchEmptyQueryReturnsAll() {
        List<AIBackend.WebSearchResult> r = backend.webSearch("", 5);
        assertNotNull(r);
        assertFalse(r.isEmpty());
    }

    @Test
    void testWebSearchKeywordMatch() {
        List<AIBackend.WebSearchResult> r = backend.webSearch("Spring Cloud Alibaba", 3);
        assertFalse(r.isEmpty());
        boolean match = r.stream().anyMatch(x -> x.title().contains("Spring") || x.snippet().contains("Spring"));
        assertTrue(match, "应该匹配到 Spring Cloud Alibaba 文档");
    }

    @Test
    void testWebSearchReturnsAtMostTopK() {
        List<AIBackend.WebSearchResult> r = backend.webSearch("a", 2);
        assertTrue(r.size() <= 2);
    }

    @Test
    void testIsHealthy() {
        assertTrue(backend.isHealthy());
    }

    @Test
    void testBatchEmbed() {
        List<float[]> v = backend.embedBatch(List.of("a", "b", "c"));
        assertEquals(3, v.size());
        for (float[] vec : v) assertEquals(512, vec.length);
    }
}
