# 架构设计

## 1. 总体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        Browser (Vue 3 SPA)                        │
│                    Element Plus + Pinia + Vite                    │
└───────────────┬──────────────────────────────────────────────────┘
                │  HTTP (Nginx :8080)
                ▼
┌──────────────────────────────────────────────────────────────────┐
│                  Spring Cloud Gateway :9000                       │
│            AuthGlobalFilter (JWT 验签 + 注入 headers)              │
│            路由分发 (lb://ai-platform-*)                           │
└───────────────┬──────────────────────────────────────────────────┘
                │
   ┌────────────┼────────────┬─────────────┬──────────────┬────────────┐
   ▼            ▼            ▼             ▼              ▼            ▼
 Auth         User        System        Model           Agent      Knowledge   Inference
 :9001        :9002       :9003         :9004           :9005      :9006       :9007
   │            │            │             │              │            │            │
   └────────────┴────────────┴─────────────┴──────────────┴────────────┴────────────┘
                                │
                ┌───────────────┼───────────────┬──────────────┐
                ▼               ▼               ▼              ▼
            MySQL            Redis            ES          Nacos
          :3306  multi      :6379  cache    :9200 RAG     :8848 registry
         (sys_*, model_*,   short-term      +fallback     config
          agent_*, kb_*)    memory          inverted
                          refresh tok       index

                       ┌────────────────────────┐
                       │  ai-model (Python)     │
                       │  Mini-GPT train/       │
                       │  export ONNX bundle    │
                       └────────┬───────────────┘
                                │  /opt/ai-platform/inference-bundles/default
                                ▼
                        ai-platform-inference
                       (Java ONNX Runtime + 自实现 forward)
```

## 2. 模块依赖图

```
                ┌──────────────┐
                │ ai-platform- │
                │   common     │  (Result, JWT, 多租户, 工具, 自动装配)
                └──────┬───────┘
                       │
       ┌───────────────┼────────────────┬───────────────────┐
       │               │                │                   │
       ▼               ▼                ▼                   ▼
   gateway          auth              user               system
                       │
                       └─ Feign ─> user (FeignClient)
                       
       ┌───────────────┼────────────────┬───────────────────┐
       │               │                │                   │
       ▼               ▼                ▼                   ▼
    model            agent           knowledge          inference
       │               │                │
       │               └─ Feign ─> knowledge, inference
       │
       └─ Java process call Python training (CLI subprocess, future)
```

## 3. 数据流：用户一次 Chat 请求

```
1. 前端   POST /api/conversation/chat  {agentId, input}
           ↓
2. 网关   验证 JWT  → 注入 X-User-Id / X-Tenant-Id
           ↓
3. agent  AgentOrchestrator.chat(agentId, sessionId, input)
           ├── ConversationService.open() or 复用
           ├── 持久化 user message
           └── ReActEngine.run(agent, session, input)
                 ├─ 拼 prompt (system + tools + memory)
                 ├─ Feign → inference.generate
                 ├─ 解析 {action, args, answer} (JSON)
                 ├─ 若 action == final → 结束
                 ├─ 否则 ToolRegistry.get(action).execute(args)
                 ├─ 持久化 assistant + tool message
                 └─ 循环 (上限 = agent.maxSteps)
           ↓
4. 前端   渲染 answer + 工具 trace
```

## 4. 数据流：模型训练 + 导出

```
1. 用户   POST /api/train/submit  {modelId, datasetId, epochs, ...}
           ↓
2. model  TrainJobService.submit()
           ├── INSERT train_job (status=queued, progress=0)
           ├── modelRegistryService.updateStatus(modelId, "training")
           └── @Async runAsync(jobId)
                 ├─ status=running, started_at=now
                 ├─ 循环 epoch (模拟训练，可替换为 subprocess.run('python train.py'))
                 └─ 完成后 status=succeeded, model.status=ready
           ↓
3. 用户   POST /api/model/export/{modelId}
           ↓
4. model  ModelExportService.exportBundle(modelId)
           ├── 校验 model.status == ready
           ├── mkdir -p /opt/ai-platform/exports/
           └── 打 zip: manifest.json + tokenizer/config.json + README.md + model.onnx
           ↓
5. 用户   下载 zip → 本地解压 → 跑 serve.py 或 java -jar
```

## 5. 关键技术点

### 5.1 多租户 SQL 拦截

`MybatisPlusTenantHandler`：
```java
Expression getTenantId() { return new LongValue(TenantContext.getTenantId()); }
String getTenantIdColumn() { return "tenant_id"; }
boolean ignoreTable(String name) { /* sys_user / sys_tenant / 关联表 跳过 */ }
```

注册为 MyBatis 插件：
```java
new MybatisPlusInterceptor().addInnerInterceptor(new TenantLineInnerInterceptor(handler));
```

效果：业务代码写 `selectList(null)`，SQL 自动变为 `WHERE tenant_id = ?`，白名单表不受影响。

### 5.2 ReAct 循环

```java
for (int step = 1; step <= maxSteps; step++) {
    String prompt = buildPrompt(agent, userInput, trace, tools);
    String llmOutput = inference.generate(prompt);
    JSONObject parsed = parseAction(llmOutput);
    if (parsed.action.equals("final")) return parsed.answer;
    AgentTool tool = registry.get(parsed.action);
    String observation = tool.execute(parsed.args);
    trace.add(new Message(tool=..., content=observation));
}
```

### 5.3 自包含 ONNX Bundle

训练侧 Python 导出 4 个文件：
- `config.json` — 超参（vocab_size / block_size / n_embd / n_layer / n_head / head_dim）
- `weights.json` — 张量字典，base64 编码 float32
- `tokenizer.json` — 字节级 tokenizer
- `manifest.json` — 文件清单
- `README.md` — 快速启动

Java 侧读取：
```java
MiniGptModel m = MiniGptModel.loadFromDirectory(Paths.get("exports/mini_gpt.bundle"));
String text = m.generate("你好", 50, 0.8f);
```

**优势**：bundle 完全跨语言、跨运行时、零外部依赖（除了 fastjson），可以直接放 U 盘里发人。

## 6. 性能优化（生产建议）

* **多租户索引**：所有表 `KEY idx_tenant (tenant_id, ...)`
* **模型推理**：Java forward 当前 O(T²) 全连接 attention，生产可用 FlashAttention 或切到 ONNX Runtime
* **ReAct 并行工具调用**：当前串行，可改成 CompletableFuture.allOf
* **向量检索**：byte-hash 是回退方案，真实 ES 应配 ik 分词 + 向量字段（dense_vector）
* **消息压缩**：Message.content 长期累积后按 session 归档到 OSS / ClickHouse
