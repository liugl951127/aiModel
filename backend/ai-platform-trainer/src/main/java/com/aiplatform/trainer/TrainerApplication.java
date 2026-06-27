package com.aiplatform.trainer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Trainer service. Runs Java-side model training (DJL) and exports ONNX.
 *
 * <p>MyBatis-Plus mapper bean 需要 SqlSessionFactory, 不能 exclude DataSourceAutoConfiguration.
 * 如遇沙箱无 MySQL, 仍需 MySQL 可达才能启动 mapper (有 connection fail-fast fallback).
 *
 * ★ v3.x: 强制 PYTORCH_FLAVOR=cpu 跳过 DJL cuda 探测, 消除
 *   'No matching cuda flavor for win-x86_64 found: cu065' WARN.
 *   生产环境如需 GPU, 启动时 export PYTORCH_FLAVOR=cu121 并加 pytorch-native-cuda 依赖.
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

    static {
        // ★ DJL 0.36 cuda 探测屏蔽: Windows 上 LibUtils 启动时即使只引 cpu native
        //   也会探测 cu065/cu118 等, 找不到匹配 native 就 WARN.
        //   强制设 cpu flavor 后, DJL 直接用 cpu native, 跳过 cuda 探测.
        if (System.getProperty("PYTORCH_FLAVOR") == null
                || System.getProperty("PYTORCH_FLAVOR").isEmpty()) {
            System.setProperty("PYTORCH_FLAVOR", "cpu");
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(TrainerApplication.class, args);
    }
}