package com.aiplatform.system.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.system.entity.SysLoginAudit;
import com.aiplatform.system.mapper.SysLoginAuditMapper;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 系统活动流控制器.
 * <p>提供两类接口:
 * <ol>
 *   <li>{@code GET /api/activity/stream} — SSE 长连接, 实时推送系统/训练/Agent 等事件
 *       给前端 LiveTickerBar 组件, 替代之前假数据</li>
 *   <li>{@code GET /api/activity/recent} — 最近 30 条历史事件, 给页面初始化时填充</li>
 * </ol>
 *
 * <h2>事件格式 (SSE data 字段 JSON)</h2>
 * <pre>{@code
 * {
 *   "type":   "train" | "agent" | "kb" | "wf" | "sys",
 *   "text":   "用户 X 训练了模型 Y",
 *   "actor":  "liugl",
 *   "action": { "label": "查看", "path": "/train" }
 * }
 * }</pre>
 *
 * @author liugl
 * @since 2026-06-17
 */
@Slf4j
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final SysLoginAuditMapper loginAuditMapper;

    /**
     * 当前在线 SSE 客户端列表.
     */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 最近事件环形缓存 (供 SSE 客户端连上后回放 + /recent 查询).
     */
    private static final int RECENT_LIMIT = 30;
    private final Deque<Map<String, Object>> recentEvents = new ArrayDeque<>(RECENT_LIMIT);

    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "activity-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /**
     * SSE 长连接: 客户端连上后, 服务端把 recentEvents 立刻回放, 然后持续推送新事件.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L); // 永不过期
        emitters.add(emitter);

        // 回放历史
        try {
            for (Map<String, Object> e : recentEvents) {
                emitter.send(SseEmitter.event().name("activity").data(e));
            }
            // 立即推一条 hello, 让前端确认连上
            emitter.send(SseEmitter.event().name("hello").data(Map.of(
                    "type", "sys", "text", "活动流已连接 (" + emitters.size() + " 个客户端)"
            )));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(t -> emitters.remove(emitter));
        return emitter;
    }

    /**
     * 最近事件列表 (JSON 数组, 替代前端假数据).
     */
    @GetMapping("/recent")
    public Result<List<Map<String, Object>>> recent() {
        // 1) 从内存缓存取
        List<Map<String, Object>> r = new ArrayList<>(recentEvents);
        // 2) 不足时用登录审计填充
        if (r.size() < 5) {
            loginAuditMapper.selectList(null).stream().limit(RECENT_LIMIT).forEach(a -> {
                r.add(Map.of(
                        "type", "sys",
                        "text", a.getUsername() + " 在 " + a.getLoginIp() + " 登录了系统",
                        "actor", a.getUsername() == null ? "unknown" : a.getUsername()
                ));
            });
        }
        return Result.success(r);
    }

    /**
     * 业务侧推事件入口 (供其它服务 Feign 调用, 或系统内部发事件).
     */
    public void publish(String type, String text, String actor, String action) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", type);
        e.put("text", text);
        e.put("actor", actor);
        e.put("time", LocalDateTime.now().toString());
        if (action != null) e.put("action", action);

        // 写缓存
        synchronized (recentEvents) {
            if (recentEvents.size() >= RECENT_LIMIT) recentEvents.pollFirst();
            recentEvents.offerLast(e);
        }
        // 推所有客户端
        for (SseEmitter em : emitters) {
            try {
                em.send(SseEmitter.event().name("activity").data(e));
            } catch (IOException ex) {
                emitters.remove(em);
                em.completeWithError(ex);
            }
        }
    }

    /**
     * 应用启动时启动心跳, 推一个系统事件让所有在线客户端感知.
     */
    @jakarta.annotation.PostConstruct
    public void startHeartbeat() {
        heartbeat.scheduleAtFixedRate(() -> {
            if (emitters.isEmpty()) return;
            Map<String, Object> beat = Map.of(
                    "type", "sys",
                    "text", "心跳检测 " + LocalDateTime.now().toString().substring(11, 19),
                    "actor", "system"
            );
            emitters.removeIf(em -> {
                try {
                    em.send(SseEmitter.event().name("activity").data(beat));
                    return false;
                } catch (Exception e) {
                    return true;
                }
            });
        }, 30, 30, TimeUnit.SECONDS);

        // 启动时发一条 welcome
        publish("sys", "活动流服务已启动, 监听所有系统事件", "system", null);
        log.info("ActivityController 启动完成, 等待 SSE 客户端连接");
    }
}
