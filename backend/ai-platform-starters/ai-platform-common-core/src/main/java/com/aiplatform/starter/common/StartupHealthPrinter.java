package com.aiplatform.starter.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 启动健康自检：把端口、profile、Bean 数、本机 IP 一次性打印到日志。
 *
 * <p>在多副本部署 + 频繁启停的环境下，运维人员能直接 grep "BOOT-OVERVIEW"
 * 看到服务起来的全貌，省去 {@code jps}/{@code actuator/health} 的步骤。</p>
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class StartupHealthPrinter implements ApplicationRunner {

    private final ApplicationContext ctx;
    private final Environment env;

    @Override
    public void run(ApplicationArguments args) {
        String[] profiles = env.getActiveProfiles();
        int port = env.getProperty("server.port", Integer.class, 8080);
        int beanCount = ctx.getBeanDefinitionCount();
        String host;
        try { host = InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { host = "unknown"; }
        String appName = env.getProperty("spring.application.name", "unknown");
        log.info("================ BOOT-OVERVIEW ================");
        log.info("  application : {}", appName);
        log.info("  profiles    : {}", profiles.length == 0 ? "[default]" : Arrays.toString(profiles));
        log.info("  port        : {}", port);
        log.info("  host        : {}", host);
        log.info("  bean count  : {}", beanCount);
        log.info("  arg count   : {}", args.getSourceArgs().length);
        log.info("================================================");
    }
}
