# 接口思维导图 (API Mindmap)

> 全景式接口分类, 60+ 接口按业务域分组, 一图看完整个 API surface.
>
> 配套:  [接口时序图](API-FLOW.md) / [架构图](ARCHITECTURE.md)

---

## 一、Mermaid 思维导图 (主脑图)

```mermaid
mindmap
  root((AI Agent Platform<br/>60+ 接口))
    Gateway 网关层
      22 路由 ::lb://ai-platform-* ::
      AuthGlobalFilter ::JWT 校验 + X-User-Id/X-Tenant-Id 透传 ::
      限流 ::30 QPS RequestRateLimiter ::
    Auth 认证
      POST /api/auth/login
      POST /api/auth/logout
      GET  /api/auth/audit/login ::登录审计 ::
      GET  /api/auth/health
    User 用户/角色/菜单/租户
      GET  /api/user/page
      POST /api/user
      PUT  /api/user
      DELETE /api/user/{id}
      角色 CRUD ::5 接口 ::
      菜单 CRUD ::5 接口 ::
      租户 CRUD ::5 接口 ::
    Model 模型管理
      POST /api/model
      GET  /api/model/page
      POST /api/model/{code}/new-version
      POST /api/model/{id}/activate
      导出 zip ::3 种格式 ::
        POST /api/model/export/{id}?format=onnx
        POST /api/model/export/{id}?format=gguf
        POST /api/model/export/{id}?format=pytorch
      下载 zip ::流式 application/zip ::
        GET /api/model/export/{id}/download
      元数据
        GET /api/model/export/{id}/manifest
        GET /api/model/export/{id}/formats
    Trainer 训练
      GET  /api/trainer/models
      POST /api/trainer/submit ::DB 持久化 ::
      GET  /api/trainer/job/{id} ::优先内存, 降级 DB ::
      GET  /api/trainer/jobs ::DB 历史 + 内存实时 ::
      PUT  /api/trainer/job/{id}/params ::热更超参 ::
      SSE 实时预览
        POST /api/trainer/preview/{id}/generate
        GET  /api/trainer/preview/{id}/subscribe ::Redis ring 持久化 ::
      训练健康
        GET /api/trainer/health
    Inference 推理
      GET  /api/inference/models
      POST /api/inference/generate ::DJL/PyTorch ::
      POST /api/chat/completions ::OpenAI 兼容 ::
      GET  /api/inference/health
    Knowledge 知识库
      POST /api/knowledge/documents
      GET  /api/knowledge/search
      POST /api/knowledge/pipeline
      GET  /api/knowledge/pipeline/nodes
    Agent 智能体
      CRUD
        POST /api/agent
        GET  /api/agent/list
        POST /api/agent/{id}/think
      ReAct 单步
        POST /api/agent/{id}/think
      工具调用
        POST /api/agent/tool/invoke
      对话
        POST /api/conversation/chat ::DB 持久化 invoke_log ::
        GET  /api/conversation/history?sessionId=
        GET  /api/conversation/invoke-logs?agentId=
      多 Agent 案例
        GET  /api/multi-agent-case/list
        POST /api/multi-agent-case/run
      联网搜索
        POST /api/agent/web-search ::DuckDuckGo, 无 API key ::
    Workflow 流程编排
      AI 极速生成 ::10 场景 ::
        GET  /api/workflow/ai-scenarios
        POST /api/workflow/ai-generate {input}
        POST /api/workflow/ai-modify {input, current}
      组件 schema
        GET  /api/workflow/component-schemas
        GET  /api/workflow/component-schemas/{nodeId}
        POST /api/workflow/component-schemas/{nodeId}/suggest ::AI 推荐 ::
      工作流定义 ::DB 持久化 ::
        POST /api/workflow/spec
        GET  /api/workflow/spec/list
        GET  /api/workflow/spec/{id}
        DELETE /api/workflow/spec/{id}
        POST /api/workflow/spec/{id}/duplicate
      运行 ::DB 持久化 run ::
        POST /api/workflow/run
        GET  /api/workflow/runs
        GET  /api/workflow/run/{id}
      单节点实时
        POST /api/workflow/exec
        POST /api/workflow/exec/batch
    Files 文件上传
      分片上传 ::Redis 持久化 ::
        POST /api/files/chunk/init
        PUT  /api/files/chunk/{id}?index=N
        POST /api/files/chunk/{id}/complete
        GET  /api/files/chunk/{id} ::断点续传 ::
      对象管理
        POST /api/files
        GET  /api/files/{id}
        DELETE /api/files/{id}
    Biz 业务全链路 ::10 表 ::
      CRUD ::每表 7 接口 ::
        /customer /chat /opportunity /quote /contract /order
        /payment /product /service /expense
      仪表盘
        GET /api/biz/dashboard
        GET /api/biz/{entity}/stats
    System 系统
      活动流
        GET /api/activity/stream ::SSE ::
      监控 ::9 服务 + 4 时序图 ::
        GET /api/monitor/snapshot
        GET /api/monitor/stream ::SSE ::
        GET /api/monitor/metrics
      分布式 7 大能力
        POST /api/distributed/lock ::Redis SETNX ::
        POST /api/distributed/snowflake
        POST /api/distributed/rate-limit ::令牌桶 ::
        POST /api/distributed/idempotency ::幂等键 ::
        GET  /api/distributed/cache/get
        POST /api/distributed/event/publish ::事件总线 ::
        GET  /api/distributed/scheduler/info
      分布式事务 Seata
        GET  /api/distributed-tx/config
        PUT  /api/distributed-tx/config
        POST /api/distributed-tx/demo/order ::3-ds AT ::
```

