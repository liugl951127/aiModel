# Seata 分布式事务 — Windows + JDK 17 安装配置全流程

> 适用版本: **Seata 2.0.0** + **JDK 17** + **Nacos 2.3.2** + **MySQL 8.x** + **Spring Cloud Alibaba 2023.0.1.0**
> 预计耗时: 30-45 分钟 (含下载)
> 本项目已集成 Seata demo (`backend/seata-demo/`), 可直接对照验证.

---

## 目录

1. [前置环境](#1-前置环境)
2. [下载 Seata](#2-下载-seata)
3. [数据库准备](#3-数据库准备)
4. [Nacos 命名空间准备](#4-nacos-命名空间准备)
5. [Seata 配置](#5-seata-配置)
6. [启动 Seata Server](#6启动-seata-server)
7. [项目集成 Seata Client](#7-项目集成-seata-client)
8. [验证](#8-验证)
9. [常见问题](#9-常见问题)

---

## 1. 前置环境

确认以下已安装并运行:

| 组件 | 版本 | 检查命令 | 期望输出 |
|---|---|---|---|
| JDK | 17+ | `java -version` | `openjdk version "17"` |
| Maven | 3.8+ | `mvn -v` | `Apache Maven 3.8.x` |
| MySQL | 8.0+ | `mysql --version` | `Ver 8.0.x` |
| Nacos | 2.3+ | 浏览器访问 `http://localhost:8848/nacos` | 登录页 (nacos/nacos) |

### 1.1 JDK 17 安装 (若无)

```powershell
# 推荐使用 Microsoft OpenJDK 17
# 下载: https://learn.microsoft.com/zh-cn/java/openjdk/download

# 或用 winget
winget install Microsoft.OpenJDK.17

# 或用 Chocolatey
choco install temurin17

# 配置环境变量 (系统属性 → 高级 → 环境变量)
# JAVA_HOME = C:\Program Files\Microsoft\jdk-17.0.x
# Path 追加 %JAVA_HOME%\bin

# 验证
java -version
```

### 1.2 Nacos 启动 (若无)

```powershell
# 下载 Nacos 2.3.2
# https://github.com/alibaba/nacos/releases/download/2.3.2/nacos-server-2.3.2.zip

# 解压到 D:\nacos
cd D:\nacos\bin

# 单机模式启动 (Windows)
startup.cmd -m standalone

# 访问 http://localhost:8848/nacos (nacos/nacos)
```

---

## 2. 下载 Seata

### 2.1 官方下载

```powershell
# 创建工作目录
mkdir D:\seata
cd D:\seata

# 下载 Seata 2.0.0 (Windows 友好, 含 bin/startup.cmd)
# 推荐: https://github.com/seata/seata/releases/download/v2.0.0/seata-server-2.0.0.zip

# 浏览器下载或用 PowerShell
Invoke-WebRequest -Uri "https://github.com/seata/seata/releases/download/v2.0.0/seata-server-2.0.0.zip" -OutFile "D:\seata\seata-server-2.0.0.zip"

# 解压
Expand-Archive -Path "D:\seata\seata-server-2.0.0.zip" -DestinationPath "D:\seata\"
```

### 2.2 目录结构

解压后结构:

```
D:\seata\seata-server-2.0.0\
├── bin\
│   ├── seata-server.bat              # Windows 启动脚本
│   └── seata-server.sh               # Linux 启动脚本
├── conf\
│   ├── application.yml               # 主配置 (2.0+ 推荐)
│   ├── application.example.yml       # 配置示例
│   ├── logback-spring.xml            # 日志
│   ├── registry.conf                # 旧版注册中心 (2.0 兼容)
│   └── file.conf                     # 旧版文件配置
├── lib\                              # 依赖 jar
├── logs\                             # 日志输出
└── sessionStore\                     # 存储 (单机模式)
```

---

## 3. 数据库准备

Seata AT 模式需要在**每个业务库**建 `undo_log` 表 (用于回滚).

### 3.1 创建 Seata Server 自己的库

```sql
-- 连接到 MySQL
mysql -u root -p

-- 创建 seata 库
CREATE DATABASE IF NOT EXISTS seata DEFAULT CHARSET utf8mb4;

-- 切换
USE seata;
```

### 3.2 Seata Server 表结构 (2.0 版本)

Seata 2.0 自带 SQL 脚本, 在 conf 目录的 jdbc 目录 (需自己创建) 或 github:

```sql
-- branch_table (分支事务表)
CREATE TABLE IF NOT EXISTS `branch_table`
(
    `branch_id`         BIGINT       NOT NULL,
    `xid`               VARCHAR(128) NOT NULL,
    `transaction_id`    BIGINT,
    `resource_group_id` VARCHAR(32),
    `resource_id`       VARCHAR(256),
    `branch_type`       VARCHAR(8),
    `status`            TINYINT,
    `application_id`    VARCHAR(32),
    `transaction_service_group` VARCHAR(32),
    `client_id`         VARCHAR(64),
    `md5`              VARCHAR(128),
    `gmt_create`        DATETIME(6),
    `gmt_modified`      DATETIME(6),
    PRIMARY KEY (`branch_id`),
    KEY `idx_xid` (`xid`)
);

-- global_table (全局事务表)
CREATE TABLE IF NOT EXISTS `global_table`
(
    `xid`                       VARCHAR(128) NOT NULL,
    `transaction_id`            BIGINT,
    `status`                    TINYINT      NOT NULL,
    `application_id`            VARCHAR(32),
    `transaction_service_group` VARCHAR(32),
    `transaction_name`          VARCHAR(128),
    `timeout`                   INT,
    `begin_time`                BIGINT,
    `application_data`          VARCHAR(2000),
    `gmt_create`                DATETIME(6),
    `gmt_modified`              DATETIME(6),
    PRIMARY KEY (`xid`),
    KEY `idx_status_gmt_modified` (`status`, `gmt_modified`),
    KEY `idx_transaction_id` (`transaction_id`)
);

-- lock_table (分布式锁表)
CREATE TABLE IF NOT EXISTS `lock_table`
(
    `row_key`        VARCHAR(128) NOT NULL,
    `xid`            VARCHAR(96),
    `transaction_id` BIGINT,
    `branch_id`     BIGINT       NOT NULL,
    `resource_id`    VARCHAR(256),
    `table_name`     VARCHAR(32),
    `pk`            VARCHAR(36),
    `status`         TINYINT      NOT NULL DEFAULT '0' COMMENT '0:locked,1:rollbacking',
    `gmt_create`     DATETIME,
    `gmt_modified`   DATETIME,
    PRIMARY KEY (`row_key`),
    KEY `idx_status` (`status`),
    KEY `idx_branch_id` (`branch_id`),
    KEY `idx_xid` (`xid`)
);
```

### 3.3 业务库的 undo_log 表 (AT 模式必须)

**每个**业务库都要建:

```sql
-- 在 ai_platform 业务库执行
USE ai_platform;

CREATE TABLE IF NOT EXISTS `undo_log`
(
    `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
    `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
    `log_status`    INT(11)      NOT NULL COMMENT '0:normal status,1:defense status',
    `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
    `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`),
    KEY `ix_log_created` (`log_created`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COMMENT ='AT transaction mode undo table';
```

**本项目已包含**: `deploy/sql/01_schema.sql` 末尾已有该表.

---

## 4. Nacos 命名空间准备

### 4.1 创建命名空间

1. 访问 `http://localhost:8848/nacos`
2. 登录 (nacos/nacos)
3. 命名空间 → 新建:
   - 命名空间 ID: `seata` (自定义, 一会儿配置要用)
   - 命名空间名: `seata`
   - 描述: 分布式事务 Seata 配置中心
4. 复制**命名空间 ID** (会自动生成, 类似 `a1b2c3d4-...`)

### 4.2 上传 Seata 配置到 Nacos

下载 `config.txt` (Seata 官方):

```powershell
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/seata/seata/2.0.0/script/client/conf/config.txt" -OutFile "D:\seata\config.txt"
```

或者直接创建 `D:\seata\config.txt`, 内容示例:

```properties
# For details about configuration items, see https://seata.io/zh-cn/docs/user/configurations.html
# Transport configuration
transport.type=TCP
transport.server=NIO
transport.heartbeat=true
transport.thread-factory.worker-thread-prefix=NettyServerWorkerThread
transport.thread-factory.boss-thread-prefix=NettyServerBossThread
transport.thread-factory.client-selector-thread-prefix=NettyClientSelectorThread
transport.thread-factory.client-selector-thread-size=1
transport.thread-factory.client-worker-thread-prefix=NettyClientWorkerThread
transport.thread-factory.client-worker-thread-size=default
transport.shutdown.timeout=3000
transport.serialization=seata
transport.compressor=none

# Transaction routing rules configuration, only for the server
service.vgroupMapping.default_tx_group=default
service.default.grouplist=127.0.0.1:8091
service.enableDegrade=false
service.disableGlobalTransaction=false

# Transaction rule configuration, only for the server
client.rm.async-commit-buffer-limit=10000
client.rm.lock.retry-interval=10
client.rm.lock.retry-times=30
client.rm.lock.retry-policy-branch-execution-once=false
client.rm.report-retry-count=5
client.rm.table-meta-check-enable=true
client.rm.table-meta-checker-interval=60000
client.rm.sql-parser-type=druid
client.rm.report-success-enable=false
client.rm.saga-branch-register-limit=50
client.rm.tcc-action-intercept-order=-2147482648
client.rm.tcc-intercept-order=-2147482648
client.tm.commit-retry-count=5
client.tm.rollback-retry-count=5
client.tm.default-global-transaction-timeout=60000
client.tm.rollback-check-timeout=0
client.tm.message-driven=false
client.tm.enhance-schema-detection=true
client.tm.support-spring-data-starter=false

# Transaction rule configuration, only for the server
server.undo.log-save-days=7
server.undo.log-delete-period=86400000
server.undo.preserve.data=true
server.max-commit-retry-timeout=-1
server.max-rollback-retry-timeout=-1
server.recovery.committing-retry-period=1000
server.recovery.asyn-committing-retry-period=1000
server.recovery.rollback-retry-period=1000
server.recovery.timeout-retry-period=1000
server.recovery.rollbacking-retry-period=1000
server.recovery.sending-retry-period=1000
server.undo.log-delete-period=86400000
server.session.branch-async-queue-size=5000
server.session.global-async-queue-size=2000
server.session.timeout-retry-period=1000
server.session.timeout-retry-count=3

# Metrics configuration, only for the server
metrics.enabled=false
metrics.registry-type=compact
metrics.exporter-list=prometheus
metrics.exporter-prometheus-port=9898

# Store configuration, only for the server
store.mode=db
store.publicKey=
store.file.dir=file_store/data
store.file.max-branch-session-size=16384
store.file.max-global-session-size=512
store.file.file-write-buffer-cache-size=16384
store.file.flush-disk-mode=async
store.file.sessionReloadReadSize=100
store.db.datasource=druid
store.db.db-type=mysql
store.db.driver-class-name=com.mysql.cj.jdbc.Driver
store.db.url=jdbc:mysql://127.0.0.1:3306/seata?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
store.db.user=root
store.db.password=root
store.db.min-conn=5
store.db.max-conn=30
store.db.global-table=global_table
store.db.branch-table=branch_table
store.db.lock-table=lock_table
store.db.query-limit=100
store.db.max-wait=5000
```

### 4.3 用 seata-nacos.sh 脚本上传 (推荐)

下载 nacos-config.sh:

```powershell
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/seata/seata/2.0.0/script/client/conf/nacos-config.sh" -OutFile "D:\seata\nacos-config.sh"
```

**Windows 上需要 Git Bash 或 WSL 才能运行 sh**, 改用手动方式 (下一节) 或用 Python 脚本.

### 4.4 手动上传 (Windows 友好)

写个 Python 脚本 `upload_seata_nacos.py`:

```python
# D:\seata\upload_seata_nacos.py
import requests
import sys

NACOS = "http://127.0.0.1:8848"
NAMESPACE = "your-seata-namespace-id"  # 替换成 4.1 复制的 ID

def upload(key, value):
    url = f"{NACOS}/nacos/v1/cs/configs"
    params = {
        "dataId": key,
        "group": "SEATA_GROUP",
        "content": value,
        "tenant": NAMESPACE
    }
    r = requests.post(url, params=params)
    print(f"{key}: {r.text}")

# 读取 config.txt
with open("D:/seata/config.txt") as f:
    for line in f:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            k, v = line.split("=", 1)
            upload(k, v)
```

```powershell
pip install requests
python D:\seata\upload_seata_nacos.py
```

**验证**: 在 Nacos → 配置管理 → seata 命名空间, 应看到 `service.vgroupMapping.default_tx_group` 等几十个配置项.

---

## 5. Seata 配置

### 5.1 主配置 `application.yml`

`D:\seata\seata-server-2.0.0\conf\application.yml`:

```yaml
server:
  port: 7091
  servlet:
    session:
      timeout: 1800000

spring:
  application:
    name: seata-server
  main:
    allow-bean-definition-overriding: false
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: your-seata-namespace-id   # 4.1 的命名空间 ID
        group: SEATA_GROUP
        username: nacos
        password: nacos
      config:
        server-addr: 127.0.0.1:8848
        namespace: your-seata-namespace-id
        group: SEATA_GROUP
        username: nacos
        password: nacos

seata:
  config:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      namespace: your-seata-namespace-id
      group: SEATA_GROUP
      username: nacos
      password: nacos
  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: 127.0.0.1:8848
      namespace: your-seata-namespace-id
      group: SEATA_GROUP
      username: nacos
      password: nacos
      cluster: default
  store:
    mode: db
    db:
      datasource: druid
      db-type: mysql
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://127.0.0.1:3306/seata?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
      user: root
      password: root
      min-conn: 5
      max-conn: 30
      global-table: global_table
      branch-table: branch_table
      lock-table: lock_table
  server:
    port: 8091
    service-port: 8091
    max-commit-retry-timeout: -1
    max-rollback-retry-timeout: -1
  security:
    secretKey: SeataSecretKey123456789012345678901234567890123456789
    tokenValidityInMilliseconds: 1800000
    csrf:
      enable: false
  log:
    level:
      io:
        seata: info
```

### 5.2 关键配置说明

| 配置 | 说明 | 本项目值 |
|---|---|---|
| `seata.config.type` | 配置中心类型 | nacos |
| `seata.registry.type` | 注册中心类型 | nacos |
| `seata.store.mode` | 存储模式 (file/db/redis) | **db** (生产) |
| `seata.store.db.url` | Seata Server 自己的库 | `jdbc:mysql://127.0.0.1:3306/seata` |
| `seata.server.port` | Seata TCP 端口 | 8091 |
| `server.port` | Seata Web 控制台端口 | 7091 |

### 5.3 业务库配置 (重点!)

`store.db.url` 是 Seata Server 自己的存储库, 跟业务库无关. 业务库在**客户端**配.

---

## 6. 启动 Seata Server

### 6.1 Windows 启动

```powershell
cd D:\seata\seata-server-2.0.0\bin

# 直接启动 (前台)
seata-server.bat

# 后台启动 (PowerShell)
Start-Process -FilePath ".\seata-server.bat" -WindowStyle Hidden
```

### 6.2 JVM 调优 (可选, 生产建议)

编辑 `seata-server.bat`, 在 `java` 命令前加:

```bat
set JVM_XMS=2g
set JVM_XMX=4g
set JVM_XMN=1g
set JVM_MAX_DIRECT_MEMORY_SIZE=1g
```

### 6.3 启动成功标志

日志 (`logs/seata_xxxx.log`) 应包含:

```
2024-xx-xx INFO  [main] io.seata.server.ServerRunner - seata server started
2024-xx-xx INFO  [main] io.seata.core.rpc.netty.NettyRemotingServer - Server started, listen port: 8091
```

并 Nacos 服务列表出现 `seata-server`:

```
http://localhost:8848/nacos → 服务管理 → 服务列表
  → seata-server (1 个实例)
```

### 6.4 防火墙

如果 Nacos 跨机器, Windows 防火墙放行 8091 和 7091:

```powershell
New-NetFirewallRule -DisplayName "Seata 8091" -Direction Inbound -LocalPort 8091 -Protocol TCP -Action Allow
New-NetFirewallRule -DisplayName "Seata 7091" -Direction Inbound -LocalPort 7091 -Protocol TCP -Action Allow
```

---

## 7. 项目集成 Seata Client

### 7.1 父 pom 加依赖管理 (已在本项目)

```xml
<!-- backend/pom.xml -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.seata</groupId>
            <artifactId>seata-spring-boot-starter</artifactId>
            <version>2.0.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 7.2 业务模块引入

```xml
<!-- backend/seata-demo/pom.xml -->
<dependencies>
    <dependency>
        <groupId>io.seata</groupId>
        <artifactId>seata-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

### 7.3 application.yml (每个业务服务)

```yaml
seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: default_tx_group
  config:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      namespace: your-seata-namespace-id
      group: SEATA_GROUP
      username: nacos
      password: nacos
  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: 127.0.0.1:8848
      namespace: your-seata-namespace-id
      group: SEATA_GROUP
      username: nacos
      password: nacos
```

### 7.4 多数据源场景 (本项目重点)

参考 `seata-demo` 模块: 3 个 DataSource + 3 SqlSessionFactory + `@GlobalTransactional`.

**核心代码 `DataSourceConfig.java`**:

```java
@Configuration
public class DataSourceConfig {

    @Bean("orderDs")
    @ConfigurationProperties(prefix = "spring.datasource.order")
    public DataSource orderDataSource() {
        return new HikariDataSource();
    }

    @Bean("orderSf")
    public SqlSessionFactory orderSqlSessionFactory(@Qualifier("orderDs") DataSource ds) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        return factory.getObject();
    }

    @MapperScan(basePackages = "com.aiplatform.seata.order.mapper",
               sqlSessionFactoryRef = "orderSf")
    public interface OrderMapperScan {}
    // ... 类似 stock, account
}
```

**业务方法** (关键):

```java
@Service
public class AgentInvokeService {
    @Autowired private OrderService orderService;
    @Autowired private StockService stockService;
    @Autowired private AccountService accountService;

    /**
     * 二重注解: @GlobalTransactional (TC 在场) + @Transactional (本地事务兜底)
     */
    @GlobalTransactional(name = "seata-agent-tx", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public String agentOrder() {
        orderService.create();      // 写 order 库
        stockService.decrease();    // 写 stock 库
        accountService.debit();     // 写 account 库
        return "ok";
    }
}
```

### 7.5 Seata 数据源自动代理 (AT 模式)

每个 DataSource 都要被 Seata 代理. 有两种方式:

**方式 1: 显式代理** (本项目用这个, 因为多数据源):

```java
@Bean("orderDsProxy")
public DataSource orderDsProxy(@Qualifier("orderDs") DataSource ds) {
    return new DataSourceProxy(ds);  // Seata 提供的代理
}
```

**方式 2: Spring Boot 自动 (单数据源)**:

```yaml
seata:
  enable-auto-data-source-proxy: true
```

---

## 8. 验证

### 8.1 Seata Server 自检

```powershell
# 健康检查
curl http://127.0.0.1:7091/api/v1/health

# 应返回
{"code":200,"message":"success","data":{"status":"UP"}}
```

### 8.2 Nacos 注册检查

浏览器 `http://localhost:8848/nacos` → 服务管理 → 服务列表:
- `seata-server` (1 实例, 端口 8091)
- `ai-platform-auth`, `ai-platform-user` 等

### 8.3 跑本项目 seata-demo 测试

```powershell
cd D:\workspace\ai-agent-platform\backend

# 编译
mvn -T 2C -DskipTests -B install

# 启动 seata-demo (3 端口 9020/9021/9022)
java -jar seata-demo\target\seata-demo.jar --server.port=9020
java -jar seata-demo\target\seata-demo.jar --server.port=9021
java -jar seata-demo\target\seata-demo.jar --server.port=9022
```

### 8.4 13 个集成测试

本项目 `seata-demo` 内置 13 个测试, 覆盖:

| 场景 | 期望 |
|---|---|
| TC 在场: 正常 commit | ✓ 3 库都有数据 |
| TC 在场: 故意抛异常 rollback | ✓ 3 库都无数据 |
| TC 不在场: @Transactional 本地 | ✓ 单库事务回滚 |
| 跨服务 RPC + Seata 透传 XID | ✓ 全局事务生效 |
| 嵌套调用 | ✓ 子事务 join |
| 读未提交 | ✓ 隔离级别生效 |

```powershell
mvn -pl seata-demo test
# Tests run: 13, Failures: 0
```

### 8.5 验证 SQL 回滚

```sql
-- 故意抛异常
curl -X POST http://localhost:9020/api/order/create?amount=100\&fail=true

-- 查业务库 (应有 UNDID 数据, 不应提交)
SELECT * FROM order_tb;
-- 期望: 没有 amount=100 的记录 (回滚了)

-- 查 undo_log
SELECT * FROM undo_log ORDER BY log_created DESC LIMIT 5;
-- 期望: 有刚才那笔的记录 (回滚日志)
```

---

## 9. 常见问题

### Q1: `No available service`

**原因**: 客户端连不上 Seata TC, 或 namespace 配错.

```powershell
# 1) 检查 Seata 是否在 Nacos 注册
curl http://localhost:8848/nacos/v1/ns/instance/list?serviceName=seata-server

# 2) 客户端 namespace 必须跟 server 一致
# 3) 检查端口 8091 防火墙
netstat -an | findstr 8091
```

### Q2: `no available server to connect`

**原因**: 默认 1s 超时太短, 或 Seata 没启动.

```yaml
seata:
  client:
    rm:
      report-success-enable: false
    tm:
      commit-retry-count: 5
      rollback-retry-count: 5
```

### Q3: `Can't not find Cluster`

**原因**: `vgroupMapping` 没配或不对.

Nacos 应该有:
- `service.vgroupMapping.default_tx_group=default`

客户端 `tx-service-group: default_tx_group` 必须一致.

### Q4: 业务表没数据但 undo_log 有

**原因**: 二阶段提交失败, 但 TC 已回滚. 检查:
- MySQL `binlog_format=ROW` (必须)
- Seata Server 库的 `global_table/branch_table/lock_table` 状态

```sql
SELECT * FROM seata.global_table ORDER BY gmt_create DESC LIMIT 5;
SELECT * FROM seata.branch_table ORDER BY gmt_create DESC LIMIT 5;
```

### Q5: Nacos 连接 403

**原因**: 2.3+ 强制鉴权.

```yaml
seata:
  config:
    nacos:
      username: nacos
      password: nacos
  registry:
    nacos:
      username: nacos
      password: nacos
```

### Q6: Windows 启动后控制台乱码

```powershell
# 启动前设置编码
chcp 65001

# 或编辑 seata-server.bat 加
set JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8
```

### Q7: Spring Boot 3.x + Seata 兼容

Spring Boot 3.x 用了 Jakarta EE, 需 Seata 1.7+ (推荐 2.0). 旧版 Seata 1.5 会因 `javax.*` 报错.

### Q8: undo_log 表结构不对

报错 `Table 'xxx.undo_log' doesn't exist` 或 `Incorrect column spec`. 用本项目提供的:

```sql
-- backend/deploy/sql/01_schema.sql 末尾有标准脚本
```

### Q9: 想用 file 模式 (单机测试)

改 `application.yml`:

```yaml
seata:
  store:
    mode: file
    file:
      dir: sessionStore
```

不需要建 seata 库, 但生产禁用.

### Q10: 多 Seata Server (集群)

需要 2 个 Seata + 共享 Nacos + 共享 DB, 互相不感知, TC 自动选主:

```yaml
# 启动 2 个实例
seata-server.bat -p 8091
seata-server.bat -p 8092
```

---

## 10. 性能调优

### 10.1 JVM

```bat
# seata-server.bat
set JVM_XMS=4g
set JVM_XMX=8g
set JVM_XMN=2g
set JVM_GC=-XX:+UseG1GC -XX:MaxGCPauseMillis=200
set JVM_OOM=-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=D:\seata\logs
```

### 10.2 数据库

```sql
-- 给 undo_log 加索引 (高频查)
ALTER TABLE undo_log ADD INDEX idx_log_created (log_created);

-- 定期清理 (Seata 自动 7 天)
SET GLOBAL expire_logs_days = 7;
```

### 10.3 客户端

```yaml
seata:
  client:
    rm:
      async-commit-buffer-limit: 5000
      report-retry-count: 3
    tm:
      commit-retry-count: 3
      rollback-retry-count: 3
      default-global-transaction-timeout: 60000
```

---

## 11. 监控 (可选)

### 11.1 Prometheus + Grafana

`seata-server-2.0.0` 自带 metrics 端点 `:9898/metrics`.

`prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'seata'
    static_configs:
      - targets: ['localhost:9898']
```

Grafana 导入 Seata 官方 Dashboard (id: 10807).

### 11.2 Seata 控制台

`http://localhost:7091` (默认账户 seata/seata):

- 事务列表: 看全局事务状态
- 分支事务: 看每个分支的提交/回滚
- 锁管理: 看锁竞争
- Session 管理: 清理僵尸

---

## 12. 一键脚本 (PowerShell)

保存为 `D:\seata\setup.ps1`, 一键初始化:

```powershell
# 检查 Java
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "未找到 Java, 请先安装 JDK 17"
    exit 1
}

# 检查 MySQL
try {
    mysql -u root -e "SELECT 1" 2>&1 | Out-Null
} catch {
    Write-Error "MySQL 未运行"
    exit 1
}

# 创建库
Write-Host "[1/4] 创建 seata 库..."
Get-Content "D:\seata\seata-server-2.0.0\conf\seata_server.sql" | mysql -u root seata

# 提示
Write-Host "[2/4] Seata Server 库已建"
Write-Host "[3/4] 请修改 conf\application.yml 的 namespace 和密码"
Write-Host "[4/4] 启动: D:\seata\seata-server-2.0.0\bin\seata-server.bat"

# 启动
Start-Process -FilePath "D:\seata\seata-server-2.0.0\bin\seata-server.bat" -WindowStyle Normal
Write-Host "Seata 已启动, http://localhost:7091"
```

---

## 13. 总结

| 阶段 | 关键操作 | 时间 |
|---|---|---|
| 1. 准备 | JDK 17 + MySQL + Nacos | 已就绪 |
| 2. 下载 | Seata 2.0.0 zip | 5 min |
| 3. 数据库 | 建 seata 库 + 3 表 + 业务库 undo_log | 5 min |
| 4. Nacos | 建命名空间 + 上传 config.txt | 10 min |
| 5. 配置 | 改 application.yml | 5 min |
| 6. 启动 | 跑 seata-server.bat | 1 min |
| 7. 验证 | curl health + 注册检查 | 1 min |
| 8. 集成 | 业务代码 @GlobalTransactional | 15 min |
| **合计** | - | **~45 min** |

---

## 14. 参考链接

- Seata 官方: <https://seata.io/zh-cn/>
- 2.0 文档: <https://seata.apache.org/zh-cn/docs/overview/what-is-seata>
- 本项目 demo: `backend/seata-demo/`
- 本项目配置: `backend/ai-platform-*/application.yml` + `seata-demo/`
- Nacos: <https://nacos.io/zh-cn/>
- Spring Cloud Alibaba: <https://sca.aliyun.com/>
