package com.aiplatform.starter.common.gray;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灰度发布过滤器：按白名单 + 概率灰度部分流量到新版本。
 *
 * <h2>配置</h2>
 * <pre>
 *   aiplatform:
 *     gray:
 *       enabled: true
 *       ratio: 0.1                # 灰度 10% 流量
 *       whitelist-users: u1,u2    # 白名单用户全部走新版本
 *       header: X-Gray-Tag
 *       tag: v2
 * </pre>
 *
 * <p>判定逻辑：白名单用户命中 → 头加 {@code X-Gray-Hit=1}；否则按 ratio
 * 随机命中，命中同样加头。下游服务（用同一个头）可决定是否走新逻辑。</p>
 *
 * <p>仅在 servlet 栈（{@code OncePerRequestFilter} 在 classpath）时生效，
 * reactive 服务（gateway）不引这个 bean。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(prefix = "aiplatform.gray", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
public class GrayReleaseFilter extends OncePerRequestFilter {

    public static final String HEADER_HIT = "X-Gray-Hit";
    public static final String HEADER_TAG = "X-Gray-Tag";

    @Value("${aiplatform.gray.ratio:0.0}")
    private double ratio;
    @Value("${aiplatform.gray.tag:}")
    private String tag;
    @Value("${aiplatform.gray.whitelist-users:}")
    private String whitelist;
    @Value("${aiplatform.gray.header:X-Gray-Tag}")
    private String userHeader;

    private volatile Set<String> userSet = new HashSet<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        boolean hit = decide(req);
        if (hit) {
            resp.setHeader(HEADER_HIT, "1");
            if (tag != null && !tag.isBlank()) resp.setHeader(HEADER_TAG, tag);
            req = new HeaderWrappedRequest(req, userHeader, tag);
        }
        chain.doFilter(req, resp);
    }

    private boolean decide(HttpServletRequest req) {
        String user = req.getHeader(userHeader);
        if (user != null && !user.isBlank() && userSet.contains(user)) return true;
        return ratio > 0 && ThreadLocalRandom.current().nextDouble() < ratio;
    }

    public void setWhitelist(String csv) {
        this.userSet = csv == null ? new HashSet<>()
                : new HashSet<>(Arrays.asList(csv.split("\\s*,\\s*")));
    }

    private static final class HeaderWrappedRequest extends HttpServletRequestWrapper {
        private final String name;
        private final String value;
        HeaderWrappedRequest(HttpServletRequest req, String name, String value) {
            super(req);
            this.name = name;
            this.value = value;
        }
        @Override public String getHeader(String n) {
            if (n != null && n.equalsIgnoreCase(name)) return value;
            return super.getHeader(n);
        }
    }
}
