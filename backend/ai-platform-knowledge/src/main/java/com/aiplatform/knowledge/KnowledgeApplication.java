package com.aiplatform.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {"com.aiplatform.knowledge", "com.aiplatform.common"})
@MapperScan("com.aiplatform.knowledge.mapper")
public class KnowledgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
