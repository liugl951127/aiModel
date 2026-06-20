package com.aiplatform.ai.autoconfigure;

import com.aiplatform.ai.backend.AIBackend;
import com.aiplatform.ai.backend.AIBackendRouter;
import com.aiplatform.ai.backend.AIBackendSwitcher;
import com.aiplatform.ai.backend.ExternalWebSearchAdapter;
import com.aiplatform.ai.backend.impl.DuckDuckGoSearchAdapter;
import com.aiplatform.ai.backend.impl.HttpAIBackend;
import com.aiplatform.ai.backend.impl.InternalAIBackend;
import com.aiplatform.ai.backend.impl.MockAIBackend;
import com.aiplatform.ai.backend.impl.OllamaAIBackend;
import com.aiplatform.ai.backend.impl.OnnxAIBackend;
import com.aiplatform.ai.local.LocalEmbeddingClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ★ AI Backend 自动装配 (Spring Boot 3 + auto-configuration).
 *
 * <p>任何模块只需依赖 ai-platform-ai.jar, 就自动获得:
 * <ul>
 *   <li>{@link AIBackendSwitcher} - 业务统一入口</li>
 *   <li>{@link AIBackendRouter} - 多后端路由器</li>
 *   <li>{@link LocalEmbeddingClient} - 本地 embedding 缓存</li>
 *   <li>5 个 AI 后端实现 (mock/internal/onnx/ollama/http, 按 aiplatform.ai.backend 选)</li>
 *   <li>外部 web 搜索适配器 (按 aiplatform.ai.search-mode=external 启)</li>
 * </ul>
 *
 * <p>不需要在每个业务模块手写 @ComponentScan("com.aiplatform.ai").
 *
 * <h2>触发条件</h2>
 * {@code ConditionalOnClass(AIBackendSwitcher.class)} - 类路径有这个类就激活.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(AIBackendSwitcher.class)
public class AiBackendAutoConfiguration {

    /** 业务统一入口 */
    @Bean
    @ConditionalOnMissingBean
    public AIBackendSwitcher aiBackendSwitcher(AIBackendRouter router,
                                               ObjectProvider<List<ExternalWebSearchAdapter>> externalAdaptersProvider) {
        // 外部适配器可能为空 (默认 internal 模式), Spring 注入空 list 兼容
        List<ExternalWebSearchAdapter> adapters = externalAdaptersProvider.getIfAvailable(java.util.Collections::emptyList);
        return new AIBackendSwitcher(router, adapters);
    }

    /** Router */
    @Bean
    @ConditionalOnMissingBean
    public AIBackendRouter aiBackendRouter(List<AIBackend> backends) {
        return new AIBackendRouter(backends);
    }

    /** Local embedding 客户端 (注入 AIBackend bean) */
    @Bean
    @ConditionalOnMissingBean
    public LocalEmbeddingClient localEmbeddingClient(ObjectProvider<AIBackend> backendProvider) {
        // backend 可能未就绪 (比如 mock 还没 @Bean 注册), 用 mock 兜底
        AIBackend backend = backendProvider.getIfAvailable(MockAIBackend::new);
        return new LocalEmbeddingClient(backend);
    }

    // ========== 5 个 AI 后端实现 ==========

    /** 默认 mock (无 token 也能跑) */
    @Bean
    @ConditionalOnProperty(name = "aiplatform.ai.backend", havingValue = "mock", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MockAIBackend mockAIBackend() {
        return new MockAIBackend();
    }

    /** 内部 RAG */
    @Bean
    @ConditionalOnProperty(name = "aiplatform.ai.backend", havingValue = "internal")
    @ConditionalOnMissingBean
    public InternalAIBackend internalAIBackend(InternalAIBackend.Props props) {
        return new InternalAIBackend(props);
    }

    /** ONNX 本地模型 */
    @Bean
    @ConditionalOnProperty(name = "aiplatform.ai.backend", havingValue = "onnx")
    @ConditionalOnMissingBean
    public OnnxAIBackend onnxAIBackend(OnnxAIBackend.Props props) {
        return new OnnxAIBackend(props);
    }

    /** Ollama 本地 LLM */
    @Bean
    @ConditionalOnProperty(name = "aiplatform.ai.backend", havingValue = "ollama")
    @ConditionalOnMissingBean
    public OllamaAIBackend ollamaAIBackend(OllamaAIBackend.Props props) {
        return new OllamaAIBackend(props);
    }

    /** HTTP 远端 */
    @Bean
    @ConditionalOnProperty(name = "aiplatform.ai.backend", havingValue = "http")
    @ConditionalOnMissingBean
    public HttpAIBackend httpAIBackend(HttpAIBackend.Props props) {
        return new HttpAIBackend(props);
    }

    // ========== 外部 web 搜索 ==========

    /** DuckDuckGo 外部搜索 */
    @Bean
    @ConditionalOnProperty(name = "aiplatform.ai.search-mode", havingValue = "external")
    @ConditionalOnMissingBean
    public ExternalWebSearchAdapter duckDuckGoSearchAdapter() {
        return new DuckDuckGoSearchAdapter();
    }
}
