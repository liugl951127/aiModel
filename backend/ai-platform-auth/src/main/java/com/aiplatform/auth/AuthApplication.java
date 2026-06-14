package com.aiplatform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.aiplatform.auth.feign")
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.aiplatform.auth",
        "com.aiplatform.starter.common",
        "com.aiplatform.starter.web",
        "com.aiplatform.starter.redis",
        "com.aiplatform.starter.secure",
        "com.aiplatform.starter.nacos"
})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
