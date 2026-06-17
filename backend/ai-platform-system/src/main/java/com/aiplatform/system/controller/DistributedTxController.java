package com.aiplatform.system.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.system.config.DistributedTxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分布式事务配置 API — 后台可调, 关闭不影响产品运行.
 *
 * <p>前端管理面板: /system/distributed-tx (TODO) — 切开关, 实时生效 (Reload).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/distributed-tx")
@RequiredArgsConstructor
public class DistributedTxController {

    private final DistributedTxProperties props;

    @GetMapping
    public Result<Map<String, Object>> get() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enabled", props.isEnabled());
        r.put("seataServerAddr", props.getSeataServerAddr());
        r.put("txServiceGroup", props.getTxServiceGroup());
        r.put("timeoutMs", props.getTimeoutMs());
        r.put("autoFallback", props.isAutoFallback());
        // 探测 seata TC 是否可达
        boolean tcReachable = probe(props.getSeataServerAddr());
        r.put("seataTcReachable", tcReachable);
        r.put("effectiveMode", !props.isEnabled() ? "OFF" : (tcReachable ? "SEATA" : "LOCAL_FALLBACK"));
        return Result.success(r);
    }

    @PostMapping
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        if (body.containsKey("enabled")) props.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.containsKey("seataServerAddr")) props.setSeataServerAddr(String.valueOf(body.get("seataServerAddr")));
        if (body.containsKey("txServiceGroup")) props.setTxServiceGroup(String.valueOf(body.get("txServiceGroup")));
        if (body.containsKey("timeoutMs")) props.setTimeoutMs(((Number) body.get("timeoutMs")).intValue());
        if (body.containsKey("autoFallback")) props.setAutoFallback(Boolean.TRUE.equals(body.get("autoFallback")));
        log.info("[DIST-TX] config updated: enabled={} addr={} group={} timeout={}ms fallback={}",
                props.isEnabled(), props.getSeataServerAddr(), props.getTxServiceGroup(), props.getTimeoutMs(), props.isAutoFallback());
        return get();
    }

    private boolean probe(String addr) {
        if (addr == null || addr.isBlank()) return false;
        try {
            String[] parts = addr.split(":");
            int port = Integer.parseInt(parts[1]);
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(parts[0], port), 1000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
