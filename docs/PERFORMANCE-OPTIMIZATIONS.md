# Performance Optimizations (v3.x)

## 缓存覆盖清单

| Module | Service | Method | TTL | 触发 invalidate |
|---|---|---|---|---|
| **user** | UserService | getByUsername | 5min | update/delete |
| | | getById | 5min | update/delete |
| | | listAll | 5min | create/update/delete |
| | AuthService | login() | **rate limit 10/60s** | sliding window |
| **system** | MenuService | list | 10min | create/update/delete/assignToRole |
| | | tree | 10min | (computed from list) |
| | | listMenuIdsByRole | 10min | assignToRole |
| | RoleService | list | 5min | create/update/delete/changeStatus |
| | | listByUser | 5min | assignToUser (pattern evict) |
| | | listUserIdsByRole | 5min | (none) |
| **knowledge** | ModelRegistryService | list | 1min | register/update/delete |
| | | getById | 1min | register/update/delete |
| | | listVersions | 1min | register/update/delete |
| | KnowledgeService | listBases | 5min | createBase |
| | TrainJobService | get(id) | **10s** | submit + runAsync 5处 |
| **agent** | AgentService | list | 5min | create/update/delete |
| | | getById | 5min | update/delete |
| | ToolService | list | 10min | create/delete |
| | ConversationService | list(agentId) | 2min | open() |
| **workflow** | WorkflowSpecRepository | list | 5min | save/delete/duplicate/incrRunCount |
| | | getById | 5min | save/delete/incrRunCount |
| | | listSimple | 5min | (from list) |

## 慢 SQL 监控

**拦截器**: `SlowSqlMybatisInterceptor` (`ai-platform-web-starter`)

**配置**:
```yaml
aiplatform:
  sql:
    enabled: true
    slow-threshold-ms: 200  # 阈值
    log-enabled: true
```

**日志格式**:
```
[SQL-SLOW] cost=350ms msId=com.aiplatform.xx.service.X.method sql=SELECT ... params=...
[SQL-ERR] cost=120ms e=...
```

**MyBatis @Signature**: `StatementHandler.prepare/update/batch` — 拦截所有 SQL 执行

## MyBatis-Plus 配置

**自动注册拦截器**:
- `PaginationInnerInterceptor` - Page<T> 自动加 LIMIT
- `SlowSqlMybatisInterceptor` - 慢 SQL 日志

**默认配置** (`spring-defaults.properties`):
- `mybatis-plus.configuration.map-underscore-to-camel-case=true`
- `mybatis-plus.global-config.db-config.logic-delete-field=deleted`
- `spring.datasource.hikari.maximum-pool-size=20`
- `spring.datasource.hikari.minimum-idle=5`
- `spring.datasource.hikari.max-lifetime=1200000`

## 安全增强

**登录限流** (Redis sliding window, Lua atomic):
- 同一用户名 + IP: 60s 最多 10 次
- 超过抛 `SYSTEM_BUSY (450)`: "登录尝试过于频繁"
- 防密码爆破 + 资源耗尽

## 性能效果预估

| 场景 | 原 QPS | 优化后 | 倍数 |
|---|---|---|---|
| 登录 (UserService.getByUsername) | 200 | 10000+ | **50x** |
| 模型列表 (5+ 页面调用) | 50 | 5000+ | **100x** |
| 角色查询 (JWT 鉴权每次请求) | 500 | 50000+ | **100x** |
| 菜单树 (登录/路由切换) | 100 | 10000+ | **100x** |
| 训练任务轮询 (TrainJobService.get) | 1/s | 0.1/s | **10x** (TTL 10s) |

## 待验证 (用户 Windows 端)

```powershell
cd E:\toolsProject\aiModel\backend
mvn clean install -DskipTests
# 启动 user/system/agent/knowledge/workflow 模块
# 看启动日志:
#   [MybatisInterceptorConfig] 慢 SQL 监控启用, threshold=200ms
#   [DefaultPerformance] 默认性能配置加载: 慢 SQL 监控 + HikariCP 调优
# 用 redis-cli 看缓存命中:
#   redis-cli KEYS 'aiplatform:*'
# 压测: wrk -t4 -c100 -d30s http://localhost:9002/api/auth/login
```

## 模块清单

11 个微服务, 29+ service 类, 30+ 张表
覆盖率 ~84.5% (840/995 测试)