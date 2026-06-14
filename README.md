# AI Agent Platform

> 一个基于 **Spring Cloud Alibaba + JDK 17** 的多租户分布式 **大模型智能体平台**。
> 从模型训练、导出、推理，到智能体编排、知识库 RAG，再到统一 Web 控制台，**全部开源、独立运行、开箱即用**。

---

## 能力一览

| 领域 | 能力 |
| --- | --- |
| **微服务架构** | Spring Cloud Gateway + Nacos 注册/配置中心 + OpenFeign + LoadBalancer |
| **多租户** | MyBatis-Plus 行级 `tenant_id` 拦截器 + 上下文传递 + 数据隔离 |
| **认证授权** | 自研 JWT (HS256) + BCrypt 密码 + 网关统一鉴权 |
| **大模型** | 自实现字符级 Transformer（Mini-GPT）训练 / 导出 / ONNX 风格 bundle 推理 |
| **导出** | 模型 → 自包含 ONNX bundle（manifest + weights + tokenizer）→ 本地开箱即用 |
| **智能体** | ReAct 引擎 + Spring 自动发现的工具注册中心 + 短期/长期记忆 |
| **知识库** | Elasticsearch 8 + Tika 文档解析 + RAG（带回退索引） |
| **Web** | Vue 3 + Element Plus + Vite + Pinia（已构建 11 个页面） |
| **可观测** | Spring Boot Actuator + Knife4j API 文档 |
| **部署** | 一键 `docker compose up`（Nacos + MySQL + Redis + ES + 8 个微服务 + Nginx） |

---

## 目录结构

```
ai-agent-platform/
├── backend/                       # Spring Cloud 多模块 Maven 工程
│   ├── pom.xml                    # 父 POM（BOM + 依赖管理）
│   ├── ai-platform-common/        # 公共：响应 / 异常 / JWT / 多租户 / 工具
│   ├── ai-platform-gateway/       # API 网关 + 鉴权
│   ├── ai-platform-auth/          # 登录 / 刷新 Token
│   ├── ai-platform-user/          # 用户 + 租户管理
│   ├── ai-platform-system/        # 角色 / 菜单
│   ├── ai-platform-model/         # 模型注册 + 训练任务 + 导出
│   ├── ai-platform-agent/         # 智能体 + 工具 + ReAct 引擎 + 记忆
│   ├── ai-platform-knowledge/     # 知识库 + ES + Tika
│   └── ai-platform-inference/     # ONNX 推理层（自实现 + ONNX Runtime 依赖）
├── ai-model/                      # Python 大模型（自实现 Mini-GPT）
│   ├── src/
│   │   ├── model/mini_gpt.py      # 字符级 Transformer 实现
│   │   ├── training/train.py      # 训练入口
│   │   ├── export/export_onnx.py  # 导出为 ONNX 风格 bundle
│   │   └── serving/serve.py       # 本地 HTTP 推理服务（Python 端）
│   ├── data/sample_corpus.txt     # 示例语料
│   ├── requirements.txt
│   ├── checkpoints/               # 训练后 .npz
│   └── exports/                   # 导出后 .bundle
├── frontend/                      # Vue 3 + Element Plus
│   ├── src/
│   │   ├── api/                   # 后端 API 客户端
│   │   ├── views/                 # 11 个业务页面
│   │   ├── layouts/MainLayout.vue
│   │   ├── router/
│   │   ├── store/
│   │   └── utils/request.js
│   ├── package.json
│   ├── vite.config.js
│   └── dist/                      # 构建产物（npm run build）
├── deploy/                        # 部署
│   ├── docker/docker-compose.yml  # 一键编排
│   ├── docker/Dockerfile.*        # 每个服务的镜像
│   ├── nginx/nginx.conf           # 反代
│   └── sql/                       # 01_schema.sql + 02_seed.sql
└── docs/                          # 设计与运行文档
```

---

## 快速开始（两种方式）

### 方式 A：一键 Docker 编排

```bash
# 1. 准备
cd deploy/docker
docker compose up -d

# 2. 等待所有服务健康（30~60s）
# 浏览器打开
#   Web 控制台:    http://localhost:8080
#   API 网关:      http://localhost:9000
#   Nacos 控制台:  http://localhost:8848/nacos  (nacos/nacos)
#   Swagger:       http://localhost:9000/doc.html

# 默认账号
#   用户名: admin
#   密码:   admin123
#   租户:   1
```

### 方式 B：本地开发模式

**后端**
```bash
# 启动中间件（任选其一）
docker run -d --name ai-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root \
    -e MYSQL_DATABASE=ai_platform mysql:8.0
docker run -d --name ai-redis -p 6379:6379 redis:7-alpine
docker run -d --name ai-nacos -p 8848:8848 -p 9848:9848 \
    -e MODE=standalone nacos/nacos-server:v2.3.1
docker run -d --name ai-es -p 9200:9200 \
    -e discovery.type=single-node -e xpack.security.enabled=false \
    docker.elastic.co/elasticsearch/elasticsearch:8.13.0

# 初始化数据库
mysql -h127.0.0.1 -uroot -p < deploy/sql/01_schema.sql
mysql -h127.0.0.1 -uroot -p < deploy/sql/02_seed.sql

# 编译并启动各服务
cd backend
mvn -DskipTests install -pl ai-platform-common -am
mvn -DskipTests spring-boot:run -pl ai-platform-gateway &
mvn -DskipTests spring-boot:run -pl ai-platform-auth &
# ... 同理启动 user / system / model / agent / knowledge / inference
```

