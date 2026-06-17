# 生产部署手册 (今晚生产!)

> **目标**: 30 分钟内完成整套平台部署到生产, 包括后端 9 个微服务 + 前端 + Redis + (可选) MySQL / Nacos.

---

## 1. 部署清单

| 组件 | 端口 | 启动顺序 | 是否必须 |
|---|---|---|---|
| **Redis 7** | 6379 | 1 | ✅ 必须 |
| **MySQL 8** | 3306 | 2 | ✅ 必须 (用项目自带 schema 脚本) |
| **ai-platform-auth** | 9001 | 3 | ✅ 必须 |
| **ai-platform-user** | 9002 | 3 | ✅ 必须 |
| **ai-platform-system** | 9003 | 3 | ✅ 必须 |
| **ai-platform-workflow** | 9011 | 3 | ✅ 必须 (新功能核心) |
| **ai-platform-knowledge** | 9005 | 3 | ✅ 必须 |
| **ai-platform-trainer** | 9010 | 3 | ✅ 必须 |
| **ai-platform-model** | 9004 | 3 | ✅ 必须 |
| **ai-platform-agent** | 9006 | 3 | ✅ 必须 |
| **ai-platform-files** | 9008 | 3 | ✅ 必须 |
| **ai-platform-inference** | 9007 | 4 | 推荐 |
| **ai-platform-gateway** | 9000 | 5 | ✅ 必须 (统一入口) |
| **Nacos** (可选) | 8848 | - | 可选, 默认关 |
| **Frontend (Nginx)** | 80/443 | 6 | ✅ 必须 |

---

## 2. 环境准备 (10 分钟)

### 2.1 Redis 7 (必须, 不超过 1 分钟)

```bash
# Windows (Docker)
docker run -d --name redis -p 6379:6379 redis:7

# Linux
docker run -d --name redis -p 6379:6379 redis:7
# 或
apt install -y redis && systemctl start redis

# 验证
redis-cli ping  # 输出 PONG
```

### 2.2 MySQL 8 + 项目 schema (必须, 5 分钟)

```bash
# 启动 MySQL
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=951127 -p 3306:3306 mysql:8

# 等待 30s, 导入 schema
docker exec -i mysql mysql -uroot -p951127 < deploy/sql/01_schema.sql
docker exec -i mysql mysql -uroot -p951127 < deploy/sql/02_seed.sql
```

**注意**: 项目默认密码 `951127` (跟生产要求一致), 改密改 `application.yml` 里 `${MYSQL_PASSWORD:951127}`.

### 2.3 Nacos (可选, 0 分钟跳过)

生产环境**不需要 Nacos**:
- 所有 yml 已经 `nacos.config.import` 关闭, 走本地配置
- 服务发现也走本地 (`nacos.discovery.enabled: false`)

直接跳过 Nacos, 服务照样跑.

---

## 3. 后端启动 (10 分钟)

### 3.1 编译 (本地一次性, 上传 jar 到生产)

```powershell
cd E:\toolsProject\aiModel\backend
mvn -T 2C -DskipTests -B clean package
```

会生成 9 个 jar 到 `ai-platform-*/target/*.jar`. 上传到生产.

### 3.2 一键启动所有服务 (Linux)

```bash
#!/bin/bash
# deploy/start-all.sh
cd /opt/ai-platform
mkdir -p logs

# 1. Redis
docker start redis 2>/dev/null || docker run -d --name redis -p 6379:6379 redis:7
sleep 2
redis-cli ping

# 2. MySQL
docker start mysql 2>/dev/null || docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=951127 -p 3306:3306 mysql:8
sleep 5

# 3. 微服务 (并行启动, 后台)
for svc in auth user system workflow knowledge trainer model agent files inference; do
  nohup java -Xms256m -Xmx512m -jar ai-platform-$svc/target/ai-platform-$svc.jar \
    > logs/$svc.log 2>&1 &
  echo "Started $svc (pid=$!)"
done

# 4. Gateway 最后启动
nohup java -Xms256m -Xmx512m -jar ai-platform-gateway/target/ai-platform-gateway.jar \
  > logs/gateway.log 2>&1 &
echo "Started gateway (pid=$!)"

echo ""
echo "等待服务启动..."
sleep 30

# 5. 健康检查
echo "=== 健康检查 ==="
for port in 9001 9002 9003 9011 9005 9010 9004 9006 9008 9007 9000; do
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/api/auth/health | grep -q 200; then
    echo "✓ port $port OK"
  else
    echo "✗ port $port FAIL"
  fi
done
```

### 3.3 Windows 启动

每个服务双击运行, 或写一个 `start-all.bat`:

```bat
@echo off
REM deploy\start-all.bat
for %%s in (auth user system workflow knowledge trainer model agent files inference) do (
  start "ai-%%s" /min java -Xms256m -Xmx512m -jar ai-platform-%%s\target\ai-platform-%%s.jar
  echo Started %%s
)
start "ai-gateway" /min java -Xms256m -Xmx512m -jar ai-platform-gateway\target\ai-platform-gateway.jar
echo Started gateway
```

### 3.4 启动顺序 (关键)

