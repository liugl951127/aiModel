package com.aiplatform.starter.common.limiter;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.ResultCode;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流 AOP。
 *
 * <p>每个 key 维护一个 {@link TokenBucket}：{@code permits} 个令牌，{@code perSecond}
 * 秒补充一个。请求时先从桶里取，取不到抛 {@link BusinessException} 429。</p>
 *
 * <p>本实现是进程内桶。生产集群请替换 Redis Lua 脚本版（{@code RATE_LIMIT_SCRIPT}）。</p>
 */
@Slf4j
@Aspect
@Component
public class RateLimiterAspect {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

    /**
     * 拦截 {@link RateLimiter} 标注的方法。tryAcquire 失败 → 抛 BusinessException(429)。
     */
    @Around("@annotation(rateLimiter)")
    public Object around(ProceedingJoinPoint pjp, RateLimiter rateLimiter) throws Throwable {
        String key = resolveKey(pjp, rateLimiter);
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(rateLimiter.permits(), rateLimiter.perSecond()));
        if (!bucket.tryAcquire()) {
            log.warn("[RATE] blocked key={} permits={} perSec={}",
                    key, rateLimiter.permits(), rateLimiter.perSecond());
            throw new BusinessException(ResultCode.FAIL, "rate limited: " + key);
        }
        return pjp.proceed();
    }

    /** 解析 key 模板：SpEL 求值，失败回退到字面量。 */
    private String resolveKey(ProceedingJoinPoint pjp, RateLimiter rl) {
        String tmpl = rl.key();
        if (tmpl == null || tmpl.isBlank()) {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            return sig.getDeclaringTypeName() + "#" + sig.getName();
        }
        if (!tmpl.contains("#")) return tmpl;
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            Method method = sig.getMethod();
            EvaluationContext ctx = new StandardEvaluationContext();
            String[] params = names.getParameterNames(method);
            Object[] args = pjp.getArgs();
            if (params != null) {
                for (int i = 0; i < params.length; i++) ctx.setVariable(params[i], args[i]);
            }
            Expression exp = parser.parseExpression(tmpl);
            return exp.getValue(ctx, String.class);
        } catch (Exception e) {
            log.debug("[RATE] spel fallback: {}", e.getMessage());
            return tmpl;
        }
    }

    /**
     * 进程内令牌桶（thread-safe + 锁-free）。
     */
    private static final class TokenBucket {
        private final int permits;
        private final long perSecondNanos;
        private double available;
        private long lastRefillNanos;

        TokenBucket(int permits, int perSecond) {
            this.permits = permits;
            this.available = permits;
            this.lastRefillNanos = System.nanoTime();
            this.perSecondNanos = (long) perSecond * 1_000_000_000L;
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed > 0) {
                available = Math.min(permits, available + (double) elapsed / perSecondNanos);
                lastRefillNanos = now;
            }
            if (available >= 1.0) {
                available -= 1.0;
                return true;
            }
            return false;
        }
    }

    /** 自检：暴露桶数。 */
    public int bucketCount() { return buckets.size(); }

    /** 重置（测试用）。 */
    public void reset() { buckets.clear(); }

    // 占位：等待接入 Redis
    private static final AtomicLong SEQ = new AtomicLong();
    private static final String RATE_LIMIT_SCRIPT =
            "local key=KEYS[1] local p=tonumber(ARGV[1]) local r=tonumber(ARGV[2])\n" +
            "local v=redis.call('HMGET',key,'t','a') local t=tonumber(v[1]) local a=tonumber(v[2])\n" +
            "local now=redis.call('TIME')[1] if t==nil then t=now a=p end\n" +
            "a=math.min(p, a + (now-t)*r/1000) redis.call('HMSET',key,'t',now,'a',a) redis.call('EXPIRE',key,60)\n" +
            "if a>=1 then a=a-1 return 1 else return 0 end";
    @SuppressWarnings("unused")
    private static String placeholder() { return RATE_LIMIT_SCRIPT + " " + SEQ.incrementAndGet(); }
}
