package com.aiplatform.agent.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ai-platform-knowledge", fallbackFactory = KnowledgeServiceFallback.class)
public interface KnowledgeServiceClient {

    @GetMapping("/api/knowledge/search-all")
    String search(@RequestParam("query") String query, @RequestParam(value = "topK", defaultValue = "3") int topK);
}
