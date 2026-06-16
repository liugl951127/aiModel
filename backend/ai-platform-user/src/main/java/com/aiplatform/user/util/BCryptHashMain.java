package com.aiplatform.user.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码 hash 生成器 — 用项目真实的 BCryptPasswordEncoder (Spring Security Crypto).
 *
 * <p>用法:
 * <pre>
 *   # 编译 + install (一次性)
 *   cd backend
 *   mvn -N install
 *   mvn -pl ai-platform-user -DskipTests install
 *
 *   # 跑 — 不传参 = 生成默认 3 个密码 (admin123/demo123/123456)
 *   LC_ALL=C.UTF-8 mvn -pl ai-platform-user exec:java \
 *     -Dexec.mainClass="com.aiplatform.user.util.BCryptHashMain" \
 *     -Dexec.classpathScope=runtime
 *
 *   # 跑 — 自定义密码
 *   LC_ALL=C.UTF-8 mvn -pl ai-platform-user exec:java \
 *     -Dexec.mainClass="com.aiplatform.user.util.BCryptHashMain" \
 *     -Dexec.args="MyPassword" \
 *     -Dexec.classpathScope=runtime
 * </pre>
 *
 * <p>输出可直接 UPDATE 到 sys_user.password 字段.</p>
 */
public class BCryptHashMain {

    public static void main(String[] args) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        if (args.length == 0) {
            // 默认 3 个
            for (String pwd : new String[]{"admin123", "demo123", "123456"}) {
                print(enc, pwd);
                System.out.println();
            }
        } else {
            print(enc, args[0]);
        }
    }

    private static void print(BCryptPasswordEncoder enc, String plaintext) {
        // Spring BCryptPasswordEncoder 内部硬编码 strength=10
        String hash = enc.encode(plaintext);
        boolean match = enc.matches(plaintext, hash);
        System.out.println("明文:   " + plaintext);
        System.out.println("hash:   " + hash);
        System.out.println("rounds: 10 (Spring BCryptPasswordEncoder 默认)");
        System.out.println("自验:   " + (match ? "通过" : "失败"));
    }
}
