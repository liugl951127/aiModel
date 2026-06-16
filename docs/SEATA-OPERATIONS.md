# Seata 分布式事务 — 深度操作手册

> 涵盖 Seata 2.0 全量运行机制: 架构、4 种事务模式、AT/TCC/Saga 详解、锁机制、隔离级别、故障恢复、性能优化.
> 适合需要深入理解 Seata 内部机制, 或生产环境调优、故障排查的工程师.

---

## 目录

1. [架构总览](#1-架构总览)
2. [角色: TC / TM / RM](#2-角色-tc--tm--rm)
3. [4 种事务模式对比](#3-4-种事务模式对比)
4. [AT 模式详解 (核心)](#4-at-模式详解-核心)
5. [TCC 模式](#5-tcc-模式)
6. [Saga 模式](#6-saga-模式)
7. [XA 模式](#7-xa-模式)
8. [锁机制](#8-锁机制)
9. [隔离级别](#9-隔离级别)
10. [事务生命周期](#10-事务生命周期)
11. [故障恢复](#11-故障恢复)
12. [配置矩阵](#12-配置矩阵)
13. [性能调优](#13-性能调优)
14. [常见故障排查](#14-常见故障排查)
15. [与 Spring Cloud Alibaba 集成细节](#15-与-spring-cloud-alibaba-集成细节)

---

## 1. 架构总览

```
┌──────────────────────────────────────────────────────────────────┐
│                       应用集群 (N 个服务)                        │
│                                                                  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │  Service A  │    │  Service B  │    │  Service C  │         │
│  │ ┌─────────┐ │    │ ┌─────────┐ │    │ ┌─────────┐ │         │
│  │ │   TM    │ │    │ │   TM    │ │    │ │   TM    │ │  事务管理器 │
│  │ │(发起方)  │ │    │ │(参与方)  │ │    │ │(参与方)  │ │  发起/结束 │
│  │ └────┬────┘ │    │ └────┬────┘ │    │ └────┬────┘ │  全局事务  │
│  │      │       │    │      │       │    │      │       │         │
│  │ ┌────▼────┐ │    │ ┌────▼────┐ │    │ ┌────▼────┐ │         │
│  │ │   RM    │ │    │ │   RM    │ │    │ │   RM    │ │  资源管理器 │
│  │ │(代理DS) │ │    │ │(代理DS) │ │    │ │(代理DS) │ │  操作本地DB│
│  │ └────┬────┘ │    │ └────┬────┘ │    │ └────┬────┘ │         │
│  └──────┼───────┘    └──────┼───────┘    └──────┼───────┘         │
│         │                  │                  │                   │
│         └──────────────────┼──────────────────┘                   │
│                            │                                      │
└────────────────────────────┼──────────────────────────────────────┘
                             │ Netty (8091)
                             ▼
                ┌────────────────────────┐
                │   Seata TC (Server)    │
                │  Transaction Coordinator│
                │  ┌──────────────────┐  │
                │  │ GlobalSessionMgr │  │  全局事务
                │  │ BranchSessionMgr │  │  分支事务
                │  │ LockManager      │  │  全局锁
                │  │ SessionRecover   │  │  故障恢复
                │  └────────┬─────────┘  │
                │           │             │
                │  ┌────────▼─────────┐  │
                │  │  Store (db/file)  │  │  global/branch/lock_table
                │  └──────────────────┘  │
                └────────────┬───────────┘
                             │
                             ▼
                       ┌──────────┐
                       │   DB     │  ← global_table / branch_table / lock_table
                       └──────────┘
```

### 1.1 一句话总结

- **TM** (Transaction Manager) 负责定义全局事务的边界 (Begin / Commit / Rollback)
- **RM** (Resource Manager) 负责分支事务的注册、提交、回滚
- **TC** (Transaction Coordinator) 负责协调, 维护全局/分支事务状态, 推进二阶段

---

## 2. 角色: TC / TM / RM

### 2.1 TC (Transaction Coordinator)

**部署形态**: 独立 Server 进程, 端口 8091 (Netty) + 7091 (Web Console).

**核心职责**:

| 模块 | 职责 |
|---|---|
| GlobalSessionManager | 管理所有 GlobalSession, 默认上限 2 万 |
| BranchSessionManager | 管理所有 BranchSession, 默认上限 5 万 |
| LockManager | 全局锁, 防脏写/脏读 |
| SessionRecover | 定时扫描超时未结束的事务, 重试或回滚 |
| Coordinator | 二阶段驱动: 询问所有 RM 是否可提交, 全 yes 才 commit |
| MetricsExporter | 指标导出 (Prometheus 9898 端口) |

**存储**: 默认 db 模式, 3 张表:
- `global_table`: 全局事务状态 (XID, status, applicationId, ...)
- `branch_table`: 分支事务 (XID, branchId, resourceId, status, ...)
- `lock_table`: 全局锁 (row_key, xid, branch_id, status)

### 2.2 TM (Transaction Manager)

**部署形态**: 业务进程内嵌 (通过 `seata-spring-boot-starter`).

**核心 API**:

```java
// 全局事务发起方
@GlobalTransactional(name = "my-tx", rollbackFor = Exception.class,
                    propagation = Propagation.REQUIRED, timeoutMills = 60000)
public String doBusiness() {
    // 调用其它微服务 (RM 注册分支到 TC)
    orderService.create();
    stockService.decrease();
    return "ok";
}
```

**底层实现**: `GlobalTransactionalInterceptor` (AOP) 包装方法, 流程:

```
1. 生成 XID (雪花算法)
2. 注入 XID 到 RootContext (ThreadLocal)
3. 向 TC 发 Begin, 拿到 GlobalSession
4. 执行业务方法
   - 内部 RPC 调用通过 SeataFilter 把 XID 透传到下游
   - 下游 RM 注册分支到 TC
5. 方法成功: 向 TC 发 Commit (异步, 二阶段)
6. 方法失败: 向 TC 发 Rollback (异步, 二阶段)
7. 清空 RootContext
```

### 2.3 RM (Resource Manager)

**部署形态**: 业务进程内嵌, 代理业务数据源.

**代理 DataSource**:

```java
// Seata 1.x 用 DataSourceProxy
// Seata 2.x 用 seata-datasource 自动配置
@Bean
public DataSource dataSource() {
    HikariDataSource ds = new HikariDataSource();
    // ... 配置 ...
    return new DataSourceProxy(ds);  // Seata 包装
}
```

**核心职责**:

| 操作 | 流程 |
|---|---|
| 注册分支 (Branch Register) | 本地 SQL 执行前后, 向 TC 注册 BranchSession, 拿到 branchId 和 row keys |
| 上报状态 (Branch Report) | 本地事务结束 (commit/rollback) 后, 把状态上报 TC |
| undo_log 写入 | AT 模式特有, 解析 SQL 生成 before/after image, 写到 `undo_log` 表 |
| 全局锁 | UPDATE/INSERT 时尝试拿全局锁, 防止脏写 |

---

## 3. 4 种事务模式对比

| 维度 | AT | TCC | Saga | XA |
|---|---|---|---|---|
| 一致性 | 弱 (最终一致) | 强 | 弱 (补偿) | 强 |
| 性能 | ★★★★★ | ★★★★ | ★★★★★ | ★★ |
| 侵入性 | 零 (自动) | 高 (需 3 方法) | 中 (需正向+补偿) | 零 |
| 隔离 | 全局锁 | 业务自己 | 无 | DB 锁 |
| 回滚 | 自动 (undo_log) | 业务 Cancel | 业务补偿 | DB 自动 |
| 适合 | 常规 CRUD | 高性能核心 | 长事务/跨服务 | 强一致但慢 |
| 本项目 | ✅ 默认 | - | - | - |

---

## 4. AT 模式详解 (核心)

AT (Automatic Transaction) 是 Seata 默认模式, **对业务零侵入**, 适合 90% 场景.

### 4.1 核心机制: 二阶段 + before/after image

**关键概念**: Seata 通过解析 SQL, 自动生成"前镜像"和"后镜像", 存到 `undo_log` 表, 用于回滚.

### 4.2 完整流程

#### 阶段 1: 加载 (Load)

```
┌─────────┐                                ┌─────────┐
│ 业务调用 │  @GlobalTransactional          │   TC    │
│ (TM)    │ ──── Begin (XID) ────────────▶ │         │
└────┬────┘                                └─────────┘
     │  注入 XID 到 RootContext (ThreadLocal)
     │
     ▼
┌──────────────┐                           ┌─────────┐
│  业务 SQL    │                           │   TC    │
│  UPDATE ...  │ ──── Branch Register ───▶ │         │
│              │  (resourceId, lockKeys)   │         │
└──────┬───────┘                           └─────────┘
       │
       ▼  ┌──────────────────────────────────────────┐
          │  1. 解析 SQL (Druid)                      │
          │  2. 查询前镜像 (SELECT ... FOR UPDATE)   │  ← before image
          │  3. 执行原 SQL                            │
          │  4. 查询后镜像 (SELECT ... )              │  ← after image
          │  5. 写 undo_log (before + after)         │
          │  6. 申请全局锁 (基于主键)                 │
          │  7. 本地事务 COMMIT                       │
          └──────────────────────────────────────────┘
```

**undo_log 行内容** (示例):

```json
{
  "branchId": 1,
  "xid": "192.168.1.1:8091:123456",
  "beforeImage": {
    "rows": [
      {"id": 100, "amount": 1000, "name": "张三"}
    ],
    "tableName": "account"
  },
  "afterImage": {
    "rows": [
      {"id": 100, "amount": 900, "name": "张三"}
    ],
    "tableName": "account"
  }
}
```

#### 阶段 2a: 全局提交 (Commit)

```
┌─────────┐                                ┌─────────┐
│ 业务方法 │  成功                          │   TC    │
│ return  │ ──── Global Commit ──────────▶ │         │
└─────────┘                                └────┬────┘
                                                │
                                                ▼ 异步
                                       ┌────────────────┐
                                       │ 1. 通知所有 RM │
                                       │ 2. RM 删      │
                                       │    undo_log   │
                                       │ 3. 释放全局锁 │
                                       └────────────────┘
```

**为什么是异步**: AT 的"二阶段"实际上只删 undo_log, 不需要再写库, 性能极高.

#### 阶段 2b: 全局回滚 (Rollback)

```
┌─────────┐                                ┌─────────┐
│ 业务方法 │  抛异常                        │   TC    │
│ throw   │ ──── Global Rollback ────────▶ │         │
└─────────┘                                └────┬────┘
                                                │
                                                ▼ 异步
                                       ┌──────────────────┐
                                       │ 1. 通知所有 RM   │
                                       │ 2. RM 读         │
                                       │    undo_log      │
                                       │ 3. 校验脏写      │  ← 关键!
                                       │ 4. 反向 SQL 补偿  │
                                       │ 5. 删 undo_log   │
                                       │ 6. 释放全局锁    │
                                       └──────────────────┘
```

### 4.3 脏写校验 (Dirty Write Check)

**回滚前必须校验**, 防止本地 SQL 提交后, 又被其他事务改了:

```java
// 简化伪代码
public void branchRollback(BranchSession branch) {
    // 1. 读当前数据
    CurrentRow current = SELECT * FROM account WHERE id = 100;
    // 2. 对比 afterImage
    if (!current.equals(afterImage)) {
        // 脏写! 抛异常, 需要人工介入
        throw new BranchTransactionException("Dirty write detected");
    }
    // 3. 写回 beforeImage
    UPDATE account SET amount = beforeImage.amount WHERE id = 100;
    // 4. 删 undo_log
    DELETE FROM undo_log WHERE branch_id = branch.id;
    // 5. 释放全局锁
    LockManager.unlock(branch.lockKeys);
}
```

**业务影响**: 如果发现脏写, Seata 会停止回滚并记录告警, 需人工处理 (这是 AT 模式的"弱一致性"体现).

### 4.4 SQL 类型支持

| SQL 类型 | AT 支持 | 备注 |
|---|---|---|
| INSERT | ✅ | 主键必填 |
| UPDATE | ✅ | 解析 SET 子句 |
| DELETE | ✅ | 解析 WHERE |
| SELECT | ✅ | 无影响, 不注册分支 |
| TRUNCATE | ❌ | 不支持 |
| ALTER | ❌ | 不支持 |
| 批量 INSERT | ✅ | 解析每行 |
| 复杂 UPDATE (多表) | ⚠️ | 仅支持单表 |
| 嵌套子查询 | ⚠️ | 部分支持 |

### 4.5 undo_log 表结构 (必须)

```sql
CREATE TABLE `undo_log` (
    `branch_id`     BIGINT       NOT NULL,
    `xid`           VARCHAR(128) NOT NULL,
    `context`       VARCHAR(128) NOT NULL,    -- 序列化类型标记
    `rollback_info` LONGBLOB     NOT NULL,    -- before/after image JSON
    `log_status`    INT          NOT NULL,    -- 0=normal, 1=defense
    `log_created`   DATETIME(6)  NOT NULL,
    `log_modified`  DATETIME(6)  NOT NULL,
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`),
    KEY `ix_log_created` (`log_created`)
);
```

**每张业务表都需要**这张表, Seata 通过该表定位要回滚的数据.

### 4.6 AT 模式代码示例

```java
@Service
public class OrderService {
    @Autowired
    private OrderMapper orderMapper;

    // 不需要任何注解! Seata 自动接管
    public void create(Order order) {
        orderMapper.insert(order);
    }
}

@Service
public class AgentInvokeService {
    @Autowired
    private OrderService orderService;
    @Autowired
    private StockService stockService;

    // 关键: 发起方加 @GlobalTransactional
    @GlobalTransactional(name = "seata-tx", rollbackFor = Exception.class)
    public String doOrder() {
        orderService.create(new Order(...));
        stockService.decrease(1L, 10);  // 跨服务调用
        return "ok";
    }
}
```

---

## 5. TCC 模式

TCC (Try-Confirm-Cancel) 是**业务侵入式**的强一致方案.

### 5.1 三阶段

| 阶段 | 业务实现 | 例子 (扣库存) |
|---|---|---|
| **Try** | 资源预留 (不真正扣) | 冻结库存 10 |
| **Confirm** | 真正执行业务 | 把冻结的 10 扣掉 |
| **Cancel** | 释放 Try 预留的资源 | 解冻 10 |

### 5.2 代码示例

```java
// 1. 定义本地 TCC Bean
@LocalTCC
public interface StockTccAction {
    // Try: 冻结库存
    @TwoPhaseBusinessAction(name = "stockTry", commitMethod = "confirm", rollbackMethod = "cancel")
    boolean tryDecrease(@BusinessActionContextParameter(paramName = "itemId") Long itemId,
                       @BusinessActionContextParameter(paramName = "count") int count);

    // Confirm: 真正扣减
    boolean confirm(BusinessActionContext context);

    // Cancel: 解冻
    boolean cancel(BusinessActionContext context);
}

// 2. 实现
@Service
public class StockTccActionImpl implements StockTccAction {

    @Autowired private StockMapper stockMapper;
    @Autowired private FreezeMapper freezeMapper;

    public boolean tryDecrease(Long itemId, int count) {
        // 1. 冻结 (扣减 freeze_count, 增加 used_count)
        Stock stock = stockMapper.selectById(itemId);
        if (stock.getAvailable() < count) return false;
        stock.setFreeze(stock.getFreeze() + count);
        stockMapper.updateById(stock);
        // 2. 记录冻结明细
        freezeMapper.insert(new Freeze(itemId, count, context.getXid()));
        return true;
    }

    public boolean confirm(BusinessActionContext ctx) {
        Long itemId = (Long) ctx.getActionContext("itemId");
        int count = (int) ctx.getActionContext("count");
        // 把冻结转为已用
        Stock stock = stockMapper.selectById(itemId);
        stock.setFreeze(stock.getFreeze() - count);
        stock.setUsed(stock.getUsed() + count);
        stockMapper.updateById(stock);
        // 删冻结记录
        freezeMapper.deleteByXid(ctx.getXid());
        return true;
    }

    public boolean cancel(BusinessActionContext ctx) {
        Long itemId = (Long) ctx.getActionContext("itemId");
        int count = (int) ctx.getActionContext("count");
        // 解冻
        Stock stock = stockMapper.selectById(itemId);
        stock.setFreeze(stock.getFreeze() - count);
        stockMapper.updateById(stock);
        freezeMapper.deleteByXid(ctx.getXid());
        return true;
    }
}

// 3. 发起
@GlobalTransactional
public void order() {
    stockTccAction.tryDecrease(itemId, 10);
    // ... 其它 TCC
}
```

### 5.3 TCC 关键问题

**幂等**: Confirm/Cancel 可能被重试 (网络抖动), 必须**幂等**:
- 用 `xid + branch_id` 作唯一键
- 状态机: INIT → CONFIRMED / CANCELED

**悬挂**: Try 比 Cancel 晚到达 (Try 网络慢, Cancel 已发完). 解决: Cancel 时检查是否已 Try, 没有就空操作.

**空回滚**: Try 没收到 (没真正执行), Cancel 来了. 解决: 在 Cancel 业务里判断"有没有冻结记录", 没有则跳过.

---

## 6. Saga 模式

**长事务/跨服务**, 用正向 + 补偿流程.

### 6.1 状态机定义 (JSON)

```json
{
  "Name": "OrderSaga",
  "Steps": [
    {
      "Name": "CreateOrder",
      "Service": "orderService",
      "Method": "create",
      "Compensate": {
        "Service": "orderService",
        "Method": "deleteByOrderId"
      }
    },
    {
      "Name": "DecreaseStock",
      "Service": "stockService",
      "Method": "decrease",
      "Compensate": {
        "Service": "stockService",
        "Method": "increase"
      }
    }
  ]
}
```

### 6.2 特点

- 适合**长事务** (分钟级)
- 适合**跨多服务、跨多公司**
- 写隔离: 不支持 (业务自己保证)
- 终态一致性, 补偿可能失败需要重试

---

## 7. XA 模式

基于数据库 XA 协议 (2PC), Seata 2.0 引入.

### 7.1 机制

```
Prepare 阶段: RM 调 XA prepare, DB 锁资源, 但未提交
Commit 阶段: 所有 RM OK 后, XA commit
```

### 7.2 与 AT 对比

| 维度 | AT | XA |
|---|---|---|
| 一致性 | 弱 (脏写校验失败时) | 强 |
| 锁粒度 | 行 (无锁) | 整表行锁 (Prepare 期间) |
| 性能 | 高 | 低 |
| DB 要求 | 任意 | 必须支持 XA (InnoDB OK) |

**何时用 XA**: 银行/证券等强一致场景, 不能容忍最终一致.

### 7.3 配置

```yaml
seata:
  data-source-proxy-mode: XA
```

---

## 8. 锁机制

### 8.1 全局锁 (Global Lock)

Seata 维护的分布式锁, 存在 `lock_table`.

**作用**: 防止 AT 模式下的脏写.

**获取时机**: RM 写 SQL 时 (INSERT/UPDATE/DELETE).

**释放时机**: 全局事务结束 (commit/rollback).

**存储**:

```sql
CREATE TABLE lock_table (
    row_key VARCHAR(128),    -- 格式: "table_name:pk1,pk2"
    xid VARCHAR(96),
    transaction_id BIGINT,
    branch_id BIGINT,
    status TINYINT,          -- 0=locked
    PRIMARY KEY (row_key)
);
```

**例子**:

```sql
-- 业务: UPDATE account SET amount = 900 WHERE id = 100
-- Seata: INSERT INTO lock_table (row_key, xid, branch_id) 
--        VALUES ('account:100', '192.168.1.1:8091:12345', 1)
--        ON DUPLICATE KEY UPDATE branch_id = 1  -- 同一个 XID 不阻塞
```

### 8.2 本地锁 (Local Lock)

AT 模式**读不加全局锁**, 只在写时加. 但**读未提交**通过本地事务隔离级别保证:

```sql
-- 默认 AT 读隔离级别: Read Uncommitted
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
```

### 8.3 锁等待与超时

```yaml
seata:
  client:
    rm:
      lock:
        retry-interval: 10       # 锁等待重试间隔 (ms)
        retry-times: 30          # 锁等待重试次数
        retry-policy-branch-execution-once: false  # 重试到其他机器
```

**锁获取失败**:
- 重试 30 次 × 10ms = 300ms 后仍失败
- 抛 `LockConflictException`
- 业务可以选择重试整个事务

### 8.4 全局锁 vs 业务唯一索引

**建议**: 关键业务字段 (如订单号) 加**唯一索引**, Seata 全局锁只是兜底.

```sql
ALTER TABLE order_tb ADD UNIQUE INDEX uk_order_no (order_no);
```

---

## 9. 隔离级别

### 9.1 AT 模式默认隔离

**写隔离**: 全局锁 + 本地事务, 等价于 **Read Committed**.
**读隔离**: 本地 `Read Uncommitted`, 能看到**未提交的 Seata 分支事务**.

### 9.2 脏读场景与防护

**问题**: 全局事务 T1 还没提交, 另一个事务 T2 (非 Seata) 读到了中间状态.

**示例**:

```
T1 (Seata): UPDATE account SET amount = 900 WHERE id = 100   -- before=1000, after=900
T2 (非Seata): SELECT amount FROM account WHERE id = 100       -- 读到 900 (脏)
T1 rollback: UPDATE account SET amount = 1000 WHERE id = 100  -- 回滚
```

**解决方案** (按强度排序):

| 方案 | 强度 | 性能影响 |
|---|---|---|
| 业务上"select for update" | 中 | 加行锁 |
| 关键字段加唯一索引 | 强 | 插入时校验 |
| Seata `SELECT FOR UPDATE` | 强 | 等全局锁释放 |
| 业务幂等 + 终态判断 | 强 | 无 |

**推荐组合**:
1. 关键写用 `SELECT ... FOR UPDATE` 拿 Seata 全局锁
2. 读走普通 SELECT, 但有版本号 / 终态校验

### 9.3 XA 模式隔离

XA 走 DB 原生 RC 或 RR 隔离, 不会出现 Seata AT 的脏读问题.

---

## 10. 事务生命周期

### 10.1 状态机 (Global)

```
              Begin
               │
               ▼
        ┌─────────────┐
        │   BEGIN     │
        └──────┬──────┘
               │
        ┌──────▼──────┐
        │   ACTIVE    │  ← 注册分支, 执行 SQL
        └──────┬──────┘
               │
        ┌──────┴──────┐
        │             │
        ▼             ▼
   ┌─────────┐  ┌─────────┐
   │COMMITTING│ │ROLLBACKING│
   └────┬────┘  └────┬────┘
        │             │
   询问所有RM     通知所有RM
        │             │
        ▼             ▼
   ┌─────────┐  ┌─────────┐
   │COMMITTED │ │ROLLED_BACK│
   └─────────┘  └─────────┘
```

### 10.2 状态机 (Branch)

```
   Registered
       │
   ┌───┴───┐
   ▼       ▼
PhaseOneDone  Failed
   │       │
   │       ▼
   │   PhaseOneFailed
   │
   ▼
PhaseTwoCommitted  /  PhaseTwoRollbacked
```

### 10.3 超时机制

```java
@GlobalTransactional(timeoutMills = 60000)  // 60s 超时
```

超时触发:
- 60s 后 TC 主动标记 `GlobalRollback`
- RM 收到后读 undo_log 反向补偿

**默认 timeout**: 60s, 改 `client.tm.default-global-transaction-timeout`.

---

## 11. 故障恢复

### 11.1 TC 宕机重启

```
TC 启动 → SessionRecover 扫描 global_table
        → 找 status = COMMITTING / ROLLBACKING 的会话
        → 询问对应 RM 当前状态
        → 续推进二阶段
        → 完成或回滚
```

**关键**: `global_table/branch_table` 状态持久化在 DB, 重启不丢.

### 11.2 RM 宕机

RM 宕机时如果 TC 还在:
- 异步二阶段, RM 重启后从 TC 拉未完成分支
- 继续 commit/rollback

**RM 长时间不可用** (timeout):
- TC 标记分支失败
- 全局事务回滚
- 其它分支跟着回滚

### 11.3 全链路超时

```
RM1 注册分支 → RM2 慢 → 超时 → TC 全局回滚 → RM1 也回滚
```

### 11.4 预防: 监控告警

```yaml
# seata-server 端 metrics
metrics:
  enabled: true
  registry-type: compact
  exporter-list: prometheus
  exporter-prometheus-port: 9898
```

监控指标:
- `seata.session.global.active.count` (活跃全局事务数)
- `seata.session.branch.active.count` (活跃分支数)
- `seata.transaction.commit.count` / `rollback.count`
- `seata.lock.contention.count` (锁竞争)
- `seata.branch.report.failed.count` (分支上报失败)

---

## 12. 配置矩阵

### 12.1 TC (Server) 端

| 配置 | 默认 | 说明 |
|---|---|---|
| `server.port` | 8091 | Netty 端口 |
| `server.service-port` | 8091 | 同上 |
| `server.max-commit-retry-timeout` | -1 | commit 重试超时 (ms), -1=无限 |
| `server.max-rollback-retry-timeout` | -1 | rollback 重试超时, -1=无限 |
| `server.recovery.committing-retry-period` | 1000 | committing 状态重试周期 |
| `server.recovery.rollbacking-retry-period` | 1000 | rollbacking 状态重试周期 |
| `server.recovery.timeout-retry-period` | 1000 | 超时检查周期 |
| `server.session.branch-async-queue-size` | 5000 | 分支异步队列 |
| `server.session.global-async-queue-size` | 2000 | 全局异步队列 |
| `server.undo.log-save-days` | 7 | undo_log 保留天数 |
| `server.undo.log-delete-period` | 86400000 | 清理周期 (1 天) |

### 12.2 Client 端

| 配置 | 默认 | 说明 |
|---|---|---|
| `client.tm.commit-retry-count` | 5 | 提交重试次数 |
| `client.tm.rollback-retry-count` | 5 | 回滚重试次数 |
| `client.tm.default-global-transaction-timeout` | 60000 | 全局事务默认超时 (ms) |
| `client.rm.async-commit-buffer-limit` | 10000 | 异步提交缓冲 |
| `client.rm.lock.retry-interval` | 10 | 锁等待重试间隔 (ms) |
| `client.rm.lock.retry-times` | 30 | 锁等待重试次数 |
| `client.rm.report-retry-count` | 5 | 上报重试次数 |
| `client.rm.table-meta-check-enable` | true | 表元数据检查 |
| `client.rm.sql-parser-type` | druid | SQL 解析器 |

### 12.3 性能参数

| 场景 | 调优 |
|---|---|
| 高 QPS | `client.rm.async-commit-buffer-limit` 调到 50000 |
| 长事务多 | `server.session.global-async-queue-size` 调到 10000 |
| 锁竞争多 | `client.rm.lock.retry-times` 调到 100 |
| 跨公网慢 | `client.rm.report-retry-count` 调到 10 |

---

## 13. 性能调优

### 13.1 TC Server 调优

```bat
# seata-server.bat
set JVM_XMS=4g
set JVM_XMX=8g
set JVM_XMN=2g
set JVM_GC_OPTS=-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled
set JVM_OOM=-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=D:\seata\logs
```

### 13.2 数据库调优

```sql
-- 1. 给 undo_log 加索引
ALTER TABLE undo_log ADD INDEX idx_log_created (log_created);

-- 2. 减少 undo_log 大小 (业务字段 JSON 序列化)
-- 避免存大文本字段 (TEXT/BLOB), 最多 VARCHAR(2000)

-- 3. 分区表 (TB 级)
ALTER TABLE undo_log PARTITION BY RANGE (TO_DAYS(log_created)) (
    PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
    ...
);
```

### 13.3 客户端调优

```yaml
seata:
  client:
    rm:
      async-commit-buffer-limit: 50000
      report-retry-count: 5
      table-meta-check-enable: false  # 高 QPS 关闭元数据检查
    tm:
      commit-retry-count: 5
      rollback-retry-count: 5
```

### 13.4 连接池调优

```java
// Seata 包装的 DataSource 用原 HikariCP 配置
@Bean
public DataSource dataSource() {
    HikariDataSource ds = new HikariDataSource();
    ds.setMaximumPoolSize(50);
    ds.setMinimumIdle(10);
    ds.setConnectionTimeout(30000);
    return new DataSourceProxy(ds);
}
```

### 13.5 性能参考 (单 TC)

| 场景 | QPS | 延迟 |
|---|---|---|
| 简单 AT 事务 (单分支) | 5000+ | 5ms |
| 复杂 AT 事务 (5 分支) | 1000+ | 30ms |
| TCC | 8000+ | 3ms |
| Saga | 3000+ | 50ms (含补偿) |
| XA | 500+ | 100ms |

**测试条件**: 8C16G, SSD, MySQL 8.0, 单 TC 实例.

---

## 14. 常见故障排查

### 14.1 启动期

| 错误 | 原因 | 解决 |
|---|---|---|
| `no available server` | 客户端连不上 TC | 检查 Nacos 注册、namespace 一致 |
| `Table 'xxx.undo_log' doesn't exist` | 业务库缺表 | 跑 01_schema.sql 末尾脚本 |
| `Access denied for user` | DB 密码错 | 检查 `store.db.password` |
| `403 nacos` | 2.3+ 鉴权 | 加 username/password |
| `port 8091 already in use` | 端口占用 | `netstat -an` 查 |
| `Could not find Cluster` | vgroupMapping 没配 | Nacos 加 `service.vgroupMapping.{name}={group}` |

### 14.2 运行期

| 错误 | 原因 | 解决 |
|---|---|---|
| `LockConflictException` | 全局锁等待超时 | 调大 retry-times / 查死锁 |
| `BranchReportFailed` | 分支上报失败 | 查 RM 网络 |
| `GlobalSessionListener error` | TC 状态机错误 | 看 `global_table` 状态 |
| `Dirty write detected` | AT 回滚时发现脏写 | 业务避免并发改同 row |
| `Transaction timeout` | 超时 | 调大 timeoutMills |
| `Could not commit JDBC Connection` | DB 连接异常 | 查 DB 健康 |

### 14.3 调试技巧

**1. 开启 DEBUG 日志** (`logback-spring.xml`):

```xml
<logger name="io.seata" level="DEBUG"/>
```

**2. 看 XID 流转**: grep XID 在所有服务日志里.

```
TC: GlobalSession[xid=192.168.1.1:8091:12345, status=ACTIVE]
RM-A: BranchRegister xid=192.168.1.1:8091:12345 branchId=1
RM-B: BranchRegister xid=192.168.1.1:8091:12345 branchId=2
TC: GlobalCommit xid=192.168.1.1:8091:12345
```

**3. 查 global_table**:

```sql
SELECT xid, status, application_id, gmt_create, gmt_modified
FROM global_table
ORDER BY gmt_create DESC LIMIT 20;
```

**4. 查 lock_table**:

```sql
SELECT * FROM lock_table WHERE xid = 'xxx';
```

**5. 查 undo_log**:

```sql
SELECT branch_id, xid, log_status, log_created, LENGTH(rollback_info) AS size
FROM undo_log
ORDER BY log_created DESC LIMIT 10;
```

### 14.4 紧急处置

**情况 1: TC 假死**

```bash
# 1) 看进程
jstack <pid> > thread_dump.txt

# 2) 看堆
jmap -heap <pid>

# 3) 实在不行 kill, 重启会 recover
```

**情况 2: 大量悬挂事务**

```sql
-- 查 status=ACTIVE 但 1 小时没结束的
SELECT xid, status, gmt_create
FROM global_table
WHERE status = 'ACTIVE' AND gmt_create < DATE_SUB(NOW(), INTERVAL 1 HOUR);

-- 手动标 ROLLED_BACK
UPDATE global_table SET status = 6 WHERE xid = 'xxx';
```

**情况 3: undo_log 撑爆表**

```sql
-- 立即清理
DELETE FROM undo_log WHERE log_created < DATE_SUB(NOW(), INTERVAL 7 DAY);
```

---

## 15. 与 Spring Cloud Alibaba 集成细节

### 15.1 自动装配流程

```
seata-spring-boot-starter (引入)
    ↓
spring.factories / AutoConfiguration.imports 自动加载
    ↓
SeataAutoConfiguration 读 seata.* 配置
    ↓
1. 初始化 GlobalTransactionScanner (扫描 @GlobalTransactional)
2. 初始化 DataSourceProxy (包装业务 DS)
3. 初始化 RpcClient (Netty 客户端连 TC)
4. 启动心跳 + 续约
```

### 15.2 XID 透传

```java
// Feign 调用: Seata 提供 Interceptor 自动透传
@Bean
public RequestInterceptor seataFeignInterceptor() {
    return template -> {
        String xid = RootContext.getXID();
        if (xid != null) {
            template.header("Xid", xid);
        }
    };
}

// Spring Cloud OpenFeign 自动注入 SeataFeignInterceptor
```

**链**: HTTP Header `Xid` → 下游服务 `RootContext.bindXID(xid)` → 业务 SQL 注册到同一全局事务.

### 15.3 多数据源场景

本项目 `seata-demo` 用了 3 个 DataSource, 每个都要 Seata 代理:

```java
@Configuration
public class DataSourceConfig {

    // 1. 订单库
    @Bean("orderDs")
    @ConfigurationProperties(prefix = "spring.datasource.order")
    public DataSource orderDs() { return new HikariDataSource(); }

    @Bean("orderProxy")
    public DataSource orderProxy(@Qualifier("orderDs") DataSource ds) {
        return new DataSourceProxy(ds);  // ★ Seata 包装
    }

    @Bean("orderSf")
    public SqlSessionFactory orderSf(@Qualifier("orderProxy") DataSource proxy) throws Exception {
        SqlSessionFactoryBean f = new SqlSessionFactoryBean();
        f.setDataSource(proxy);  // 用代理
        return f.getObject();
    }

    @MapperScan(basePackages = "...order.mapper", sqlSessionFactoryRef = "orderSf")
    public class OrderMapperConfig {}
    // ... stock, account 同样
}
```

### 15.4 关键: 二重注解

```java
@GlobalTransactional(name = "tx", rollbackFor = Exception.class)  // Seata 全局
@Transactional(rollbackFor = Exception.class)                       // 本地兜底
public String doOrder() {
    orderService.create();
    stockService.decrease();
    accountService.debit();
}
```

**为什么双注解**:
- TC 在场: `@GlobalTransactional` 接管, `@Transactional` 走 REQUIRED 加入
- TC 不在场: `@GlobalTransactional` 抛 "No available service" 失败, 但 `@Transactional` 仍兜底
- 双重保护, 任何场景都不丢事务

### 15.5 H2 单机测试

seata-demo 用 H2 (内存库), 注意:

```java
// H2 URL 加 ;LOCK_MODE=0 (关闭文件锁, 允许多连接)
"jdbc:h2:mem:test;LOCK_MODE=0;DB_CLOSE_DELAY=-1;MODE=MYSQL"
```

---

## 16. Seata 2.0 vs 1.x 重大变化

| 项 | 1.5.x | 2.0.x |
|---|---|---|
| 配置格式 | `registry.conf + file.conf` | `application.yml` 统一 |
| 启动类 | `seata-server.sh -h ip -p port` | `seata-server.bat` 读 `application.yml` |
| Spring Boot 3 | ❌ | ✅ (Jakarta EE) |
| XA 模式 | 实验 | GA |
| 协议 | 私有 | 支持 gRPC + Netty |
| Console | 内嵌 | 独立 7091 端口, 鉴权 |
| Metrics | 弱 | Prometheus 完善 |
| Saga | JS 状态机 | 注解 + 状态机 |

**升级注意**: 配置完全重写, 旧 `registry.conf` 兼容但推荐迁到 `application.yml`.

---

## 17. 总结: Seata 操作清单

### 上线前 checklist

- [ ] JDK 17 + MySQL 8 + Nacos 2.3 都正常
- [ ] Seata 2.0 zip 下载并解压
- [ ] seata 库 + 3 张表已建
- [ ] 每个业务库建 `undo_log` 表
- [ ] Nacos 命名空间 `seata` 创建
- [ ] config.txt 上传到 Nacos (或用 Python 脚本)
- [ ] application.yml 改 namespace + 密码
- [ ] 启动 Seata Server, 验证 `curl :7091/api/v1/health`
- [ ] Nacos 服务列表有 `seata-server`
- [ ] 业务代码加 `@GlobalTransactional`
- [ ] 每个 DS 用 `DataSourceProxy` 包装
- [ ] Feign Interceptor 注入 `Xid` header
- [ ] 跑集成测试, 13/13 通过
- [ ] 配置 Prometheus 抓 `:9898/metrics`
- [ ] Grafana 导入 Seata Dashboard (id: 10807)
- [ ] 监控告警规则: 全局事务 > 5min / 锁竞争 > 100/s

### 日常运维

- [ ] 每日: 看 GC 日志 + undo_log 大小
- [ ] 每周: 清理 undo_log (7 天前)
- [ ] 每月: 看 Seata 性能指标趋势
- [ ] 季度: 全链路演练, 模拟 TC 宕机 / RM 慢响应

---

## 18. 参考

- 官方: <https://seata.io/>
- 2.0 文档: <https://seata.apache.org/>
- AT 模式: <https://seata.io/zh-cn/docs/dev/mode/at-mode.html>
- TCC 模式: <https://seata.io/zh-cn/docs/dev/mode/tcc-mode.html>
- 本项目: `backend/seata-demo/`
- 部署文档: `docs/SEATA-WINDOWS-INSTALL.md`