---

## 二、ASCII 树形脑图 (终端友好)

```
📡 AI Agent Platform ─── 60+ REST 接口 ────────────────────────────────────┐
│                                                                          │
├─ 🌐 Gateway (8081) ───────── 22 路由 ──────────────────────────────────┤
│  │ /api/auth → auth │ /api/user → user │ /api/role → user            │
│  │ /api/menu → user │ /api/biz → system │ /api/distributed → system   │
│  │ /api/monitor → system │ /api/model → model │ /api/dataset → model    │
│  │ /api/trainer → trainer │ /api/inference → inference                  │
│  │ /api/knowledge → knowledge │ /api/workflow → workflow                │
│  │ /api/agent → agent │ /api/conversation → agent                       │
│  │ /api/files → files │ /api/multi-agent-case → agent                   │
│  │ AuthGlobalFilter: JWT + 透传 X-User-Id/X-Username/X-Tenant-Id       │
│  │ RateLimiter: 30 QPS/秒                                              │
│                                                                          │
├─ 🔐 Auth (9002) ───────────── 4 接口 ────────────────────────────────────┤
│  │ POST  /api/auth/login            登录 (返回 JWT)                      │
│  │ POST  /api/auth/logout           登出                                 │
│  │ GET   /api/auth/audit/login      登录审计列表                         │
│  │ GET   /api/auth/health           健康检查                              │
│                                                                          │
├─ 👤 User (9001) ───────────── 20 接口 ───────────────────────────────────┤
│  │ User CRUD (4)        /api/user                                       │
│  │ Role CRUD (5)        /api/role                                       │
│  │ Menu CRUD (5)        /api/menu                                       │
│  │ Tenant CRUD (5)       /api/tenant                                    │
│  │ Dict (1)              /api/dict                                      │
│                                                                          │
├─ 🧠 Model (9003) ──────────── 13 接口 ───────────────────────────────────┤
│  │ POST   /api/model                  注册模型                            │
│  │ GET    /api/model/list             模型列表                             │
│  │ GET    /api/model/page             分页                                │
│  │ GET    /api/model/{id}             详情                                │
│  │ PUT    /api/model                  更新                                │
│  │ DELETE /api/model/{id}             删除                                │
│  │ GET    /api/model/versions/{code}  版本列表                            │
│  │ POST   /api/model/{code}/new-version  新版本                          │
│  │ POST   /api/model/{id}/activate    激活                                │
│  │ POST   /api/model/export/{id}      导出 (返回 zip + downloadUrl)       │
│  │ GET    /api/model/export/{id}/download  流式下载 zip                  │
│  │ GET    /api/model/export/{id}/formats  支持的格式列表                  │
│  │ GET    /api/model/export/{id}/manifest  manifest JSON                │
│                                                                          │
├─ 📊 Trainer (9004) ────────── 11 接口 ───────────────────────────────────┤
│  │ GET    /api/trainer/models          训练器列表                         │
│  │ POST   /api/trainer/submit          提交训练 (返回 jobId)              │
│  │ GET    /api/trainer/job/{id}        单个任务 (优先内存, 降级 DB)       │
│  │ GET    /api/trainer/jobs            任务列表 (DB 历史 + 内存实时)      │
│  │ PUT    /api/trainer/job/{id}/params 热更超参                          │
│  │ POST   /api/trainer/preview/{id}/generate  请求样本生成               │
│  │ GET    /api/trainer/preview/{id}/subscribe SSE 事件流                │
│  │ GET    /api/trainer/lora             LoRA 训练                        │
│  │ GET    /api/trainer/dpo             DPO 训练                          │
│  │ GET    /api/trainer/guard/presets   防幻觉预设                         │
│  │ GET    /api/trainer/health          健康检查                            │
│                                                                          │
├─ 🚀 Inference (9006) ──────── 4 接口 ────────────────────────────────────┤
│  │ GET    /api/inference/models        推理模型列表                       │
│  │ POST   /api/inference/generate      生成                                │
│  │ POST   /api/chat/completions         OpenAI 兼容对话                   │
│  │ GET    /api/inference/health         健康                                │
│                                                                          │
├─ 📚 Knowledge (9005) ─────── 12 接口 ────────────────────────────────────┤
│  │ POST   /api/knowledge/documents     上传文档                            │
│  │ GET    /api/knowledge/search        检索                                │
│  │ POST   /api/knowledge/qa            问答                                │
│  │ POST   /api/knowledge/pipeline      流水线保存                         │
│  │ GET    /api/knowledge/pipeline/list 流水线列表                        │
│  │ GET    /api/knowledge/pipeline/{id} 流水线详情                        │
│  │ GET    /api/knowledge/pipeline/nodes 节点类型                         │
│  │ GET    /api/knowledge/stats         统计                                │
│  │ GET    /api/knowledge/health        健康                                │
│                                                                          │
├─ 🤖 Agent (9007) ─────────── 13 接口 ───────────────────────────────────┤
│  │ CRUD                                                                 │
│  │   POST   /api/agent                 注册                                │
│  │   GET    /api/agent/list            列表                                │
│  │   GET    /api/agent/page            分页                                │
│  │   GET    /api/agent/{id}            详情                                │
│  │   PUT    /api/agent                 更新                                │
│  │   DELETE /api/agent/{id}            删除                                │
│  │ ReAct                                                                │
│  │   POST   /api/agent/{id}/think      单步思考                           │
│  │   POST   /api/agent/tool/invoke     工具调用                            │
│  │ 对话 (DB 持久化 invoke_log)                                          │
│  │   POST   /api/conversation/chat     完整对话                            │
│  │   GET    /api/conversation/history  历史                                │
│  │   GET    /api/conversation/invoke-logs 调用日志                       │
│  │ 多 Agent                                                              │
│  │   GET    /api/multi-agent-case/list                                  │
│  │   POST   /api/multi-agent-case/run                                   │
│  │ Web 搜索                                                              │
│  │   POST   /api/agent/web-search      DuckDuckGo                         │
│                                                                          │
├─ 🔀 Workflow (9008) ──────── 18 接口 ────────────────────────────────────┤
│  │ AI 极速生成                                                           │
│  │   GET    /api/workflow/ai-scenarios        9 个场景列表                 │
│  │   POST   /api/workflow/ai-generate         一句话 → 画布               │
│  │   POST   /api/workflow/ai-modify           多轮修改                     │
│  │ 组件 schema (后端可配, 不是前端 hardcode)                              │
│  │   GET    /api/workflow/component-schemas                            │
│  │   GET    /api/workflow/component-schemas/{nodeId}                   │
│  │   POST   /api/workflow/component-schemas/{nodeId}/suggest            │
│  │ 工作流定义 (DB 持久化 workflow_spec)                                  │
│  │   POST   /api/workflow/spec                                          │
│  │   GET    /api/workflow/spec/list                                     │
│  │   GET    /api/workflow/spec/{id}                                     │
│  │   DELETE /api/workflow/spec/{id}                                     │
│  │   POST   /api/workflow/spec/{id}/duplicate                          │
│  │ 运行 (DB 持久化 workflow_run)                                         │
│  │   POST   /api/workflow/run                                           │
│  │   GET    /api/workflow/runs                                          │
│  │   GET    /api/workflow/run/{id}                                      │
│  │ 单节点实时                                                            │
│  │   POST   /api/workflow/exec                                          │
│  │   POST   /api/workflow/exec/batch                                    │
│  │ 模板                                                                  │
│  │   GET    /api/workflow/templates/{name}                              │
│                                                                          │
├─ 📁 Files (9009) ─────────── 7+3 接口 ───────────────────────────────────┤
│  │ 对象 CRUD                                                             │
│  │   POST   /api/files                 上传                                │
│  │   GET    /api/files/{id}            下载                                │
│  │   DELETE /api/files/{id}            删除                                │
│  │ 分片上传 (Redis 持久化 chunk:session + chunk:received)               │
│  │   POST   /api/files/chunk/init      初始化 (返回 uploadId)             │
│  │   PUT    /api/files/chunk/{id}?index=N  上传分片                       │
│  │   POST   /api/files/chunk/{id}/complete  合并                          │
│  │   GET    /api/files/chunk/{id}      断点续传查已收 index              │
│  │ 流式上传                                                              │
│  │   POST   /api/files/streaming-upload                                 │
│                                                                          │
├─ 📊 System (9010) ────────── 业务 70 + 分布式 14 + 监控 3 ─────────────┤
│  │ 业务全链路 (10 表 × 7 接口 = 70)                                      │
│  │   /api/biz/customer/{page,list,id,POST,PUT,DELETE,stats}             │
│  │   /api/biz/chat/{...}                                                 │
│  │   /api/biz/opportunity/{...}                                         │
│  │   /api/biz/quote/{...}                                               │
│  │   /api/biz/contract/{...}                                            │
│  │   /api/biz/order/{...}                                               │
│  │   /api/biz/payment/{...}                                             │
│  │   /api/biz/product/{...}                                             │
│  │   /api/biz/service/{...}                                             │
│  │   /api/biz/expense/{...}                                             │
│  │   /api/biz/dashboard       总览                                       │
│  │ 分布式 7 大能力 (Redis + Redisson)                                    │
│  │   POST   /api/distributed/lock        锁                                │
│  │   POST   /api/distributed/snowflake   ID                                │
│  │   POST   /api/distributed/rate-limit  限流                              │
│  │   POST   /api/distributed/idempotency 幂等                              │
│  │   GET    /api/distributed/cache/get   缓存                              │
│  │   POST   /api/distributed/event/publish 事件                            │
│  │   GET    /api/distributed/scheduler/info 调度                          │
│  │   GET    /api/distributed/health     健康                              │
│  │ Seata 分布式事务                                                       │
│  │   GET    /api/distributed-tx/config  配置                              │
│  │   PUT    /api/distributed-tx/config  更新                              │
│  │   POST   /api/distributed-tx/demo/order 演示                           │
│  │ 监控 (9 服务 + 4 时序图)                                              │
│  │   GET    /api/monitor/snapshot        快照                              │
│  │   GET    /api/monitor/stream          SSE                              │
│  │   GET    /api/monitor/metrics         时序                              │
│  │ 活动流                                                                 │
│  │   GET    /api/activity/stream         SSE                              │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 三、按 HTTP 方法分布

```
GET     ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 60+ (60%)
POST    ━━━━━━━━━━━━━━━━━━━━━━ 35 (35%)
PUT     ━━━━━━━ 5 (4%)
DELETE  ━━━━━━━ 4 (3%)
SSE     ━━━━ 4 (text/event-stream)
─────────────────────────────────────────────
合计    接口    ~110
```

---

## 四、按持久化策略分布

```
内存 (短/临时) ────────────────── 9 接口
  ChunkSession.putChunk          ConcurrentHashMap 暂存分片
  PreviewBus.subscribe           ConcurrentHashMap ring (重连补)
  WorkflowRun 实时状态           ConcurrentHashMap 实时 run

