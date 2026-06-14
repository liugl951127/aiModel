package com.aiplatform.agent.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KnowledgeServiceFallback implements FallbackFactory<KnowledgeServiceClient> {
    @Override
    public KnowledgeServiceClient create(Throwable cause) {
        log.error("[FEIGN] knowledge fallback: {}", cause.getMessage());
        return (query, topK) -> "(知识库服务暂不可用) " + cause.getMessage();
    }
}
