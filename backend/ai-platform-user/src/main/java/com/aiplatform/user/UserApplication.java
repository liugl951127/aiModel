package com.aiplatform.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * User service entry point. Nacos is optional. Spring Boot's auto-config
 * will enable DataSourceAutoConfiguration only when a datasource is configured.
 */
@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.aiplatform.user",
        "com.aiplatform.starter.common",
        "com.aiplatform.starter.web",
        "com.aiplatform.starter.mybatis",
        "com.aiplatform.starter.redis",
        "com.aiplatform.starter.secure",
        "com.aiplatform.starter.nacos"
})
@MapperScan("com.aiplatform.user.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
