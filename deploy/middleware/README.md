# 运行时中间件一键部署

> **目标**: Windows / Linux / macOS 都能**一条命令**拉起 6 个中间件
> **适用**: 部署 AI Platform 完整版 (11 微服务 + 6 中间件)

## 包含的中间件 (6 件套)

| # | 名称 | 镜像 | 端口 | 用途 |
|---|---|---|---|---|
| 1 | **Elasticsearch** | `docker.elastic.co/elasticsearch/elasticsearch:8.13.0` | 9200 | 知识库 RAG (向量/全文) |
| 2 | **Nacos** | `nacos/nacos-server:v2.3.1` | 8848, 9848 | 配置中心 / 服务注册 (可选) |
| 3 | **Nginx** | `nginx:alpine` | 8080 | 前端代理 (网关入口) |
| 4 | **Prometheus** | `prom/prometheus:v2.50.0` | 9090 | 指标采集 |
| 5 | **Grafana** | `grafana/grafana:10.4.0` | 3000 | 可视化 (admin/admin) |
| 7 | **Ollama** | `ollama/ollama:latest` | 11434 | 本地 LLM (qwen2.5/nomic-embed) |

> **数据库 MySQL 8 / Redis 7 走项目主 `deploy/docker/docker-compose.yml`** (在那个 compose 里管理)

---

## Windows 11 / 10 用户

### 准备工作 (5 分钟)

1. **装 Docker Desktop**
   - 官网: <https://www.docker.com/products/docker-desktop/>
   - 双击安装, 启动后等右下角**图标变绿** (🐳)
   - **Settings → Resources → Memory** 调到 **≥ 4GB** (ES + Ollama 需要)

2. **打开 PowerShell 或 Git Bash**
   - 推荐 **WSL2** (Ubuntu 22.04) — 路径最兼容
   - 或 **Git Bash** (装 Git for Windows 时带)
   - **PowerShell 原生** 也支持 (.ps1 脚本)

### 启动 (1 条命令)

#### 方法 A: PowerShell 原生
```powershell
cd D:\toolsProject\aiModel\deploy\middleware
.\deploy-middleware.ps1

# 首次运行若报错 "running scripts is disabled":
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Bypass
```

#### 方法 B: WSL2 / Git Bash
```bash
cd /d/toolsProject/aiModel/deploy/middleware
bash deploy-middleware.sh
```

#### 方法 C: 一行
```bash
bash <(curl -fsSL https://raw.githubusercontent.com/liugl951127/aiModel/main/deploy/middleware/deploy-middleware.sh)
```

### 启动后

```
✓ docker: Docker version 24.0.7
✓ Docker daemon: running
✓ docker compose: v2.21.0
✓ 6 个端口 (8080/8848/9200/3000/9090/11434) 全部空闲
====================================================  启动 6 个中间件  ====================================================
[+] Running 7/7
 ✔ Network ai-platform-net        Created
 ✔ Volume ...es-data              Created
 ...
 ✔ Container ai-middleware-elasticsearch  Started
 ...

✓ 6 个中间件已起在后台
  访问入口 (浏览器):
    Nginx       http://localhost:8080
    Grafana     http://localhost:3000  (admin/admin)
    Prometheus  http://localhost:9090
    Ollama      http://localhost:11434
    ...
```

### 验证 (等 30-60s 后)

```bash
# bash
bash deploy-middleware.sh health
# PowerShell
.\deploy-middleware.ps1 health
```

期望:
```
✓ Nginx (http://127.0.0.1:8080) → 200
✓ Grafana (http://127.0.0.1:3000/api/health) → 200
✓ Prometheus (http://127.0.0.1:9090/-/ready) → 200
✓ Elasticsearch (http://127.0.0.1:9200/_cluster/health) → 200
✓ Nacos (http://127.0.0.1:8848/nacos/) → 200
✓ Ollama (http://127.0.0.1:11434/api/tags) → 200

✓ 全部健康 ✓
```

---

## Linux / macOS

```bash
cd /path/to/aiModel/deploy/middleware
bash deploy-middleware.sh
```

---

## 全部命令

| 命令 | bash | PowerShell | 作用 |
|---|---|---|---|
| 启动 | `bash deploy-middleware.sh` | `.\deploy-middleware.ps1` | 启动 6 个中间件 |
| 状态 | `bash deploy-middleware.sh status` | `.\deploy-middleware.ps1 status` | 看容器 + 端口 |
| 停止 | `bash deploy-middleware.sh stop` | `.\deploy-middleware.ps1 stop` | 停止 (数据保留) |
| 日志 | `bash deploy-middleware.sh logs` | `.\deploy-middleware.ps1 logs` | 所有服务日志 |
| 单服务日志 | `bash deploy-middleware.sh logs elasticsearch` | `.\deploy-middleware.ps1 logs elasticsearch` | 单个服务日志 |
| 健康检查 | `bash deploy-middleware.sh health` | `.\deploy-middleware.ps1 health` | 6 个 URL 检查 |
| 拉镜像 | `bash deploy-middleware.sh pull` | `.\deploy-middleware.ps1 pull` | 只下载不启动 |
| 删数据 | `bash deploy-middleware.sh reset` | `.\deploy-middleware.ps1 reset` | ⚠️ 删 ES/Nacos 等数据 |
| 帮助 | `bash deploy-middleware.sh help` | `.\deploy-middleware.ps1 help` | 帮助 |

