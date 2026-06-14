package com.aiplatform.starter.common.idempotent;

import com.aiplatform.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 幂等 AOP 拦截。
 *
 * <p>从 servlet header/参数里取 {@code X-Idempotency-Key}（通过反射，
 * 不直接 import jakarta.servlet 让 reactive 服务也能编译），未指定时
 * 退化为方法签名 + 客户端 IP。命中缓存则直接返回原 result，不进入方法体。</p>
 *
 * <p>注意：reactive 服务下 AOP 不会触发（spring mvc 拦截器链才走 servlet），
 * 所以该 starter 的 servlet 工具反射不会导致 NoClassDefFoundError。</p>
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    private static final String HEADER = "X-Idempotency-Key";
    private static final String PARAM  = "idempotencyKey";

    /** 进程内缓存：key -> (deadlineEpochMs, resultJson)。生产可换 Redis SETNX。 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String key = resolveKey(pjp, idempotent);
        long now = System.currentTimeMillis();

        // 1. 命中
        CacheEntry hit = cache.get(key);
        if (hit != null && hit.deadlineMs > now) {
            log.info("[IDEMPOTENT] hit key={}", key);
            return hit.result;
        }
        // 2. 没命中 → 执行
        Object result = pjp.proceed();
        // 3. 缓存（仅 Result 成功状态）
        if (result instanceof Result<?> r && r.getCode() != null && r.getCode() == 200) {
            cache.put(key, new CacheEntry(now + TimeUnit.SECONDS.toMillis(idempotent.ttlSec()),
                    com.alibaba.fastjson2.JSON.toJSONString(r)));
        }
        return result;
    }

    /** 解析幂等 key。 */
    private String resolveKey(ProceedingJoinPoint pjp, Idempotent idem) {
        // 反射拿 HttpServletRequest / WebRequest（不在 reactive 服务 classpath）
        Object req = currentRequest();
        String headerVal = invokeStringHeader(req, HEADER);
        if (headerVal != null && !headerVal.isBlank()) return headerVal;
        String paramVal = invokeStringParam(req, PARAM);
        if (paramVal != null && !paramVal.isBlank()) return paramVal;
        if (idem.source() == Idempotent.Source.SPEL) {
            try {
                MethodSignature sig = (MethodSignature) pjp.getSignature();
                Method method = sig.getMethod();
                EvaluationContext ctx = new StandardEvaluationContext();
                String[] params = names.getParameterNames(method);
                Object[] args = pjp.getArgs();
                if (params != null) for (int i = 0; i < params.length; i++) ctx.setVariable(params[i], args[i]);
                Expression exp = parser.parseExpression(idem.spEL());
                String v = exp.getValue(ctx, String.class);
                if (v != null && !v.isBlank()) return v;
            } catch (Exception e) {
                log.debug("[IDEMPOTENT] spel failed: {}", e.getMessage());
            }
        }
        // 退化：方法签名 + 客户端 IP
        String ip = invokeClientIp(req);
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        return sig.getDeclaringTypeName() + "#" + sig.getName() + "@" + ip;
    }

    /**
     * 用反射拿当前 servlet request（兼容 reactive 不引 servlet 的场景）。
     * {@code RequestContextHolder.getRequestAttributes()} 已在 spring-core，
     * 但 {@code ServletRequestAttributes} 在 spring-web。走全 try-catch 反射。
     */
    private static Object currentRequest() {
        try {
            Class<?> holder = Class.forName("org.springframework.web.context.request.RequestContextHolder");
            Object attrs = holder.getMethod("getRequestAttributes").invoke(null);
            if (attrs == null) return null;
            Class<?> sa = Class.forName("org.springframework.web.context.request.ServletRequestAttributes");
            if (sa.isInstance(attrs)) {
                return sa.getMethod("getRequest").invoke(attrs);
            }
        } catch (Throwable t) {
            // reactive 栈或没引 spring-web：忽略
        }
        return null;
    }

    private static String invokeStringHeader(Object req, String name) {
        if (req == null) return null;
        try {
            Object v = req.getClass().getMethod("getHeader", String.class).invoke(req, name);
            return v == null ? null : v.toString();
        } catch (Throwable t) { return null; }
    }

    private static String invokeStringParam(Object req, String name) {
        if (req == null) return null;
        try {
            Object v = req.getClass().getMethod("getParameter", String.class).invoke(req, name);
            return v == null ? null : v.toString();
        } catch (Throwable t) { return null; }
    }

    private static String invokeClientIp(Object req) {
        if (req == null) return "?";
        try {
            Object fwd = req.getClass().getMethod("getHeader", String.class).invoke(req, "X-Forwarded-For");
            if (fwd != null && !fwd.toString().isBlank()) return fwd.toString().toString().split(",")[0].trim();
            Object ra = req.getClass().getMethod("getRemoteAddr").invoke(req);
            return ra == null ? "?" : ra.toString();
        } catch (Throwable t) { return "?"; }
    }

    /** 缓存条目：到期时间 + 序列化好的 result。 */
    private record CacheEntry(long deadlineMs, String result) {}
}