MySQL (持久化) ────────────────── 60+ 接口
  workflow_spec / workflow_run   DB 持久化 (重启不丢)         [改]
  model_train_job                DB 持久化 (重启不丢)         [改]
  agent_invoke_log               DB 持久化 (重启不丢)         [改]
  业务全链路 10 表                DB 持久化
  sys_user / role / menu / tenant DB 持久化
  file_object                    DB 持久化

Redis (缓存/分布式) ──────────────── 15 接口
  chunk:session / chunk:received  Redis Hash + Set            [改]
  preview:ring                   Redis List                   [改]
  distributed:lock               Redis SETNX
  rate-limit:bucket              Redis token bucket
  idem:key                       Redis SETNX + TTL
  cache:key                      Redis String + TTL
  event:bus:*                    Redis Pub/Sub
  scheduler:leader               Redis SET NX EX

ES (检索) ─────────────────────────── 2 接口
  knowledge.search               ES 8 全文 + 向量
  knowledge.documents            Tika 解析
```

---

## 五、按 OpenAPI Tags (给前端看的)

```
auth         4
user         5
role         5
menu         5
tenant       5
model        8
trainer      11
inference    4
knowledge    9
agent        8
workflow     18
files        10
biz          70  (10 entity × 7 接口)
system       4   (monitor + activity)
distributed  7   (7 大能力)
total        ~110+ 接口, 9 大模块
```

---

## 六、OpenAPI 3.0 Spec (片段)

```yaml
openapi: 3.0.3
info:
  title: AI Agent Platform
  version: 2.0.0
