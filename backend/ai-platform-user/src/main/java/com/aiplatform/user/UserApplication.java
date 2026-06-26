package com.aiplatform.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * User + Auth service entry point. Nacos is optional. Spring Boot's auto-config
 * will enable DataSourceAutoConfiguration only when a datasource is configured.
 *
 * ★ v3.3 模块精简: 合并 auth -> user, 同时提供:
 *   - User/Tenant CRUD (端口 9002, 原 user)
 *   - Auth/Register/Login (原 auth 9001)
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.aiplatform.auth.feign")
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.aiplatform.user",
        // ★ v3.3 从 auth 合并入的包
        "com.aiplatform.auth",
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
