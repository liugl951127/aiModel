# Seata Demo — 分布式事务 (AI Agent Platform 业务场景)

> AI Agent Platform 自己的业务场景跑 seata 分布式事务 demo。**真实业务**：用户调用一次 ReAct 智能体任务，跨 3 个微服务做"扣费 + 记日志 + 累计数"，任何一步失败整体回滚。

## 业务场景

```
User: "帮我想一句 2025 营销文案"
   │
   ▼
[OrderController /api/seata/order/success]   ← @GlobalTransactional
   │
   ├── 1. user-service.deduct()              ← userdb
   │      UPDATE user_credits SET credits = credits - 50 WHERE user_id = 1
   │
   ├── 2. agent-service.log()                ← agentdb
   │      INSERT INTO agent_invoke_log ...
   │
   ├── 3. stats-service.increment()          ← statsdb
   │      INSERT/UPDATE usage_stats ...
   │
   ▼
TM commit → TC 通知所有 RM 删 undo_log
   OR
TM throw  → TC 通知所有 RM 用 undo_log 还原（全局回滚）
```

## 模块结构

```
seata-demo/
├── pom.xml
├── src/main/java/com/aiplatform/seata/
│   ├── SeataDemoApplication.java       # 启动入口（注册 3 个 mapper 包）
│   ├── config/
│   │   ├── DataSourceConfig.java        # 3 个 DataSource（userDataSource / agentDataSource / statsDataSource）
│   │   └── DataSourceHelper.java        # H2 URL 工厂
│   ├── user/
│   │   ├── entity/UserCredits.java      # user_credits 表
│   │   ├── mapper/UserCreditsMapper.java
│   │   ├── service/UserService.java     # deduct()
│   │   └── controller/UserController.java
│   ├── agent/
│   │   ├── entity/AgentInvokeLog.java
│   │   ├── mapper/AgentInvokeLogMapper.java
│   │   ├── service/AgentService.java    # log()
│   │   └── controller/AgentController.java
│   ├── stats/
│   │   ├── entity/UsageStats.java
│   │   ├── mapper/UsageStatsMapper.java
│   │   ├── service/StatsService.java    # increment()
│   │   └── controller/StatsController.java
│   └── order/
│       ├── service/AgentInvokeService.java   # @GlobalTransactional 入口
│       └── controller/OrderController.java   # /success + /rollback
└── src/main/resources/
    ├── application.yml                   # 3 个 datasource URL（hard-code H2）+ seata config
    ├── user-init.sql
    ├── agent-init.sql
    └── stats-init.sql
```

## Seata 集成要点

### 1. 依赖（pom.xml）
```xml
<dependency>
    <groupId>io.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>io.seata</groupId>
    <artifactId>seata-all</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 2. application.yml
- `seata.enabled=true` 开启
- `seata.registry.type=nacos` — TC 注册中心（与业务服务同 nacos）
- `seata.tx-service-group=my_test_tx_group` — 业务侧事务组
- `seata.service.grouplist.default=127.0.0.1:8091` — TC 地址

### 3. 多数据源
**不**用 `spring.datasource.url`（auto-config 会自动建一个），**自己**注册 3 个 bean：
- `@Bean(name="userDataSource")` + `@Primary`
- `@Bean(name="agentDataSource")`
- `@Bean(name="statsDataSource")`

`seata-spring-boot-starter` 的 `SeataDataSourceAutoConfiguration` 会自动把它们都包成
`DataSourceProxy`（AT 模式 RM）— 你**不需要**自己写 `new DataSourceProxy(ds)`。

### 4. 全局事务入口
```java
@GlobalTransactional(name = "agent-invoke", rollbackFor = Exception.class)
public InvokeResult invokeSuccess(...) {
    userService.deduct(...);   // RM1
    agentService.log(...);     // RM2
    statsService.increment(...); // RM3
}
```

任意 RM 抛异常 → TM catch → TC 通知所有 RM 用 undo_log 还原。

### 5. undo_log 表
每个 datasource 都要有 `undo_log` 表（init SQL 已建）。seata 拦截 SQL 时把
"前镜像/后镜像"序列化写这表，回滚时反序列化还原。

## 运行

### 沙箱快速跑（无 TC，仅 mock 单元测试）
```bash
cd backend
mvn -pl seata-demo test
# → Tests run: 4 (AgentInvokeServiceTest, 编排逻辑 mock)
#            3 skipped (SeataIntegrationTest, @Disabled)
```

### 真实分布式事务（生产 / CI）
```bash
# 1. 起 nacos
docker run -d --name nacos -p 8848:8848 \
    -e MODE=standalone nacos/nacos-server:v2.3.1

# 2. 起 seata TC
docker run -d --name seata -p 8091:8091 \
    -e SEATA_IP=127.0.0.1 \
    seataio/seata-server:2.0.0

# 3. 启本服务（向 nacos 注册 seata-demo）
cd backend
NACOS_DISCOVERY_ENABLED=true NACOS_SERVER=127.0.0.1:8848 \
NACOS_USER=nacos NACOS_PASS=nacos \
java -jar seata-demo/target/seata-demo.jar

# 4. curl 验证正常路径
curl -X POST http://localhost:9100/api/seata/order/success \
  -H 'Content-Type: application/json' \
  -d '{"userId":1, "agentCode":"A-DEFAULT01", "prompt":"hi", "tokens":50}'

# 5. curl 验证回滚路径（最后一步会抛异常，全部回滚）
curl -X POST http://localhost:9100/api/seata/order/rollback \
  -H 'Content-Type: application/json' \
  -d '{"userId":2, "agentCode":"A-DEFAULT01", "prompt":"hello", "tokens":30}'
# → 500 IllegalStateException
# → 但 demo 用户的 credits 仍是 100（被回滚）

# 6. 查 nacos 确认注册
curl 'http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=20'
# → count: 1, doms: [seata-demo]
```

## 与项目其他模块的关系

- **依赖** ai-platform-common / common-core / common-web / nacos-starter
- **复用** ai-platform-common 的 `BusinessException` + `ResultCode` 做异常归一
- **单独部署**: 不依赖 user/agent/stats 业务服务（demo 自己内含 3 个逻辑服务），
  真实生产环境把 3 个 service 拆成独立 jar 后只改 application.yml 即可
