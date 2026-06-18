package com.aiplatform.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/**
 * ★ OP-10 启动校验: JWT_SECRET 禁止使用默认值 (生产安全)
 * 触发时机: SpringBoot 读取完 application.yml 后, 上下文初始化前.
 * 安全策略:
 *   - 默认 secret 含 "default" 字样 → 拒绝启动
 *   - secret 长度 < 32 → 拒绝启动
 *   - 仅在 prod profile 下校验, dev/test 跳过
 *
 * @author Mavis
 */
@Slf4j
@Component
public class JwtSecretValidator implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final String DEFAULT_SECRET = "ai-platform-default-secret-key-please-change-32+";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        String[] profiles = env.getActiveProfiles();
        boolean isProd = false;
        for (String p : profiles) {
            if (p.contains("prod") || p.equals("production")) {
                isProd = true;
                break;
            }
        }
        if (!isProd) {
            log.info("[Security] 非生产环境, 跳过 JWT_SECRET 强校验");
            return;
        }
        String secret = env.getProperty("jwt.secret", "");
        if (secret == null || secret.isBlank() || DEFAULT_SECRET.equals(secret) || secret.contains("default") || secret.length() < 32) {
            log.error("");
            log.error("╔══════════════════════════════════════════════════════════════╗");
            log.error("║  [安全] JWT_SECRET 配置非法, 启动中止!                              ║");
            log.error("║  原因:                                                            ║");
            if (secret == null || secret.isBlank()) {
                log.error("║    - jwt.secret 未设置 (env JWT_SECRET 缺失)                       ║");
            } else if (DEFAULT_SECRET.equals(secret)) {
                log.error("║    - jwt.secret 使用了默认值 (极易被伪造)                          ║");
            } else if (secret.contains("default")) {
                log.error("║    - jwt.secret 含 'default' 字样 (不安全)                       ║");
            } else if (secret.length() < 32) {
                log.error("║    - jwt.secret 长度 < 32 字符                                    ║");
            }
            log.error("║  修法:                                                            ║");
            log.error("║    export JWT_SECRET=$(openssl rand -base64 48)                    ║");
            log.error("║    或在 .env / Nacos / Vault 配 32+ 字符的随机串                       ║");
            log.error("╚══════════════════════════════════════════════════════════════╝");
            log.error("");
            // 中止启动
            throw new IllegalStateException("JWT_SECRET 配置不安全, 启动中止. 请设置 32+ 字符随机串.");
        }
        log.info("[Security] JWT_SECRET 校验通过 (长度={})", secret.length());
    }
}
