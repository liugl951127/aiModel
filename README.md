# AI Agent Platform

> **基于 Spring Cloud Alibaba + JDK 17 的多租户分布式大模型智能体平台。**
> 自带大模型训练 → 导出 ONNX bundle → Java 推理层加载 → 智能体 ReAct 编排 → 知识库 RAG。
> 企业级 Maven 多模块结构，开箱即用，每个微服务可独立运行，**不强制依赖 Nacos**。

---

## 🆕 本周更新 (Latest Updates)

| 提交 | 能力 |
|---|---|
| `964ad08` | **AgentInvokeLog DB 化** — 每次 /api/conversation/chat 写入 agent_invoke_log 表 (重启不丢) |
| `8668acf` | **端到端贯通 e2e 脚本** — 跨 5 大模块 11 个接口, 11/11 PASS (脚本在 `frontend/e2e_full_chain.cjs`) |
| `9469725` | **PreviewBus SSE 事件 Redis 化** — SSE late subscriber 重连后还能看到历史 (动态代理免引依赖) |
| `9a312ad` | **ChunkUpload 会话 Redis 化** — 分片上传断点续传真可用 (重启服务不丢会话) |
| `f21a357` | **异常节点客户级提示** — 配置弹窗顶部 Banner + 必填项红框 |
| `4e5d164` | **WorkflowRun DB 化** — 工作流运行历史写入 workflow_run 表 |
| `f2a727d` | **TrainJob DB 化** — 训练任务写入 model_train_job 表 (重启服务不丢训练记录) |
| `cb32f4f` | **红色箭头 + 跨行正交折线** — 边颜色可点变橙 / 跨行L型转弯 |
| `fb4f16e` | **5 个二级菜单贯通** — 任意页面跳到下一步 (附 query 参数) |

**真 e2e 验证贯通** (1 分钟跑完 11 步):
```bash
python3 backend/mock_ai_server.py 9999 &
node frontend/e2e_full_chain.cjs http://127.0.0.1:9999
```

---

## 一、能力一览

| 领域 | 能力 |
| --- | --- |
| **微服务架构** | Spring Cloud Gateway + Nacos 注册/配置（可选）+ OpenFeign + LoadBalancer |
| **多租户** | MyBatis-Plus 行级 `tenant_id` 拦截器 + 上下文传递 + 数据隔离 |
| **认证授权** | 自研 JWT (HS256) + BCrypt 密码 + 网关统一鉴权 + 透传 headers |
| **大模型** | 自实现字符级 Transformer 训练（NumPy only）/ 训练→导出→Java 推理全链路 |
| **导出** | ONNX bundle（manifest + weights + tokenizer）→ 本地开箱即用 |
| **智能体** | ReAct 引擎 + Spring 自动发现的工具注册中心 + 短期/长期记忆 |
| **联网搜索** | `WebSearchTool` 基于 DuckDuckGo Instant Answer（无需 API Key），ReAct 循环中以 `web_search(query=…)` 调用 |
| **知识库** | Elasticsearch 8 + Tika 文档解析 + RAG（带回退索引） |
| **Web** | Vue 3 + Element Plus + Vite + Pinia（11 页面） |
| **部署** | 一键 `docker compose up`（Nacos + MySQL + Redis + ES + 8 服务 + Nginx） |
| **可观测** | Spring Boot Actuator + Knife4j API 文档 |
| **事务** | 本地 `@Transactional` (Spring TX) -- 未引入 Seata, 依赖下号 |

---

## 二、企业级 Maven 结构

```
backend/
├── pom.xml                                  # 父 POM + Aliyun mirror + 集中版本
├── ai-platform-dependencies/                # (BOM 预留)
├── ai-platform-api/                         # 共享 DTO/Feign
├── ai-platform-common/                      # 纯 Java 工具: Result, JWT, 多租户上下文
├── ai-platform-starters/                    # 可复用 starter (Spring Boot Starter 模式)
│   ├── ai-platform-web-starter/             # Web + 全局异常 + CORS + 多租户拦截器
│   ├── ai-platform-mybatis-starter/         # MP + 多租户 SQL + 自动填充
│   ├── ai-platform-redis-starter/           # Redis 工具
│   ├── ai-platform-secure-starter/          # JWT 工具
│   └── ai-platform-nacos-starter/           # Nacos (可选装配, 默认关闭)
├── ai-platform-gateway/                     # API 网关
├── ai-platform-auth/                        # 登录 / 刷新 Token
├── ai-platform-user/                        # 用户 + 租户
├── ai-platform-system/                      # 角色 / 菜单
├── ai-platform-model/                       # 模型注册 + 训练任务 + 导出
├── ai-platform-agent/                       # 智能体 + 工具 + ReAct 引擎 + 记忆
├── ai-platform-knowledge/                   # 知识库 + ES + Tika
└── ai-platform-inference/                   # ONNX 推理层 (Java)
```

