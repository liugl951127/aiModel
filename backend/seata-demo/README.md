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

## 三层事务保障（优雅降级）

| 模式 | 触发条件 | 行为 |
| --- | --- | --- |
| **seata AT 模式** | 启了 seata TC (8091) | `@GlobalTransactional` 协调，3 个 RM 写 undo_log |
| **Spring 本地事务 + @Transactional** | 沙箱无 TC（dev 默认） | user 走 user datasource 本地事务，agent/stats 各自 datasource 独立事务 |
| **业务补偿** | agent/stats 失败时 | 业务侧重试 / 异步对账（详见"无 TC 时的语义"章节） |

> **关键不变量**：user 模块（扣费）永远在事务保护下 — 即便所有分布式机制都失效，credits 也不会被错误扣减。agent/stats 失败时只能"已记但扣了费"，需要补偿。

## 模块结构

```
seata-demo/
├── pom.xml
├── src/main/java/com/aiplatform/seata/
│   ├── SeataDemoApplication.java       # 启动入口
│   ├── config/
│   │   ├── DataSourceConfig.java        # 3 个 DataSource + SqlSessionFactory
│   │   └── DataSourceHelper.java        # H2 URL 工厂
│   ├── user/
│   │   ├── entity/UserCredits.java
│   │   ├── mapper/UserCreditsMapper.java
│   │   ├── service/UserService.java     # deduct() — @Transactional 本地事务保护
│   │   └── controller/UserController.java
│   ├── agent/
│   │   ├── entity/AgentInvokeLog.java
│   │   ├── mapper/AgentInvokeLogMapper.java
│   │   ├── service/AgentService.java    # log() — @Transactional
│   │   └── controller/AgentController.java
│   ├── stats/
│   │   ├── entity/UsageStats.java
│   │   ├── mapper/UsageStatsMapper.java
│   │   ├── service/StatsService.java    # increment() — @Transactional
│   │   └── controller/StatsController.java
│   └── order/
│       ├── service/AgentInvokeService.java   # @GlobalTransactional 入口
│       └── controller/OrderController.java
└── src/main/resources/
    ├── application.yml
    ├── user-init.sql
    ├── agent-init.sql
    └── stats-init.sql
```

## 测试矩阵

| 测试类 | 测试数 | 覆盖 | 状态 |
| --- | --- | --- | --- |
| `AgentInvokeServiceTest` (mock 单元) | 4 | 编排逻辑：调用顺序、异常短路、参数传递 | ✅ 4/4 |
| `AgentInvokeServiceIntegrationTest` (Spring 集成) | 4 | 真实 3 DS + 事务：success / rollback / 余额不足 / 多次累加 | ✅ 4/4 |
| `DataSourceIsolationTest` (Spring 集成) | 5 | 3 DS 物理隔离：bean 不同、跨表查询、mapper 绑定 | ✅ 5/5 |
| **总计** | **13** | | **✅ 13/13** |

跑测试：
```bash
cd backend
mvn -pl seata-demo test
# → Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
```

## Seata 集成要点

### 1. 依赖
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
```yaml
seata:
  enabled: ${SEATA_ENABLED:true}
  application-id: seata-demo
  tx-service-group: my_test_tx_group
  registry:
    type: nacos
    nacos:
      server-addr: ${NACOS_SERVER:127.0.0.1:8848}
      username: ${NACOS_USER:nacos}
      password: ${NACOS_PASS:nacos}
      application: seata-server
  service:
    vgroup-mapping:
      my_test_tx_group: default
    grouplist:
      default: 127.0.0.1:8091
```

### 3. 多数据源
**3 个独立 DataSource + 独立 SqlSessionFactory + 独立 mapper 路径**：

```java
@Bean(name = "userDataSource") public DataSource userDataSource() { ... }
@Bean(name = "agentDataSource") public DataSource agentDataSource() { ... }
@Bean(name = "statsDataSource") public DataSource statsDataSource() { ... }

@MapperScan(basePackages = "com.aiplatform.seata.user.mapper",  sqlSessionFactoryRef = "userSqlSessionFactory")
@MapperScan(basePackages = "com.aiplatform.seata.agent.mapper", sqlSessionFactoryRef = "agentSqlSessionFactory")
@MapperScan(basePackages = "com.aiplatform.seata.stats.mapper", sqlSessionFactoryRef = "statsSqlSessionFactory")
```

