package com.aiplatform.starter.common.tracing;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪：每个 servlet 请求自动生成 traceId 写入 MDC 与响应头。
 *
 * <p>客户端可通过 {@code X-Trace-Id} 传入 traceId（用于跨服务串联），未传则
 * 生成 UUID。所有日志（logback pattern 加 {@code %X{traceId}}）自动带上
 * 该 ID，方便 ELK / Loki 检索。</p>
 *
 * <p>仅在 servlet 栈（{@code jakarta.servlet.Filter} 在 classpath）时生效，
 * reactive 服务（spring-cloud-gateway）不引这个 bean。</p>
 */
@Slf4j
@Component
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class TraceIdFilter implements Filter, Ordered {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER  = "X-Trace-Id";

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        String traceId = null;
        if (req instanceof HttpServletRequest http) {
            traceId = http.getHeader(HEADER);
        }
        if (traceId == null || traceId.isBlank() || traceId.length() > 64) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_KEY, traceId);
        if (resp instanceof HttpServletResponse http) {
            http.setHeader(HEADER, traceId);
        }
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
