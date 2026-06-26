package com.aiplatform.trainer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Trainer service. Runs Java-side model training (DJL) and exports ONNX.
 *
 * <p>MyBatis-Plus mapper bean 需要 SqlSessionFactory, 不能 exclude DataSourceAutoConfiguration.
 * 如遇沙箱无 MySQL, 仍需 MySQL 可达才能启动 mapper (有 connection fail-fast fallback).
 */
@EnableAsync
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.aiplatform.trainer",
        // ★ v3.1 模块精简: 合并 inference -> trainer, scan 包含 inference 包
        "com.aiplatform.inference",
        "com.aiplatform.starter.common",
        "com.aiplatform.starter.web",
        "com.aiplatform.starter.mybatis",
        "com.aiplatform.starter.secure",
        "com.aiplatform.starter.nacos"
})
@MapperScan("com.aiplatform.trainer.mapper")
public class TrainerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainerApplication.class, args);
    }
}