**关键设计**：
- `common` 只依赖 JJWT + fastjson2 + hutool-core，**没有任何 Spring 运行时**
- 每个 starter 是标准 Spring Boot starter：通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册
- 业务服务**只引用 starter**，不直接引用底层库
- 父 POM 集中所有版本，Aliyun mirror 优先 + Central 兜底
- **Nacos 真正可选**：yaml `nacos.config.import-check.enabled=false`，starter 用 `@ConditionalOnProperty` 装配
- 所有 bean 注入用 `@Resource` / `@Autowired`，**非 static 字段不加 final**（构造器注入除外）

---

## 三、独立运行验证

**关键不变量**：每个服务都能在没有 Nacos / 完整中间件链的情况下启动到至少 Spring 上下文阶段。

```bash
# 1. 全部模块编译
cd backend && mvn clean install -DskipTests
# → BUILD SUCCESS (16 个模块)

# 2. 全部单元测试 + SpringBootTest
mvn test
# → Tests run: 8, Failures: 0, Errors: 0
#   - common: 6 个纯 Java 测试
#   - user:   2 个 SpringBootTest (H2 内存库)

# 3. inference 服务: 无 Nacos / 无 DB / 无 Redis, 真正独立启动
java -jar ai-platform-inference/target/ai-platform-inference.jar
# → Started InferenceApplication in 10.34 seconds
curl http://localhost:9007/api/inference/models
# → {"code":200,"data":{"default":"loaded"}}
curl -X POST http://localhost:9007/api/inference/generate \
  -H 'Content-Type: application/json' \
  -d '{"modelCode":"default","prompt":"hi","maxTokens":5}'
# → {"code":200,"data":{"text":"hi...","elapsedMs":92}}
```

**生产环境开启 Nacos**：设环境变量 `NACOS_DISCOVERY_ENABLED=true NACOS_CONFIG_ENABLED=true NACOS_SERVER=ip:8848`

---

## 四、快速开始

### 方式 A: 一键 Docker 编排

```bash
cd deploy/docker
docker compose up -d
# 浏览器 http://localhost:8080
# 默认账号: admin / admin123 / 租户 1
```

### 方式 B: 本地开发模式

```bash
# 1) 中间件
docker run -d --name ai-mysql -p 3306:3306 \
    -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=ai_platform mysql:8.0
docker run -d --name ai-redis -p 6379:6379 redis:7-alpine
docker run -d --name ai-nacos -p 8848:8848 -e MODE=standalone nacos/nacos-server:v2.3.1
docker run -d --name ai-es -p 9200:9200 \
    -e discovery.type=single-node -e xpack.security.enabled=false \
    docker.elastic.co/elasticsearch/elasticsearch:8.13.0

# 2) 初始化
mysql -h127.0.0.1 -uroot -p < deploy/sql/01_schema.sql
mysql -h127.0.0.1 -uroot -p < deploy/sql/02_seed.sql

# 3) 后端 (任意顺序, 可用 IDE 启动)
cd backend
mvn install -N
mvn -pl ai-platform-common -am install -DskipTests
mvn -pl ai-platform-starters -am install -DskipTests
mvn -pl ai-platform-gateway spring-boot:run &
# ... 其他服务同理

# 4) AI 模型
cd ../ai-model
python3 -m src.training.train --data data/sample_corpus.txt --max-iters 1500
python3 -m src.export.export_onnx --in checkpoints/mini_gpt.npz --out exports/mini_gpt.bundle
sudo mkdir -p /opt/ai-platform/inference-bundles
sudo cp -r exports/mini_gpt.bundle /opt/ai-platform/inference-bundles/default

# 5) 前端
cd ../frontend
npm install
npm run dev   # http://localhost:5173
```

---

## 五、关键设计

### 5.1 Bean 注入规范

