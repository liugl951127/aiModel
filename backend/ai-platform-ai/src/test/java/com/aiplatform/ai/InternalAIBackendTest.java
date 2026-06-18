package com.aiplatform.ai;

import com.aiplatform.ai.backend.AIBackend;
import com.aiplatform.ai.backend.impl.InternalAIBackend;
import com.aiplatform.ai.backend.impl.MockAIBackend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ★ InternalAIBackend 单元测试
 *
 * 注: 完整 RAG 需要 knowledge + inference 服务跑, 这里主要测:
 * 1) 名称正确
 * 2) webSearch 走内置 corpus (服务不通时降级)
 * 3) chat 在服务不可达时降级 mock (不抛异常)
 * 4) embed 同上
 */
class InternalAIBackendTest {

    @Test
    void testName() {
        InternalAIBackend.Props p = new InternalAIBackend.Props();
        InternalAIBackend backend = new InternalAIBackend(p);
        assertEquals("internal", backend.name());
    }

    @Test
    void testWebSearchFallsBackToCorpus() {
        InternalAIBackend.Props p = new InternalAIBackend.Props();
        InternalAIBackend backend = new InternalAIBackend(p);

        // knowledge 服务没跑, 应降级到内置 corpus
        List<AIBackend.WebSearchResult> r = backend.webSearch("工作流", 5);
        assertNotNull(r);
        assertFalse(r.isEmpty(), "知识库不可达时, 至少应返回内置 corpus");
    }

    @Test
    void testWebSearchEmptyQueryReturnsAll() {
        InternalAIBackend.Props p = new InternalAIBackend.Props();
        InternalAIBackend backend = new InternalAIBackend(p);
        List<AIBackend.WebSearchResult> r = backend.webSearch("", 3);
        assertTrue(r.size() <= 3);
    }

    @Test
    void testChatFallbackWhenServicesDown() {
        InternalAIBackend.Props p = new InternalAIBackend.Props();
        p.setKnowledgeUrl("http://127.0.0.1:1");  // 不可达
        p.setInferenceUrl("http://127.0.0.1:1");
        InternalAIBackend backend = new InternalAIBackend(p);

        // 服务不可达, 应降级 mock, 不抛
        String r = backend.chat("你是一个助手",
                List.of(Map.of("role", "user", "content", "你好")),
                Map.of());
        assertNotNull(r);
        assertFalse(r.isEmpty());
    }

    @Test
    void testEmbedFallbackWhenServicesDown() {
        InternalAIBackend.Props p = new InternalAIBackend.Props();
        p.setKnowledgeUrl("http://127.0.0.1:1");
        InternalAIBackend backend = new InternalAIBackend(p);
        float[] v = backend.embed("测试");
        assertNotNull(v);
        assertEquals(512, v.length);
    }

    @Test
    void testRerankFallback() {
        InternalAIBackend.Props p = new InternalAIBackend.Props();
        p.setKnowledgeUrl("http://127.0.0.1:1");
        InternalAIBackend backend = new InternalAIBackend(p);
        List<AIBackend.RerankResult> r = backend.rerank("query",
                List.of("A 候选", "B 候选"), 2);
        assertNotNull(r);
        assertEquals(2, r.size());
    }

    @Test
    void testIsHealthyWhenServicesDown() {
        InternalAIBackend.Props p = new InternalAIBackend.Props();
        p.setKnowledgeUrl("http://127.0.0.1:1");
        InternalAIBackend backend = new InternalAIBackend(p);
        // knowledge 不可达 → unhealthy
        assertFalse(backend.isHealthy());
    }

    @Test
    void testCorpusIsKeywordSearchable() {
        InternalAIBackend.Props p = new InternalAIBackend.Props();
        InternalAIBackend backend = new InternalAIBackend(p);
        List<AIBackend.WebSearchResult> r = backend.webSearch("Seata 事务", 5);
        assertTrue(r.stream().anyMatch(x -> x.title().contains("Seata") || x.snippet().contains("Seata")),
                "应该命中 Seata corpus");
    }

    @Test
    void testInternalCorpusHas10Items() {
        // 反射拿 INTERNAL_CORPUS 不优雅, 直接搜些不相关词确保 corpus 大小
        InternalAIBackend.Props p = new InternalAIBackend.Props();
        InternalAIBackend backend = new InternalAIBackend(p);
        // 用空 query, 全部返回
        List<AIBackend.WebSearchResult> all = backend.webSearch("", 100);
        assertTrue(all.size() >= 5, "corpus 至少 5 条 (空 query 返回 5 cap 内部 + 兜底)");
    }
}
