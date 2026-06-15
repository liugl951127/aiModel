package com.aiplatform.seata;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Seata 分布式事务 demo 启动入口。
 *
 * <h2>业务场景</h2>
 * 用户在 AI Agent Platform 上跑一次 ReAct 任务，需要跨 3 个服务做"原子"操作：
 * <ol>
 *   <li>{@code user-service} 扣减用户的 ai_credits 余额（按调用 token 数）</li>
 *   <li>{@code agent-service} 写一条 agent_invoke_log（审计）</li>
 *   <li>{@code stats-service} 累加 agent 调用的 usage_stats 计数（看板）</li>
 * </ol>
 * 任意一步失败，三者全部回滚 — 这就是 seata 分布式事务的存在意义。
 *
 * <p>本 demo 把 3 个逻辑微服务塞进同一个 Spring Boot 进程，通过 3 个独立
 * DataSource + seata AT 模式实现真实分布式事务协调。生产环境拆分 3 个服务
 * jar 后代码无需改动。</p>
 *
 * <h2>启用</h2>
 * <pre>
 *   # 1. 启 nacos（seata TC 注册中心）
 *   docker run -d --name nacos -p 8848:8848 -e MODE=standalone nacos/nacos-server:v2.3.1
 *
 *   # 2. 启 seata-server（TC）
 *   docker run -d --name seata -p 8091:8091 \
 *     -e SEATA_CONFIG_NAME=file:/root/seata-config/registry \
 *     -e SEATA_IP=127.0.0.1 \
 *     seataio/seata-server:2.0.0
 *
 *   # 3. 启本服务
 *   java -jar seata-demo.jar
 * </pre>
 */
@EnableDiscoveryClient
@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
})
@org.mybatis.spring.annotation.MapperScan(
        basePackages = "com.aiplatform.seata.user.mapper",
        sqlSessionFactoryRef = "userSqlSessionFactory")
@org.mybatis.spring.annotation.MapperScan(
        basePackages = "com.aiplatform.seata.agent.mapper",
        sqlSessionFactoryRef = "agentSqlSessionFactory")
@org.mybatis.spring.annotation.MapperScan(
        basePackages = "com.aiplatform.seata.stats.mapper",
        sqlSessionFactoryRef = "statsSqlSessionFactory")
public class SeataDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeataDemoApplication.class, args);
    }
}
