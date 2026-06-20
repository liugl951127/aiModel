package com.aiplatform.system.config;

import com.aiplatform.system.controller.MonitorController;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 主动健康探活: 定时 HTTP GET 各服务的 /health 端点, 写入 MonitorController.
 * <p>默认 30 秒一次, 启动时立即一次.</p>
 *
 * @author liugl
 * @since 2026-06-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthProbe {

    private final MonitorController monitor;

    @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
    private String nacosAddr;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "health-probe");
        t.setDaemon(true);
        return t;
    });

    /** 服务名 -> 健康 URL (HTTP 端口)
     *  真实端口从 nacos 拉, 这里用常用默认, 失败不影响监控. */
    private static final Map<String, String> SERVICE_URLS = Map.ofEntries(
            Map.entry("网关",   "http://127.0.0.1:9000/api/auth/health"),
            Map.entry("认证",   "http://127.0.0.1:9001/api/auth/health"),
            Map.entry("用户",   "http://127.0.0.1:9002/api/auth/health"),
            Map.entry("系统",   "http://127.0.0.1:9003/api/activity/recent"),
            Map.entry("模型",   "http://127.0.0.1:9004/api/model/list"),
            Map.entry("智能体", "http://127.0.0.1:9005/api/agent/health"),
            Map.entry("知识库", "http://127.0.0.1:9006/api/knowledge/health"),
            Map.entry("推理",   "http://127.0.0.1:9007/api/inference/health"),
            Map.entry("训练",   "http://127.0.0.1:9008/api/trainer/health"),
            Map.entry("文件",   "http://127.0.0.1:9010/api/files/health"),
            Map.entry("工作流", "http://127.0.0.1:9011/api/workflow/health")
    );

    @PostConstruct
    public void start() {
        // 启动立即一次
        scheduler.execute(this::probeAll);
        // 30 秒一次
        scheduler.scheduleAtFixedRate(this::probeAll, 30, 30, TimeUnit.SECONDS);
        log.info("HealthProbe 启动完成, 监控 {} 个服务", SERVICE_URLS.size());
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    private void probeAll() {
        for (Map.Entry<String, String> e : SERVICE_URLS.entrySet()) {
            String name = e.getKey();
            String url = e.getValue();
            long t0 = System.currentTimeMillis();
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                conn.connect();
                int code = conn.getResponseCode();
                long elapsed = System.currentTimeMillis() - t0;
                String status = (code >= 200 && code < 400) ? "up" : (code == 401 ? "up" : "down");
                String detail = "HTTP " + code + " · " + elapsed + "ms";
                monitor.reportServiceHealth(name, status, elapsed, detail);
                conn.disconnect();
            } catch (Exception ex) {
                long elapsed = System.currentTimeMillis() - t0;
                monitor.reportServiceHealth(name, "down", elapsed, "连接失败: " + ex.getClass().getSimpleName());
            }
        }
    }
}
