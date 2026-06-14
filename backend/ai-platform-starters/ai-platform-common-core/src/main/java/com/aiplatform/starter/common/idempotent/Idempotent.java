package com.aiplatform.starter.common.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解。标在写入型 Controller 方法上，避免重复请求导致数据脏写。
 *
 * <h2>使用方式</h2>
 * <p>客户端在请求头里传 {@code X-Idempotency-Key}（UUID v4）。服务器端
 * 在 Redis 里记下 {@code key -> result}，TTL 内相同 key 直接返回缓存，
 * 不再执行方法体。</p>
 *
 * <pre>
 *   &#64;PostMapping("/submit")
 *   &#64;Idempotent(ttlSec = 600)
 *   public Result submit(...) { ... }
 * </pre>
 *
 * <p>没有 Redis 时降级为进程内 {@code ConcurrentHashMap}，方便单实例测试。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 缓存 TTL（秒）。 */
    int ttlSec() default 600;

    /** key 来源：header / param / 自定义。 */
    Source source() default Source.HEADER;

    /** 自定义 SpEL key 模板（仅当 source = SPEL）。 */
    String spEL() default "";

    enum Source { HEADER, PARAM, SPEL }
}
