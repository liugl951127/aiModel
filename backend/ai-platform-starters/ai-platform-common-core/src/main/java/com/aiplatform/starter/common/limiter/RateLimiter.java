package com.aiplatform.starter.common.limiter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解。标在 Controller 方法上，AOP 拦截后用令牌桶限制调用速率。
 *
 * <h2>三种使用模式</h2>
 * <ul>
 *   <li>{@code @RateLimiter(key = "submit", permits = 5, perSecond = 1)} — 每秒 5 个令牌</li>
 *   <li>{@code @RateLimiter(key = "user:#userId", permits = 10, perSecond = 1)} — 按用户 ID 限流</li>
 *   <li>{@code @RateLimiter(key = "ip:#remoteAddr", permits = 100, perSecond = 60)} — 按 IP 限流（每分钟 100）</li>
 * </ul>
 *
 * <p>占位符（{@code #xxx}）运行时从 SpEL 求值，支持 {@code #userId / #remoteAddr / #requestId}。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /** 限流 key 模板，SpEL；省略时用方法签名全局。 */
    String key() default "";

    /** 桶容量。 */
    int permits() default 10;

    /** 桶填充速率：每 N 秒增加 permits 个令牌。 */
    int perSecond() default 1;
}
