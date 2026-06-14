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
 * <p>DataSourceAutoConfiguration is excluded; the service falls back to in-memory
 * job tracking when no MySQL is configured. Enable by removing the exclude
 * in production.
 */
@EnableAsync
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.aiplatform.trainer",
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
