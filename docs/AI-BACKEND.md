# AI 后端路由 (本地化 + 外部联网并存)

> **目标**: 业务侧只依赖 `AIBackend` 接口, 通过 `application.yml` 切换后端, 不改代码.
> **设计**: 内部实现 + 外部联网 双轨并存, 默认走内部 (0 联网), 需要时可切外部.

---

## 一、5 个后端 + 2 个 search-mode (按推荐度)

### Chat / Embed / Rerank 后端 (5 选 1)

| Backend | 联网? | 资源 | 适用场景 |
|---|---|---|---|
| **`mock`** (默认) | ❌ 0 联网 | 0 内存 | 演示 / CI / 无模型环境 |
| **`internal`** | ❌ 0 联网 | 0 内存 | **本系统内部** (调 knowledge + inference) |
| **`onnx`** | ❌ 0 联网 | 1-2GB 内存 | **生产推荐** (本地真模型推理) |
| **`ollama`** | ❌ 0 联网 | 8-32GB 内存 | 需要更大模型 (Qwen2.5-7B+) |
| **`http`** | ✅ 联网 | 0 内存 | 接 OpenAI/DeepSeek/通义千问 (慎用) |

### WebSearch 模式 (2 选 1, 独立)

| Mode | 联网? | 走哪 |
|---|---|---|
| **`internal`** (默认) | ❌ 0 联网 | 本系统知识库 + 内置 corpus (10 条) |
| **`external`** | ✅ 联网 | DuckDuckGo / Bing / Google CSE 等适配器 |

---

## 二、快速开始

### 1. 默认 (Mock 离线 + 内部 search)
不用配置, 直接跑. 所有 chat/embed/rerank 走 mock, webSearch 走内置 corpus.

```bash
mvn -pl ai-platform-ai -am spring-boot:run
curl http://localhost:9012/api/ai/backends
# {"chatBackend":"mock","searchMode":"internal","healthy":true,"availableChat":["mock"]}
```

### 2. ONNX 本地模型 (生产推荐)

#### 2.1 下载模型
```bash
mkdir -p /opt/ai-platform/models
cd /opt/ai-platform/models

# BGE 中文嵌入 (512 维, 93MB)
git lfs install
git clone https://huggingface.co/BAAI/bge-small-zh-v1.5
# 转 ONNX: optimum-cli export onnx --task feature-extraction BAAI/bge-small-zh-v1.5/

# BGE 重排序 (568MB)
git clone https://huggingface.co/BAAI/bge-reranker-base

# Qwen2.5 1.5B (≈1GB, CPU 可跑)
git clone https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct
# 量化: optimum-cli export onnx --task causal-lm --weight-format q4 Qwen/Qwen2.5-1.5B-Instruct/
```

#### 2.2 启动
```bash
export AI_BACKEND=onnx
export ONNX_EMBED_MODEL=/opt/ai-platform/models/bge-small-zh-v1.5/onnx/model.onnx
export ONNX_EMBED_VOCAB=/opt/ai-platform/models/bge-small-zh-v1.5/vocab.txt
export ONNX_RERANK_MODEL=/opt/ai-platform/models/bge-reranker-base/onnx/model.onnx

mvn -pl ai-platform-ai -am spring-boot:run
```

### 3. Ollama 本地服务 (一拉即用)

```bash
# 安装 Ollama (https://ollama.com)
curl -fsSL https://ollama.com/install.sh | sh
ollama serve &
ollama pull qwen2.5:7b
ollama pull nomic-embed-text

export AI_BACKEND=ollama
export OLLAMA_BASE_URL=http://127.0.0.1:11434
export OLLAMA_CHAT_MODEL=qwen2.5:7b

mvn -pl ai-platform-ai -am spring-boot:run
```

### 4. HTTP (OpenAI-compatible, 会联网)

```bash
export AI_BACKEND=http
export HTTP_AI_BASE_URL=https://api.deepseek.com/v1
export HTTP_AI_API_KEY=sk-...
export HTTP_AI_CHAT_MODEL=deepseek-chat
```

### 5. 内部 (本系统 knowledge + inference, 0 联网)

```bash
export AI_BACKEND=internal
# 跟 chat 独立, 调本系统的 /api/knowledge/search 和 /api/inference/generate
export INTERNAL_KB_URL=http://127.0.0.1:9006
export INTERNAL_INF_URL=http://127.0.0.1:9007
```

### 6. WebSearch 走外部 (独立开关, 任何 chat backend 都能加)

```bash
# 默认 internal (本系统)
export AI_SEARCH_MODE=internal

# 切到 external (外部联网, 走 DuckDuckGo)
export AI_SEARCH_MODE=external
# 配合 chat 后端 = 任意 (mock/internal/onnx/ollama/http)
```

## 1.5 运行时切换 (不重启)

```bash
# 看当前配置
curl http://localhost:9012/api/ai/backends
# {"chatBackend":"mock","searchMode":"internal","healthy":true,"availableChat":["mock"]}

# 切 chat backend
curl -X POST http://localhost:9012/api/ai/switch/internal

# 切 search-mode (需重启或重构 Switcher 为非 final)
# 默认内部优先, 外部失败自动降级
```

---

## 三、运行时切换后端 (不重启)

```bash
# 看当前后端
curl http://localhost:9012/api/ai/backends

# 切到 onnx (不重启, 立即生效)
curl -X POST http://localhost:9012/api/ai/switch/onnx

# 切回 mock
curl -X POST http://localhost:9012/api/ai/switch/mock
```

