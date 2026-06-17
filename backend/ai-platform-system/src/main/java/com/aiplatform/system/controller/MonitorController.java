package com.aiplatform.system.controller;

import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * 系统实时监控控制器.
 *
 * <p>三类接口:
 * <ol>
 *   <li>{@code GET /api/monitor/snapshot}  一次性聚合快照 (服务健康/节点状态/业务指标/AI 互动)</li>
 *   <li>{@code GET /api/monitor/stream}    SSE 实时推送 (3 秒一次)</li>
 *   <li>{@code GET /api/monitor/metrics}   滚动时序数据 (QPS/响应时间/错误率, 给 ECharts)</li>
 * </ol>
 *
 * <h2>设计取舍</h2>
 * <p>不接 Prometheus / Grafana, 全部在内存里维护 (单实例够用).
 * 多实例部署时, 客户端按 gateway URL 拉即可, 每实例只监控自己视角.
 * 真要分布式监控, 接 Micrometer + Prometheus, 这里留扩展点.</p>
 *
 * @author liugl
 * @since 2026-06-17
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    // ====== 内存里的实时数据 ======

    /** 9 个核心服务的健康状态 (gateway/auth/user/system/model/inference/knowledge/files/trainer) */
    private final Map<String, ServiceHealth> services = new ConcurrentHashMap<>();

    /** QPS 时序 (60 个点, 3 秒一个, 共 3 分钟) */
    private final Deque<MetricPoint> qpsSeries = new ArrayDeque<>(60);
    /** 平均响应时间 (ms) */
    private final Deque<MetricPoint> latencySeries = new ArrayDeque<>(60);
    /** 错误率 (0-1) */
    private final Deque<MetricPoint> errorRateSeries = new ArrayDeque<>(60);
    /** AI 推理调用数 (每 3 秒) */
    private final Deque<MetricPoint> aiCallSeries = new ArrayDeque<>(60);
    /** workflow 节点执行数 */
    private final Deque<MetricPoint> workflowRunSeries = new ArrayDeque<>(60);

    /** SSE 客户端 */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** 累计计数 */
    private final java.util.concurrent.atomic.AtomicLong totalRequests = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalErrors = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalAiCalls = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalWorkflowRuns = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalTokens = new java.util.concurrent.atomic.AtomicLong(0);

    private final ScheduledExecutorService collector = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "monitor-collector");
        t.setDaemon(true);
        return t;
    });

    // ============================================================
    // 接口 1: 一次性快照
    // ============================================================

    @GetMapping("/snapshot")
    public Result<Map<String, Object>> snapshot() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ts", System.currentTimeMillis());
        r.put("services", snapshotServices());
        r.put("counters", snapshotCounters());
        r.put("business", snapshotBusiness());
        r.put("ai", snapshotAi());
        r.put("alerts", checkAlerts());
        return Result.success(r);
    }

    private List<ServiceHealth> snapshotServices() {
        return new ArrayList<>(services.values());
    }

    private Map<String, Object> snapshotCounters() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalRequests", totalRequests.get());
        r.put("totalErrors", totalErrors.get());
        r.put("totalAiCalls", totalAiCalls.get());
        r.put("totalWorkflowRuns", totalWorkflowRuns.get());
        r.put("totalTokens", totalTokens.get());
        long reqs = Math.max(1, totalRequests.get());
        r.put("errorRate", (double) totalErrors.get() / reqs);
        return r;
    }

    private Map<String, Object> snapshotBusiness() {
        Map<String, Object> r = new LinkedHashMap<>();
        // 这些数字由 ActivityController / BizController 维护, 这里给一些静态演示值
        r.put("activeUsers", 12);   // 占位, 真接登录态统计
        r.put("onlineUsers", 5);
        r.put("todayOrders", 23);
        r.put("todayQueries", 145);
        return r;
    }

    private Map<String, Object> snapshotAi() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("modelsLoaded", 4);  // MiniGpt/BGE/Reranker 等
        r.put("activeWorkflows", 8);
        r.put("runsToday", 56);
        r.put("tokensToday", 234_567L);
        r.put("avgLatencyMs", 320);
        return r;
    }

    /** 告警检查 */
    private List<Map<String, Object>> checkAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (ServiceHealth s : services.values()) {
            if ("down".equals(s.status)) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("level", "critical");
                a.put("service", s.name);
                a.put("message", s.name + " 服务离线");
                a.put("ts", System.currentTimeMillis());
                alerts.add(a);
            }
        }
        if (totalErrors.get() > 100) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("level", "warning");
            a.put("service", "system");
            a.put("message", "错误数过多: " + totalErrors.get());
            a.put("ts", System.currentTimeMillis());
            alerts.add(a);
        }
        return alerts;
    }

    // ============================================================
    // 接口 2: SSE 实时流
    // ============================================================

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        try {
            // 立即推一个 snapshot
            emitter.send(SseEmitter.event().name("snapshot").data(snapshotData()));
            emitter.send(SseEmitter.event().name("hello").data(Map.of("ts", System.currentTimeMillis(), "msg", "监控流已连接 (" + emitters.size() + " 个客户端)")));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(t -> emitters.remove(emitter));
        return emitter;
    }

    private Map<String, Object> snapshotData() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("services", snapshotServices());
        r.put("counters", snapshotCounters());
        return r;
    }

    // ============================================================
    // 接口 3: 时序数据 (ECharts)
    // ============================================================

    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("qps", seriesToList(qpsSeries));
        r.put("latency", seriesToList(latencySeries));
        r.put("errorRate", seriesToList(errorRateSeries));
        r.put("aiCalls", seriesToList(aiCallSeries));
        r.put("workflowRuns", seriesToList(workflowRunSeries));
        return Result.success(r);
    }

    private List<Map<String, Object>> seriesToList(Deque<MetricPoint> dq) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (MetricPoint p : dq) {
            r.add(Map.of("ts", p.ts, "value", p.value));
        }
        return r;
    }

    // ============================================================
    // 内部: 报告入口 (供业务调用, 不暴露给前端)
    // ============================================================

    /** 业务报告: 服务健康检查结果 (由定时任务或 health 端点触发) */
    public void reportServiceHealth(String name, String status, long responseMs, String detail) {
        ServiceHealth h = services.computeIfAbsent(name, k -> new ServiceHealth(k, status, responseMs, detail, System.currentTimeMillis()));
        h.status = status;
        h.responseMs = responseMs;
        h.detail = detail;
        h.checkedAt = System.currentTimeMillis();
        // 推流
        push(Map.of("type", "service", "data", h));
    }

    /** 业务报告: API 请求 */
    public void reportRequest(boolean isError, long tokens) {
        totalRequests.incrementAndGet();
        if (isError) totalErrors.incrementAndGet();
        if (tokens > 0) {
            totalAiCalls.incrementAndGet();
            totalTokens.addAndGet(tokens);
        }
    }

    /** 业务报告: workflow 跑一次 */
    public void reportWorkflowRun() {
        totalWorkflowRuns.incrementAndGet();
    }

    // ============================================================
    // 内部: 定时采集 + 推送
    // ============================================================

    @jakarta.annotation.PostConstruct
    public void start() {
        // 初始化时塞 9 个服务 (用占位, 等真实 health 探活覆盖)
        String[] defaults = {"网关", "认证", "用户", "系统", "模型", "推理", "知识库", "文件", "训练"};
        for (String n : defaults) {
            services.put(n, new ServiceHealth(n, "unknown", 0, "未探活", System.currentTimeMillis()));
        }
        // 启动定时: 每 3 秒采一次
        collector.scheduleAtFixedRate(this::tick, 3, 3, TimeUnit.SECONDS);
        log.info("MonitorController 启动完成");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        // 模拟数据 (生产接真实埋点)
        double qps = 50 + Math.random() * 100;
        double latency = 200 + Math.random() * 300;
        double errRate = Math.random() * 0.05;
        double ai = Math.random() * 20;
        double wf = Math.random() * 5;
        addPoint(qpsSeries, now, qps);
        addPoint(latencySeries, now, latency);
        addPoint(errorRateSeries, now, errRate);
        addPoint(aiCallSeries, now, ai);
        addPoint(workflowRunSeries, now, wf);
        // 推流
        push(Map.of("type", "metric",
                "data", Map.of(
                        "qps", qps, "latency", latency, "errorRate", errRate,
                        "aiCalls", ai, "workflowRuns", wf, "ts", now)));
    }

    private void addPoint(Deque<MetricPoint> dq, long ts, double value) {
        synchronized (dq) {
            if (dq.size() >= 60) dq.pollFirst();
            dq.offerLast(new MetricPoint(ts, value));
        }
    }

    private void push(Map<String, Object> payload) {
        emitters.removeIf(em -> {
            try {
                em.send(SseEmitter.event().name("update").data(payload));
                return false;
            } catch (Exception e) {
                return true;
            }
        });
    }

    // ============================================================
    // 内部类
    // ============================================================

    public static class ServiceHealth {
        public String name;
        public String status;       // up / down / warn / unknown
        public long responseMs;
        public String detail;
        public long checkedAt;

        public ServiceHealth() {}
        public ServiceHealth(String name, String status, long responseMs, String detail, long checkedAt) {
            this.name = name; this.status = status; this.responseMs = responseMs;
            this.detail = detail; this.checkedAt = checkedAt;
        }
    }

    public static class MetricPoint {
        public long ts;
        public double value;
        public MetricPoint() {}
        public MetricPoint(long ts, double value) { this.ts = ts; this.value = value; }
    }
}
