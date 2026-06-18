# 事故响应手册 (INCIDENT-RESPONSE)

> **目标**: P0 故障 30 分钟内响应, 1 小时内恢复 (SLA 99.5%)
> **运维经理**: on-call 排班轮转
> **联系**: 飞书 #ops-alert 频道, 24h 有人值守

---

## 一、事故等级 (Severity)

| 等级 | 描述 | 响应时间 | 升级 |
|---|---|---|---|
| **P0** | 全平台不可用 / 数据丢失 | **30min** | 立即拉群, 通知 CTO |
| **P1** | 核心功能不可用 (登录/下单) | 2h | 通知部门负责人 |
| **P2** | 部分功能不可用 (影响 1-2 模块) | 8h | 工单跟进 |
| **P3** | 体验问题 / 性能下降 | 24h | 排期修复 |

---

## 二、事故响应 5 步法 (通用)

### Step 1: 确认 (1-5min)
1. 看 Grafana 监控 — 是哪个服务?
2. 看 Alertmanager — 触发什么规则?
3. 看 ELK 日志 — 什么错误?
4. 拉相关同事到飞书临时群

### Step 2: 定级 (5-10min)
- 评估影响范围: 用户数 / 业务 / 数据
- 套 P0-P3 等级
- 决定是否回滚 (P0 必须)

### Step 3: 止血 (10-30min)
**优先恢复服务, 不在故障期改代码!**

| 场景 | 止血操作 |
|---|---|
| 单服务挂 | `docker compose restart <svc>` |
| 数据库连接池满 | 重启服务 + 看慢 SQL |
| 磁盘满 | 删日志 (`rm /opt/ai-platform/logs/*.gz`) + 扩盘 |
| 内存爆 | 重启服务 + 查 OOM 哪个线程 |
| 误发布 | `./scripts/rollback.sh <svc> <version>` |
| 配置错 | 改 Nacos / env, 重启服务 |

### Step 4: 根治 (P0: 4h, P1: 24h)
- 找 root cause (5 why / fishbone)
- 写 hotfix
- 测试 + 灰度

### Step 5: 复盘 (P0: 24h, P1: 72h)
- 写事故报告 (模板见附录)
- 团队 review
- 改进项纳入下迭代

---

## 三、常见事故 Runbook

### 事故 1: Gateway 502
**症状**: 前端 502 / 接口调不通
**排查**:
```bash
docker ps | grep gateway            # 容器在不在
docker logs --tail 50 ai-gateway    # 看启动日志
curl http://127.0.0.1:9000/actuator/health   # 直接打
```
**恢复**:
```bash
docker compose -f deploy/docker/docker-compose.yml restart gateway
```

### 事故 2: MySQL 连接失败
**症状**: 服务报 `Communications link failure`
**排查**:
```bash
docker exec ai-mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD -e "SHOW PROCESSLIST"
docker exec ai-mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD -e "SHOW STATUS LIKE 'Threads_connected'"
```
**恢复**:
```bash
# 连接数满: 杀慢查询
docker exec ai-mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD -e "KILL <ID>"
# 仍不行: 重启服务
docker compose -f deploy/docker/docker-compose.yml restart user system model
```

### 事故 3: Redis 挂了
**症状**: 分布式锁/限流/Session 全失效
**排查**:
```bash
docker logs ai-redis
docker exec ai-redis redis-cli ping
```
**恢复**:
```bash
docker compose -f deploy/docker/docker-compose.yml restart redis
# 数据丢失: 从 backup 恢复
./scripts/restore-redis.sh /opt/ai-platform/backups/redis/redis_20260618_040000.rdb
```

### 事故 4: ES 满 / 索引损坏
**症状**: 知识库检索失败
**排查**:
```bash
curl -XGET http://127.0.0.1:9200/_cluster/health?pretty
curl -XGET http://127.0.0.1:9200/_cat/indices?v
```
**恢复**:
```bash
# 删旧索引
curl -XDELETE http://127.0.0.1:9200/old_index_*
# 重建
./scripts/reindex-knowledge.sh
```

### 事故 5: Nacos 不可用
**症状**: 所有服务配置失联
**排查**:
```bash
docker logs ai-nacos
curl http://127.0.0.1:8848/nacos/
```
**恢复**:
```bash
docker compose -f deploy/docker/docker-compose.yml restart nacos
# Nacos 持久化在 nacos-data 卷, 重启不丢配置
```

### 事故 6: 误删数据库
**症状**: 数据丢失
**恢复** (立即执行):
```bash
./scripts/restore-mysql.sh /opt/ai-platform/backups/mysql/ai_platform_20260618_030000.sql.gz
```

### 事故 7: 黑客攻击 / 数据泄露
**症状**: 异常登录 / 数据导出 / 流量暴涨
**响应**:
1. 立即拉群
2. 断公网 (Nginx 关 80 端口)
3. 拉所有登录审计 / 操作审计
4. 改所有密钥 (JWT / DB / Redis)
5. 报警 + 报备 (处长 + 法务)

---

## 四、应急联系

| 角色 | 联系方式 |
|---|---|
| 运维经理 | on-call 排班表 (P0 24h) |
| 后端架构师 | 内部 IM |
| 数据库 DBA | 内部 IM |
| 安全负责人 | 内部 IM |
| 法务 | 内部 IM |
| **CTO** | 仅 P0 通知 |

---

## 五、值班表 (示例)

| 周次 | 主值班 | 备值班 |
|---|---|---|
| W24 | 张三 | 李四 |
| W25 | 李四 | 王五 |
| W26 | 王五 | 张三 |

---

## 六、事故报告模板

```markdown
# 事故报告 - <标题>

## 基本信息
- 事故 ID: INC-2026-XXXX
- 等级: P0
- 开始: 2026-06-18 10:00
- 恢复: 2026-06-18 11:30
- 影响时长: 1h 30m
- 影响用户: 约 2000

## 时间线
- 10:00 用户报障登录失败
- 10:05 值班确认 MySQL 连接池满
- 10:10 重启服务
- 10:30 仍报障, 发现是慢 SQL
- 10:45 Kill 慢查询, 服务恢复
- 11:00 全量验证 OK
- 11:30 关闭事故

## Root Cause
- 某慢 SQL 在 10:00 突然变慢 (10s+)
- 连接池被打满, 后续请求全挂

## 改进项
- [ ] 加慢 SQL 告警 (>1s)
- [ ] 加连接池使用率告警 (>80%)
- [ ] 加 SQL 慢日志收集
```