---

## 四、API 测试

### 聊天
```bash
curl -X POST http://localhost:9012/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "system": "你是助手",
    "messages": [{"role": "user", "content": "你好"}]
  }'
```

### 向量化
```bash
curl -X POST http://localhost:9012/api/ai/embed \
  -H "Content-Type: application/json" \
  -d '{"text": "今天天气不错"}'
# 返回: float[512]
```

### 联网搜索 (本地实现)
```bash
curl -X POST http://localhost:9012/api/ai/web-search \
  -H "Content-Type: application/json" \
  -d '{"query": "Spring Cloud Alibaba", "topK": 3}'
```

### 重排序
```bash
curl -X POST http://localhost:9012/api/ai/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "query": "AI 模型",
    "candidates": ["Qwen 大模型", "BGE 嵌入", "Java 编程", "Ollama"],
    "topK": 2
  }'
```

---

## 五、业务侧怎么用

### 5.1 Agent / 工具

```java
@Component
public class MyAgentTool {
    private final AIBackendRouter router;  // 注入统一接口
    public Object run() {
        float[] v = router.embed("query");         // 自动用当前后端
        String r = router.chat(...);                // 自动用当前后端
        var rerank = router.rerank(q, list, 3);     // 自动用当前后端
    }
}
```

### 5.2 知识库 RAG

```java
@Component
public class MyRagService {
    private final LocalEmbeddingClient embed;  // 自动 LRU 缓存
    public List<Doc> search(String q) {
        float[] v = embed.embed(q);
        // 走 ES dense retrieval ...
    }
}
```

---

## 六、迁移对照表

| 原 | 现在 |
|---|---|
| `https://api.duckduckgo.com/?q=...` | `AIBackend.webSearch()` 本地实现 |
| `OPENAI_API_KEY` + `https://api.openai.com/v1/chat/completions` | `AIBackend.chat()` (默认 mock, 可切 onnx/ollama) |
| `text-embedding-ada-002` 远程 | `AIBackend.embed()` (默认 mock, 可切 onnx) |
| `BAAI/bge-reranker-base` via HF Inference API | `AIBackend.rerank()` (默认 mock cosine) |
| `mock_ai_server.py` (Python 假服务) | `MockAIBackend` (Java 集成, 0 进程) |

---

## 七、文件清单

```
ai-platform-ai/
├── pom.xml
├── src/main/java/com/aiplatform/ai/
│   ├── backend/
│   │   ├── AIBackend.java              # 统一接口
│   │   ├── AIBackendRouter.java        # chat/embed/rerank 路由
│   │   ├── AIBackendSwitcher.java      # ★ 双轨路由: chat backend + search mode
│   │   ├── ExternalWebSearchAdapter.java  # 外部搜索适配器接口
│   │   └── impl/
│   │       ├── MockAIBackend.java        # 离线 (默认 chat)
│   │       ├── InternalAIBackend.java    # ★ 内部 (本系统 knowledge + inference)
│   │       ├── OnnxAIBackend.java        # ONNX Runtime
│   │       ├── OllamaAIBackend.java      # Ollama 客户端
│   │       ├── HttpAIBackend.java        # OpenAI-compatible (chat 远端, 走外网)
│   │       └── DuckDuckGoSearchAdapter.java  # ★ 外部联网搜索 (保留)
│   ├── local/
│   │   └── LocalEmbeddingClient.java   # 业务侧统一 embed 入口
│   └── controller/
│       └── AIBackendController.java    # /api/ai/** 管理 API
├── src/main/resources/
│   └── application.yml                 # 5 chat backend + 2 search mode
└── src/test/java/.../
    ├── MockAIBackendTest.java       # 10 测试
    └── InternalAIBackendTest.java  # 8 测试
```

---

## 八、运维

```bash
# 看当前后端
curl http://localhost:9012/api/ai/backends

# 健康
curl http://localhost:9012/api/ai/health

# 切后端 (运行时)
curl -X POST http://localhost:9012/api/ai/switch/onnx
```

### K8s 部署
- 选 `mock`: 任意 node, 256MB 内存
- 选 `onnx`: 选 CPU 节点, 4GB+ 内存
- 选 `ollama`: 选 GPU 节点 (有 GPU 可加速), 16GB+ 内存
- 选 `http`: 任意, 但要走外网

---

## 九、测试

```bash
mvn -pl ai-platform-ai -B test
# 18 tests, 应该全过
```

MockAIBackendTest (10):
- ✅ 名称正确 (mock)
- ✅ chat 返回非空
- ✅ embed 确定性 (同样输入→同样输出)
- ✅ embed 归一化 (norm=1)
- ✅ embed 不同文本不同向量
- ✅ rerank 按分数排序
- ✅ webSearch 空 query 返回所有
- ✅ webSearch 关键词匹配
- ✅ webSearch 限 topK
- ✅ 健康检查
- ✅ 批量 embed

InternalAIBackendTest (8):
- ✅ 名称正确 (internal)
- ✅ webSearch 降级到 corpus
- ✅ webSearch 空 query
- ✅ chat 服务不可达降级 mock
- ✅ embed 服务不可达降级
- ✅ rerank 降级
- ✅ isHealthy 服务不可达返 false
- ✅ corpus 关键词可搜
- ✅ corpus 至少 5 条
