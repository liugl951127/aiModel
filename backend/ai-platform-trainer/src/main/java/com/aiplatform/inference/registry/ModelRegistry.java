package com.aiplatform.inference.registry;

import com.aiplatform.inference.model.MiniGptModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers model bundles on disk and caches them in memory. Each model
 * directory is expected to contain a {@code config.json + weights.json} pair.
 */
@Slf4j
@Component("inferenceModelRegistry")
@RequiredArgsConstructor
public class ModelRegistry {

    @Value("${aiplatform.inference.bundle-root:/opt/ai-platform/inference-bundles}")
    private String bundleRoot;

    @Value("${aiplatform.inference.bundle-default:default}")
    private String defaultBundleName;

    private final Map<String, MiniGptModel> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            File root = new File(bundleRoot);
            if (!root.exists()) {
                log.warn("[REG] bundle root does not exist: {}", root.getAbsolutePath());
                return;
            }
            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs == null) return;
            for (File d : dirs) {
                try {
                    MiniGptModel m = MiniGptModel.loadFromDirectory(d.toPath());
                    cache.put(d.getName(), m);
                    log.info("[REG] loaded bundle {}", d.getName());
                } catch (Exception e) {
                    log.warn("[REG] skip bundle {}: {}", d.getName(), e.getMessage());
                }
            }
            if (!cache.containsKey(defaultBundleName) && !cache.isEmpty()) {
                defaultBundleName = cache.keySet().iterator().next();
            }
            log.info("[REG] total {} bundles loaded, default={}", cache.size(), defaultBundleName);
        } catch (Exception e) {
            log.error("[REG] init failed: {}", e.getMessage(), e);
        }
    }

    public MiniGptModel get(String code) {
        if (code == null || code.isBlank()) code = defaultBundleName;
        return cache.get(code);
    }

    public Map<String, String> summary() {
        Map<String, String> out = new HashMap<>();
        for (String k : cache.keySet()) out.put(k, "loaded");
        return out;
    }

    public void reload(String code, Path path) {
        MiniGptModel m = MiniGptModel.loadFromDirectory(path);
        cache.put(code, m);
    }
}
