# 监控告警使用手册 (MONITORING)

> **目标**: 7×24 监控 AI Platform 全栈, 故障 30min 响应

---

## 一、监控 3 层

### 1.1 主机层 (Node)
- CPU / 内存 / 磁盘 / 网络
- 工具: `prom/node-exporter` + Prometheus

### 1.2 容器层 (Container)
- 11 个微服务 + 中间件 (MySQL/Redis/ES/Nacos)
- 工具: `cadvisor` (可选) + Docker healthcheck

### 1.3 应用层 (Application)
- JVM 内存 / 线程 / GC
- HTTP 请求 QPS / 延迟 / 错误率
- 业务指标: 工作流运行 / 训练任务 / 知识库检索
- 工具: Spring Boot Actuator + Micrometer → Prometheus

---

## 二、Grafana 看板

| 看板 | 内容 |
|---|---|
| **Overview** | 全平台总览 (服务 UP/DOWN 状态) |
| **JVM** | Heap / GC / Thread / ClassLoad |
| **HTTP** | QPS / 延迟 P50/P95/P99 / 错误率 |
| **DB** | MySQL 连接池 / 慢查询 / 主从延迟 |
| **Redis** | 命中率 / 内存 / 连接数 |
| **Business** | 业务自定义指标 |

访问: `http://localhost:3000` (admin/admin)

---

## 三、告警规则 (Prometheus)

| 规则 | 触发条件 | 等级 |
|---|---|---|
| ServiceDown | 服务停止响应 2min | **critical** |
| HighCpuUsage | CPU > 85% 持续 5min | warning |
| HighMemoryUsage | 内存 > 90% 持续 5min | warning |
| DiskFull | 磁盘 > 85% 持续 5min | warning |
| SlowResponse | 99 分位延迟 > 3s 持续 5min | warning |

---

## 四、告警通道 (Webhook)

### 4.1 飞书
```bash
export FEISHU_WEBHOOK_URL='https://open.feishu.cn/open-apis/bot/v2/hook/XXXXX'
./scripts/alert-webhook.sh
```

### 4.2 钉钉
```bash
export DINGTALK_WEBHOOK_URL='https://oapi.dingtalk.com/robot/send?access_token=XXXXX'
./scripts/alert-webhook.sh
```

### 4.3 企业微信
```bash
export WECOM_WEBHOOK_URL='https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=XXXXX'
./scripts/alert-webhook.sh
```

### 4.4 Alertmanager 集成
```yaml
receivers:
  - name: 'ops-feishu'
    webhook_configs:
      - url: 'http://alertmanager-webhook:5001/alert'
        send_resolved: true
```

---

## 五、日常巡检

### 每天 (自动 + 人工)
- [x] 09:00 看昨日告警
- [x] 检查备份任务是否完成
- [x] 检查磁盘空间
- [x] 看核心业务 QPS 趋势

### 每周
- [ ] 周一全量 `monitor.sh` 输出
- [ ] 周三演练一次回滚
- [ ] 周五检查 backup 完整性

### 每月
- [ ] 灾备演练 (从备份恢复)
- [ ] 容量规划 (磁盘/带宽/连接数)
- [ ] 安全补丁更新

---

## 六、命令速查

```bash
# 一次性检查
./scripts/monitor.sh

# 周期检查
watch -n 30 ./scripts/monitor.sh

# 健康检查
curl http://localhost:9000/actuator/health
curl http://localhost:9000/actuator/prometheus | head -20

# 备份
./scripts/backup-mysql.sh
./scripts/backup-redis.sh

# 恢复
./scripts/restore-mysql.sh /path/to/backup.sql.gz

# 回滚
./scripts/rollback.sh gateway 2.0.20260617

# 重启单服务
docker compose -f deploy/docker/docker-compose.yml restart gateway

# 看日志
docker logs -f --tail 100 ai-gateway
```