`seata-spring-boot-starter` 的 `SeataDataSourceAutoConfiguration` 会自动
把任何 `DataSource` bean 包成 `DataSourceProxy`（前提是 `seata.enabled=true`）。

### 4. 全局事务入口
```java
@GlobalTransactional(name = "agent-invoke", rollbackFor = Exception.class)
@Transactional(rollbackFor = Exception.class)  // 无 TC 时的兜底
public InvokeResult invokeSuccess(...) {
    userService.deduct(...);     // RM1
    agentService.log(...);       // RM2
    statsService.increment(...); // RM3
}
```

### 5. undo_log 表
每个 datasource 都要有 `undo_log` 表（init SQL 已建）— seata 拦截 SQL
时把"前/后镜像"序列化写这表，回滚时反序列化还原。

## 无 TC 时的语义（沙箱 / dev）

`seata.enabled=false` 时：
- `@GlobalTransactional` 失效（seata 不接管）
- `@Transactional` 由 spring 接管，绑在 `@Primary` 的 userDataSource 上
- **结果**:
  - user.deduct 加入 outer 事务，outer 抛异常 → user 还原 ✓
  - agent.log 走 agentDataSource 独立事务，已经 commit（不回滚）
  - stats.increment 走 statsDataSource 独立事务，已经 commit（不回滚）

**生产**（有 TC）三者全部纳入全局事务，全部回滚。
**dev/沙箱**（无 TC）只 user 严格回滚 — agent/stats 失败需业务补偿。

## 运行

### 沙箱快速跑（13 个测试全过 + jar 启动）
```bash
cd backend
mvn -pl seata-demo test                                    # 13/13 PASS
mvn -pl seata-demo install -DskipTests                     # 打包
java -jar seata-demo/target/seata-demo.jar --server.port=9100   # 启动
curl http://localhost:9100/api/seata/user/1                 # → 10000 credits
```

### 真实分布式事务（生产 / CI）
```bash
# 1. 起 nacos
docker run -d --name nacos -p 8848:8848 \
    -e MODE=standalone nacos/nacos-server:v2.3.1

# 2. 起 seata TC（seata-server:2.0.0 默认 8091）
docker run -d --name seata -p 8091:8091 \
    -e SEATA_IP=127.0.0.1 \
    seataio/seata-server:2.0.0

# 3. 启本服务（向 nacos 注册 seata-demo）
NACOS_DISCOVERY_ENABLED=true NACOS_SERVER=127.0.0.1:8848 \
NACOS_USER=nacos NACOS_PASS=nacos \
java -jar seata-demo/target/seata-demo.jar

# 4. curl 验证正常路径
curl -X POST http://localhost:9100/api/seata/order/success \
  -H 'Content-Type: application/json' \
  -d '{"userId":1, "agentCode":"A-DEFAULT01", "prompt":"hi", "tokens":50}'
# → 200 OK, credits -= 50

# 5. curl 验证回滚路径（最后一步抛异常，全部回滚）
curl -X POST http://localhost:9100/api/seata/order/rollback \
  -H 'Content-Type: application/json' \
  -d '{"userId":2, "agentCode":"A-DEFAULT01", "prompt":"hello", "tokens":30}'
# → 500 IllegalStateException
# → 但 demo 用户的 credits 仍是 10000（被回滚）

# 6. 查 nacos 确认注册
curl 'http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=20'
# → count: N, doms: [..., seata-demo]
```

## 与项目其他模块的关系

- **依赖** ai-platform-common / common-core / common-web / nacos-starter
- **复用** ai-platform-common 的 `BusinessException` + `ResultCode` 做异常归一
- **不依赖** user/agent/stats 业务服务 — demo 自己内含 3 个逻辑服务
- **生产拆分**: 把 3 个 service 拆成独立 jar 后只改 application.yml（3 个 datasource URL）即可
