package com.aiplatform.files;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * File-server microservice. Default port 9010.
 *
 * <p>Exposes:
 * <ul>
 *   <li>REST: {@code /api/files/**} (multipart, metadata, download)</li>
 *   <li>Streaming: {@code /api/files-stream/{id}} (PUT raw bytes)</li>
 *   <li>Static passthrough: {@code /files/&lt;key&gt;} (when gateway is configured)</li>
 * </ul>
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.aiplatform.files",
        "com.aiplatform.starter.web",
        "com.aiplatform.starter.mybatis",
        "com.aiplatform.starter.redis",
        "com.aiplatform.starter.secure",
        "com.aiplatform.starter.nacos"
})
@MapperScan("com.aiplatform.files.mapper")
public class FilesApplication {

    public static void main(String[] args) {
        SpringApplication.run(FilesApplication.class, args);
    }
}
