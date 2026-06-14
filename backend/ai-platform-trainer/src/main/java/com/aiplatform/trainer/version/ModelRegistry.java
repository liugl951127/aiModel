package com.aiplatform.trainer.version;

import com.aiplatform.common.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 模型版本注册表。
 *
 * <p>每次训练完成 / checkpoint 都注册一个版本号，记录超参、loss、guard 配置、
 * 关联的 inference bundle 路径。前端展示页直接对接这个注册表，按版本对比。</p>
 *
 * <p>存储：用 JSON 文件（{@code /opt/ai-platform/models/registry.json}），无 DB
 * 也能跑。生产可换成 MySQL / Redis。</p>
 */
@Slf4j
@Service
public class ModelRegistry {

    private final Path registryPath = Paths.get("/opt/ai-platform/models/registry.json");

    public synchronized List<Version> list() {
        return load().stream()
                .sorted(Comparator.<Version>comparingLong(Version::getCreatedAtMs).reversed())
                .collect(Collectors.toList());
    }

    public synchronized Optional<Version> get(String version) {
        return load().stream().filter(v -> Objects.equals(v.getVersion(), version)).findFirst();
    }

    public synchronized Version register(Version v) {
        v.setCreatedAtMs(System.currentTimeMillis());
        if (v.getVersion() == null) v.setVersion("v" + (load().size() + 1));
        List<Version> all = load();
        all.removeIf(x -> Objects.equals(x.getVersion(), v.getVersion()));
        all.add(v);
        save(all);
        log.info("[MODEL-REG] registered {} loss={} bundle={}", v.getVersion(), v.getFinalLoss(), v.getBundlePath());
        return v;
    }

    public synchronized void unregister(String version) {
        List<Version> all = load();
        all.removeIf(v -> Objects.equals(v.getVersion(), version));
        save(all);
        log.info("[MODEL-REG] unregistered {}", version);
    }

    private List<Version> load() {
        try {
            if (!Files.exists(registryPath)) return new ArrayList<>();
            String json = Files.readString(registryPath);
            if (json.isBlank()) return new ArrayList<>();
            return com.alibaba.fastjson2.JSON.parseArray(json, Version.class);
        } catch (IOException e) {
            log.warn("[MODEL-REG] load failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void save(List<Version> all) {
        try {
            Files.createDirectories(registryPath.getParent());
            Files.writeString(registryPath,
                    com.alibaba.fastjson2.JSON.toJSONString(all, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat));
        } catch (IOException e) {
            throw new IllegalStateException("registry save failed", e);
        }
    }

    /**
     * 模型版本元数据。
     */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Version {
        private String version;
        private String name;
        private String modelType;
        private int nLayer;
        private int nHead;
        private int nEmbd;
        private int blockSize;
        private int vocabSize;
        private double finalLoss;
        private int iters;
        private String guardJson;       // 防幻觉配置快照
        private String bundlePath;     // inference bundle 路径
        private String checkpointPath; // checkpoint 路径
        private long createdAtMs;
        private Long tenantId;
        private String createdBy;
    }
}
