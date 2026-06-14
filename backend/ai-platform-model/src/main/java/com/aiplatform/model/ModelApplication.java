package com.aiplatform.model;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {"com.aiplatform.model", "com.aiplatform.common"})
@MapperScan("com.aiplatform.model.mapper")
public class ModelApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModelApplication.class, args);
    }
}