servers:
  - url: http://localhost:8081
    description: Gateway
tags:
  - {name: auth}
  - {name: model}
  - {name: trainer}
  - {name: workflow}
  - {name: agent}
  - {name: biz}
  - {name: distributed}
paths:
  /api/auth/login:
    post:
      tags: [auth]
      summary: 登录
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/LoginRequest'
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Result'
  /api/trainer/submit:
    post:
      tags: [trainer]
      summary: 提交训练
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                trainerId:  {type: string}
                corpusPath: {type: string}
                params:
                  type: object
                  additionalProperties: true
      responses:
        '200':
          content:
            application/json:
              schema:
                type: object
                properties:
                  code:    {type: integer}
                  message: {type: string}
                  data:
                    type: object
                    properties:
                      jobId:  {type: string}
                      status: {type: string}
                      progress: {type: integer}
  /api/workflow/ai-generate:
    post:
      tags: [workflow]
      summary: 一句话生成工作流
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                input: {type: string, description: '自然语言描述'}
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/WorkflowSpec'
components:
  schemas:
    Result:
      type: object
      properties:
        code:    {type: integer, example: 200}
        message: {type: string}
        data:    {}
        timestamp: {type: integer}
    WorkflowSpec:
      type: object
      properties:
        name:  {type: string}
        description: {type: string}
        nodes:
          type: array
          items: {$ref: '#/components/schemas/Node'}
        edges:
          type: array
          items: {$ref: '#/components/schemas/Edge'}
    Node:
      type: object
      properties:
        id:     {type: string}
        type:   {type: string, description: '32 节点类型之一'}
        name:   {type: string}
        x:      {type: number}
        y:      {type: number}
        params: {type: object, additionalProperties: true}
    Edge:
      type: object
      properties:
        from:     {type: string}
        to:       {type: string}
        fromPort: {type: string, enum: [in, out]}
        toPort:   {type: string, enum: [in, out]}