✅ 推荐：
```java
@Service
public class UserService {
    @Resource
    private UserMapper userMapper;
}

@Component
public class TenantInterceptor {
    @Autowired
    private JwtUtils jwtUtils;
}
```

❌ 禁止（早期版本，已修正）：
```java
@Service
@RequiredArgsConstructor  // 产生 final 字段
public class UserService {
    private final UserMapper userMapper;  // final + 构造器注入在某些场景下导致 bean 循环
}
```

### 5.2 Starter 模式

每个 starter：
1. 提供 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
2. 通过 `@ConditionalOnClass` / `@ConditionalOnProperty` 控制激活
3. 不强制业务服务声明 Nacos

### 5.3 多租户 SQL

`MybatisPlusTenantHandler` 给每条 SQL 自动拼 `tenant_id = ?`：
```java
public Expression getTenantId() { return new LongValue(TenantContext.getTenantId()); }
public String getTenantIdColumn() { return "tenant_id"; }
public boolean ignoreTable(String name) { return IGNORE_TABLES.contains(name.toLowerCase()); }
```

### 5.4 模型 bundle

- 训练侧 (Python) 导出 4 个文件：`config.json / weights.json / tokenizer.json / manifest.json`
- 推理侧 (Java) 用 fastjson 解析，**无需 PyTorch / ONNX Runtime**（已留好 classpath 给真实 ONNX）

### 5.4 智能体联网搜索

智能体在 ReAct 循环中可通过 `web_search(query="…")` 调用联网搜索。

| 项 | 说明 |
| --- | --- |
| 工具名 | `web_search` |
| 实现 | `com.aiplatform.agent.tool.builtin.WebSearchTool` |
| 后端 | DuckDuckGo Instant Answer (`https://api.duckduckgo.com/?q=…&format=json`)，**无需 API Key** |
| 协议 | JDK `HttpURLConnection`（不增加额外 http 客户端依赖） |
| 超时 | 默认 4s，可配 |
| 多 Agent 示例 | `agent_multi_agent_case` 中 `launch-campaign-2025` 的 research 步同时调 `web_search + kbs` |

**配置**（`ai-platform-agent/src/main/resources/application.yml`）：

```yaml
aiplatform:
  agent:
    tools:
      websearch:
        enabled: true
        endpoint: https://api.duckduckgo.com/
        max-results: 5
        timeout-ms: 4000
```

**ReAct 提示词注入**：`ToolRegistry.init()` 在启动时自动扫描所有 `AgentTool` bean 并
打印 `[TOOL] registered N tools: [web_search, knowledge_search, calculator, time] …`。
引擎会把这些工具的 `name + description + parametersSchema` 拼到系统提示词里。

**调用示例**（伪对话）：

```
User:    帮我想一句 2025 年营销文案，主题是 AI 助手
Thought: 本地知识可能不够，先联网
Action:  web_search(query="2025 AI 助手 营销文案 案例")
Obs:     《2025 年中国 AI 营销趋势报告》指出：① 场景化落地 ② 人机协同 ……
Thought: 信息足够
Action:  Final Answer: 在 2025 年，AI 助手进入“场景化、人机协同”阶段 …
```

**API**（多 Agent 演示服务）：

```bash
curl http://localhost:9004/api/agent/cases/list
# → {"code":200,"data":[{"caseKey":"launch-campaign-2025","title":"2025 营销文案多 Agent 调研", …}]}

curl -X POST http://localhost:9004/api/agent/cases/{caseKey}/run
# → 走完整 Plan-Act-Observe 循环，会调 web_search + kbs
```

**生产可换**：把 `endpoint` 指向自有搜索服务（如 Elasticsearch + crawl4j），
其它代码无需改。

---

### 5.5 事务（本项目未引入分布式事务）

本项目未引入分布式事务 (Seata 等)，全部走 Spring 本地事务 `@Transactional`：

| 项 | 说明 |
| --- | --- |
| 事务方案 | Spring `@Transactional` 本地事务 |
| 适用场景 | 单库事务足够覆盖 99% 业务场景（订单/支付/库存扣减/积分等） |
| 跨服务 | 本项目业务设计避免跨服务分布式写，设计上走本地事务 + 事件最终一致 |

如未来需引入 Seata 分布式事务，可重新加 `seata-spring-boot-starter` + TC 协调器。当前架构设计为可插拔。

---

## 六、端口

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

## 七、License

Apache 2.0
