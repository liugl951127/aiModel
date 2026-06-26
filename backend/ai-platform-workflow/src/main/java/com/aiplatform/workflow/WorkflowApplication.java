package com.aiplatform.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * Workflow orchestrator. Default port 9011.
 */
@EnableAsync
@EnableFeignClients(basePackages = "com.aiplatform.workflow.feign")
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.aiplatform.workflow",
        // ★ v3.1 模块精简: 合并 files -> workflow, scan 包含 files 包
        "com.aiplatform.files",
        "com.aiplatform.starter.common",
        "com.aiplatform.starter.web",
        "com.aiplatform.starter.mybatis",
        "com.aiplatform.starter.redis",
        "com.aiplatform.starter.secure",
        "com.aiplatform.starter.nacos"
        // redis-starter 不在这里 scan, 由 spring boot 的 AutoConfiguration.imports 自动加载.
        // 这样 Redis 不可用时, 不会强制创建 RedissonClient Bean, 服务照样启动.
})
@MapperScan({"com.aiplatform.workflow.mapper", "com.aiplatform.files.mapper"})
public class WorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