```

---

## 七、调用频率与重要性

```
🔥 高频 (每秒)              ⚡ 中频 (每分钟)          📦 低频 (每天)
─────────────────         ─────────────────         ─────────────────
POST /api/auth/login       POST /api/workflow/exec   GET /api/monitor/snapshot
POST /api/conversation/chat POST /api/inference/generate POST /api/distributed-tx/...
GET  /api/workflow/runs    PUT /api/distributed/lock  GET /api/model/export/...
POST /api/distributed/...  POST /api/trainer/preview/... POST /api/workflow/ai-generate
                           GET  /api/knowledge/search (异步, 用户触发)
```

---

## 八、接口全景统计

| 维度 | 数量 |
|---|---|
| 总接口数 | **~110** |
| 微服务数 | 9 (含 gateway) |
| 二级菜单 | 5 大组 (数据准备 / 模型管理 / 训练 / 推理 / 流程编排 / Agent / 业务 / 系统 / 分布式) |
| 数据库表 | 32 (00_init_all.sql) |
| 单元测试 | **104 个 PASS** |
| e2e 贯通测试 | **11/11 PASS** (frontend/e2e_full_chain.cjs) |
| Swagger UI | `/swagger-ui.html` (gateway 8081) |
| Knife4j UI | `/doc.html` (gateway 8081) |

---

## 九、文件位置

```
docs/
├── API-FLOW.md              接口时序图 (5 个 Mermaid sequenceDiagram)
├── API-MINDMAP.md           本文档 (接口思维导图)
├── ARCHITECTURE.md          架构图
└── FUNCTIONAL-MANUAL.md     功能手册

backend/
└── ai-platform-gateway/src/main/resources/application.yml  # 22 路由
```

---

*最后更新: 2026-06-17 — 跟 README 一致*