---

## 跟项目其他脚本的关系

```
aiModel/
├── backend/                           11 微服务 Java 源码
├── frontend/                          Vue 3 前端
├── deploy/
│   ├── docker/                        11 微服务 Docker 编排
│   │   ├── Dockerfile.{11 个}
│   │   └── docker-compose.yml
│   ├── nginx/nginx.conf               前端代理配置
│   ├── prometheus/                    指标采集配置
│   │   ├── prometheus.yml
│   │   └── alerts.yml
│   ├── helm/                          K8s Helm chart
│   └── middleware/                    ★ 6 个中间件 (本目录)
│       ├── docker-compose.yml         6 个中间件编排
│       ├── deploy-middleware.sh        bash 脚本 (Linux/Mac/WSL)
│       └── deploy-middleware.ps1       PowerShell (Windows 原生)
├── scripts/                           运维脚本
└── docs/                              文档
```

### 部署顺序

```bash
# 1. 启 6 个中间件 (本脚本)
bash deploy/middleware/deploy-middleware.sh

# 2. 启 11 个微服务 (项目主 compose)
bash deploy/docker/dev-up.sh

# 3. 启前端 (开发模式)
bash scripts/dev-frontend.sh
# 或部署前端到 Nginx:
# cp -r frontend/dist/* /var/www/html/
```

---

## 常见问题

### Q1: 端口被占用怎么办?
脚本会警告, 但**仍继续**. Docker 自动映射到 8081/8082 等其他端口. 想强制释放:
```bash
# Windows
netstat -ano | findstr :8080    # 找占用 PID
taskkill /PID <PID> /F

# Linux/Mac
lsof -i :8080
kill -9 <PID>
```

### Q2: ES 启动失败, 报 `max virtual memory areas vm.max_map_count [65530] is too low`?
ES 需要更多 mmap 计数. Windows 暂不能直接调, 改用 single-node 模式 + `discovery.type=single-node` (已配). 或在 PowerShell admin:
```powershell
# WSL2 内
wsl -d Ubuntu -- sysctl -w vm.max_map_count=262144
```

### Q3: Ollama 启动后还是 404?
```bash
# 拉模型
docker exec -it ai-middleware-ollama ollama pull qwen2.5:7b
# 改 ai-platform-ai 配置
export OLLAMA_BASE_URL=http://localhost:11434
export AI_BACKEND=ollama
```

### Q4: 重置所有数据 (清白) ?
```bash
bash deploy-middleware.sh reset   # 输 YES 确认
# 重新启动
bash deploy-middleware.sh
```

### Q5: Nacos 启动很慢, 报 `db.num is null`?
Nacos 1.x 之后默认用 MySQL. 我们的 compose 没装 MySQL 容器 (复用项目主 MySQL). 解决:
- 启项目主 compose: `bash deploy/docker/dev-up.sh` (带 MySQL)
- 或改 Nacos 用内置 Derby: `MODE=standalone, SPRING_DATASOURCE_PLATFORM=derby`

### Q6: Windows 防火墙挡了?
```powershell
# PowerShell admin
New-NetFirewallRule -DisplayName "Docker" -Direction Inbound -Enabled True -Protocol TCP -LocalPort 8080,3000,9090,9200,8848,11434 -Action Allow
```

---

## 资源占用 (Docker Desktop)

| 资源 | 最小 | 推荐 |
|---|---|---|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 10 GB | 20 GB |

各中间件容器内存占用 (近似):
- Elasticsearch: 1-2 GB
- Grafana: 200 MB
- Prometheus: 500 MB
- Ollama: 4-8 GB (拉模型后)
- Nacos: 500 MB
- Nginx: 50 MB

---

## 跟前端/后端配置对接

中间件启动后, 改项目配置文件让服务连上:

### application.yml (后端)
```yaml
spring:
  data:
    elasticsearch:
      uris: http://localhost:9200
  redis:
    host: localhost
    port: 6379
```

### ai-platform-ai 配置
```yaml
aiplatform:
  ai:
    backend: ollama
    ollama:
      base-url: http://localhost:11434
```

### Prometheus scrape
自动 (在 prometheus.yml 已配 11 个服务)