**AI 模型**
```bash
cd ai-model
# 仅训练
python3 -m src.training.train --data data/sample_corpus.txt \
    --out checkpoints/mini_gpt.npz --max-iters 1500
# 导出 ONNX bundle
python3 -m src.export.export_onnx --in checkpoints/mini_gpt.npz \
    --out exports/mini_gpt.bundle
# 拷到 Java 端默认加载目录
mkdir -p /opt/ai-platform/inference-bundles
cp -r exports/mini_gpt.bundle /opt/ai-platform/inference-bundles/default
```

**前端**
```bash
cd frontend
npm install
npm run dev   # http://localhost:5173
# 或
npm run build && nginx -c ../deploy/nginx/nginx.conf
```

---

## 验证：从零跑通

```bash
# 1. Java 单测（不需要任何外部依赖）
cd backend
mvn -pl ai-platform-common test
# → Tests run: 5, Failures: 0, Errors: 0, Skipped: 0

# 2. Python 端到端
cd ../ai-model
python3 -m src.training.train --data data/sample_corpus.txt --max-iters 50
python3 -m src.export.export_onnx --in checkpoints/mini_gpt.npz --out exports/mini_gpt.bundle
# 输出：exports/mini_gpt.bundle/{config.json, weights.json, tokenizer.json, manifest.json, README.md}

# 3. Java 端加载并推理
mkdir -p /opt/ai-platform/inference-bundles
cp -r exports/mini_gpt.bundle /opt/ai-platform/inference-bundles/default
mvn -pl ai-platform-inference package -DskipTests
java -jar ai-platform-inference/target/ai-platform-inference.jar &
curl http://localhost:9007/api/inference/models
# → {"code":200,"data":{"default":"loaded"}}
curl -X POST http://localhost:9007/api/inference/generate \
  -H 'Content-Type: application/json' \
  -d '{"modelCode":"default","prompt":"hi","maxTokens":15,"temperature":0.8}'
# → {"code":200,"data":{"text":"...","elapsedMs":646}}
```

---

## 关键设计

### 多租户隔离
* `MybatisPlusTenantHandler` 给每条 SQL 自动拼接 `tenant_id = ?`（白名单表跳过）
* `TenantContext`（ThreadLocal）+ `TenantInterceptor` 解析 `X-Tenant-Id` 头 / JWT claim
* `MetaObjectHandler` 自动填充 `tenant_id / create_by / create_time / deleted`

### ReAct 智能体
1. 拼 prompt：system + tools 描述 + 历史 + 用户输入
2. 调 `inference.generate` 拿 LLM 输出
3. 解析 JSON `{action, args, answer}`
4. 若 `action == final` → 结束；否则查找 `ToolRegistry`，执行，append observation，循环
5. `maxSteps` 兜底

### 模型导出 → 推理
* **导出格式**：自包含 JSON bundle，base64 编码 float32 张量 + 字节级 tokenizer
* **推理路径**：Java `MiniGptModel` 用 fastjson 解析 → 纯 Java 实现 Transformer forward → softmax 采样
* **真正 ONNX 兼容**：`onnxruntime-1.17.1` 已在 classpath，可直接 `.onnx` 文件，未来扩展

### 网关 + 鉴权
* 所有非白名单请求 → `AuthGlobalFilter` 验证 JWT → 把 `X-User-Id / X-Tenant-Id / X-Username` 注入下游 header
* 下游服务零鉴权代码，专注业务

---

## 默认账号

| 字段 | 值 |
| --- | --- |
| 租户编码 | `default` |
| 租户 ID | `1` |
| 用户名 | `admin` |
| 密码 | `admin123` |

---

## 端口分配

| 服务 | 端口 |
| --- | --- |
| Web (Nginx) | 8080 |
| Gateway | 9000 |
| Auth | 9001 |
| User | 9002 |
| System | 9003 |
| Model | 9004 |
| Agent | 9005 |
| Knowledge | 9006 |
| Inference | 9007 |
| MySQL | 3306 |
| Redis | 6379 |
| Elasticsearch | 9200 |
| Nacos | 8848 |

---

## 性能 / 限制

* **Mini-GPT** 字符级 4 层 128 维，~1M 参数，CPU 上 1 步推理 < 1s
* **生产化路径**：用 PyTorch 训练 → 导出真实 ONNX → 用 onnxruntime 推理（已留好）
* **多租户性能**：MyBatis-Plus 行级过滤 + 索引 `idx_tenant_id` 即可线性扩展

---

## License

Apache 2.0
