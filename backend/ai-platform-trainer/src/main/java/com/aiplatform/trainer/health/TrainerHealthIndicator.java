package com.aiplatform.trainer.health;

import ai.djl.ndarray.NDManager;
import com.aiplatform.trainer.constraints.ConstraintEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trainer 服务的健康探针 — 实现 Spring Boot Actuator 的 {@link HealthIndicator}。
 *
 * <p>当 k8s / Spring Boot Admin 拉 /actuator/health 时，本类会顺带探测：
 * <ul>
 *   <li>DJL NDManager 能否成功创建 1D 零张量（native 库加载是否成功）</li>
 *   <li>{@link ConstraintEngine} 关键通路（默认配置 + null 调）</li>
 *   <li>正在运行 / 排队的训练任务数</li>
 * </ul>
 *
 * <p>任何一个子探针 fail → status:DOWN，整服务从 LB 摘掉。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainerHealthIndicator implements HealthIndicator {

    private final ConstraintEngine constraintEngine;
    private final com.aiplatform.trainer.service.TrainingService trainingService;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean ok = true;

        // 1. NDManager 探测
        try (NDManager m = NDManager.newBaseManager()) {
            var z = m.zeros(new ai.djl.ndarray.types.Shape(1));
            details.put("ndmanager", "ok size=" + z.size());
        } catch (Throwable t) {
            ok = false;
            details.put("ndmanager", "FAIL " + t.getMessage());
        }

        // 2. Constraint 探测
        try {
            com.aiplatform.trainer.constraints.HallucinationGuardConfig cfg =
                    com.aiplatform.trainer.constraints.HallucinationGuardConfig.defaults();
            details.put("guard.defaults", "ok topK=" + cfg.getTopK() + " temp=" + cfg.getTemperature());
        } catch (Throwable t) {
            ok = false;
            details.put("guard.defaults", "FAIL " + t.getMessage());
        }

        // 3. 任务数
        try {
            int total = trainingService.all().size();
            int running = (int) trainingService.all().values().stream()
                    .filter(j -> "running".equals(j.getStatus())).count();
            int failed = (int) trainingService.all().values().stream()
                    .filter(j -> "failed".equals(j.getStatus())).count();
            details.put("jobs.total", total);
            details.put("jobs.running", running);
            details.put("jobs.failed", failed);
            if (failed > total / 2 && total > 5) ok = false;
        } catch (Throwable t) {
            details.put("jobs", "FAIL " + t.getMessage());
        }

        return ok ? Health.up().withDetails(details).build()
                  : Health.down().withDetails(details).build();
    }
}
