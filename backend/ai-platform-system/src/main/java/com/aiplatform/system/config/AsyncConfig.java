package com.aiplatform.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ★ P0-LEAD-1 开启 @Async (操作审计异步落库, 不阻塞主请求).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