| 步骤 | 服务 | 理由 |
|---|---|---|
| 1 | Redis | 9 个服务都依赖 |
| 2 | MySQL | 大部分服务 feign 调用 user/system |
| 3 | auth + user + system + workflow | 这 4 个是核心, 先起 |
| 4 | 其他服务 | 后面启动不依赖核心 |
| 5 | gateway | 最后启动, 网关路由是动态发现 |

---

## 4. 前端部署 (5 分钟)

### 4.1 编译

```bash
cd frontend
npm install   # 第一次需要, 之后不用
npm run build
```

产出 `dist/` 目录, 包含 `index.html` + `assets/`.

### 4.2 Nginx 配置

```nginx
# /etc/nginx/conf.d/ai-platform.conf
server {
    listen 80;
    server_name your-domain.com;  # 改成你的域名或 IP

    root /opt/ai-platform/frontend/dist;
    index index.html;

    # 前端 SPA 路由
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 静态资源缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 后端 API 代理到 gateway (9000)
    location /api/ {
        proxy_pass http://127.0.0.1:9000/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_read_timeout 30s;
        proxy_send_timeout 30s;
    }

    # 限流 (防爆破)
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    limit_req zone=api burst=20 nodelay;
}
```

```bash
# 加载
nginx -t && nginx -s reload
```

---

## 5. 验证 (5 分钟)

### 5.1 服务健康

```bash
# 后端
curl http://localhost:9000/api/auth/health
# → {"code":200,"message":"操作成功","data":{"service":"ai-platform-auth","status":"UP"}}

# 工作流 (新功能)
curl http://localhost:9011/api/workflow/component-schemas | jq '.data | length'
# → 35  (35 个节点的 schema)

# AI 建议
curl -X POST http://localhost:9011/api/workflow/component-schemas/train_lora/suggest \
  -H "Content-Type: application/json" \
  -d '{"lr": 0.01}' | jq '.data.suggestions[0]'
# → {"key":"lr","current":"0.01","recommended":"0.001","reason":"..."}
```

### 5.2 前端

打开 `http://your-domain.com/`:
1. **登录**: admin / admin123 (默认)
2. **工作流编排**: /workflow
3. 双击任意节点 → 配置弹窗 (有 AI 建议按钮)
4. 加载 RAG 模板 → 3 节点流水线
5. 保存 + 运行

---

## 6. 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| 启动报 `Unable to connect to Redis` | Redis 没启 或 IP/端口错 | `redis-cli ping` + 改 `application.yml` |
| 启动报 `Communications link failure` (MySQL) | MySQL 没启 或 密码错 | 用 951127 试 |
| 登录报 `401` | 启动顺序错, user/system 没就绪 | 重启 auth |
| 工作流加载模板失败 | workflow 服务没启 | 看 9011 端口 |
| 节点配置弹窗空白 | 后端 component-schemas 端点没启 | 重启 workflow 服务 |
| 前端 vite proxy 404 | gateway 没启 / 端口错 | 看 9000 |

---

## 7. 关键配置 (生产调整)

### 7.1 密码

```yaml
# application.yml 里改
spring:
  datasource:
    password: ${MYSQL_PASSWORD:951127}  # 改这里
```

### 7.2 跨域 (生产由 gateway 统一处理)

```yaml
# gateway application.yml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "*"
            allowed-methods: "*"
            allowed-headers: "*"
            allow-credentials: true
```

### 7.3 JVM 调优

```bash
java -Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
     -jar ai-platform-auth.jar
```

每个服务建议:
- `Xms = Xmx = 256m` (轻服务: user/system/auth)
- `Xms = Xmx = 512m` (重服务: workflow/agent/knowledge)
- `Xms = Xmx = 1g` (训练 / 推理: trainer/inference)

---

## 8. 监控 (上线后)

- 9 个服务各自暴露 `/actuator/health`
- 集成 Prometheus: 加 `micrometer-registry-prometheus` dep
- Redis: `redis-cli info` 看内存/连接数
- MySQL: `SHOW PROCESSLIST` 看慢查询
- 日志: `tail -f logs/*.log` 或 ELK

---

## 9. 工作流新功能验证清单 (今晚重点)

- [x] 双击节点 → 配置弹窗, 字段从**后端 schema** 动态生成 (不是前端 hardcode)
- [x] AI 建议按钮 → 调 `/suggest` 拿推荐 + 原因, 单条/全部应用
- [x] 自连 / 重边 / 死循环检测 + 标红
- [x] 流程不合法时禁止运行/导出, 但可保存
- [x] 35 节点全覆盖, 8 个组: 数据准备 / 训练 / 评估 / 部署 / Agent / 知识库 / 工具 / 推理 + 控制流
- [x] 放大画布弹窗 (缩放/拖动/还原, 不动数据)
- [x] 加载 RAG 模板 (kb_ingest → kb_search → agent_think)
- [x] 使用说明 (6 节具体)

---

## 10. 紧急回滚

```bash
# 停所有服务
pkill -f ai-platform-

# 回滚代码
cd /opt/ai-platform
git pull origin main --tags
git checkout <previous-stable-tag>

# 重启
bash deploy/start-all.sh
```

---

**部署完成, 即可访问 http://your-domain.com**
