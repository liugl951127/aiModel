package com.aiplatform.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableDiscoveryClient
@EnableAsync
@SpringBootApplication
@ComponentScan(basePackages = {"com.aiplatform.agent", "com.aiplatform.common"})
@MapperScan("com.aiplatform.agent.mapper")
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
