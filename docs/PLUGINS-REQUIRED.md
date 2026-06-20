# 项目插件清单 (PLUGINS-REQUIRED)

> **自动扫描**: 2026-06-19 · 列出运行整个 AI Agent Platform 所需的所有插件、依赖、中间件
> **分类**: 构建工具 / 后端依赖 / 前端依赖 / 中间件 / 浏览器 / IDE

---

## 一、构建工具 (必需)

| 工具 | 版本 | 用途 | 必需? |
|---|---|---|---|
| **JDK** | **17+** (eclipse-temurin 推荐) | Java 编译/运行 | ✅ |
| **Maven** | 3.6+ | Java 构建 | ✅ |
| **Node.js** | 18+ (推荐 20 LTS) | 前端构建 | ✅ |
| **npm** | 9+ (随 Node) | 前端包管理 | ✅ |
| **Python** | 3.10+ | mock_ai_server / 运维脚本 | ✅ |
| **Docker** | 24+ | 容器化部署 | ⭐ 推荐 |
| **Docker Compose** | v2+ | 多服务编排 | ⭐ 推荐 |
| **Git** | 2.30+ | 代码管理 | ✅ |

---

## 二、后端 Maven 核心依赖 (pom.xml)

### Spring 体系
| 依赖 | 版本 | 用途 |
|---|---|---|
| `spring-boot-starter-parent` | **3.2.5** | Spring Boot 父 POM |
| `spring-cloud` | **2023.0.1** | Spring Cloud 微服务 |
| `spring-cloud-alibaba` | **2023.0.1.0** | Nacos/Sentinel |
| `spring-boot-starter-web` | 3.2.5 | REST API |
| `spring-boot-starter-actuator` | 3.2.5 | 健康检查 + Prometheus |
| `spring-boot-starter-data-redis` | 3.2.5 | Redis 客户端 |
| `spring-boot-starter-data-elasticsearch` | 3.2.5 | ES 客户端 |
| `spring-boot-starter-security` | 3.2.5 | 安全框架 |
| `spring-boot-starter-validation` | 3.2.5 | 参数校验 |
| `spring-boot-starter-aop` | 3.2.5 | AOP (审计) |
| `spring-cloud-starter-gateway` | 2023.0.1 | API 网关 |
| `spring-cloud-starter-openfeign` | 2023.0.1 | 服务调用 |
| `spring-cloud-starter-loadbalancer` | 2023.0.1 | 负载均衡 |

### 数据 / 持久化
| 依赖 | 版本 | 用途 |
|---|---|---|
| `mybatis-plus-spring-boot3-starter` | (3.5.7) | ORM |
| `mysql-connector-j` | **8.0.33** | MySQL 驱动 |
| `h2database` | (随 Spring) | 单元测试 |

### 工具库
| 依赖 | 版本 | 用途 |
|---|---|---|
| `hutool-all` | **5.8.27** | Java 工具集 |
| `fastjson2` | **2.0.49** | JSON 解析 |
| `lombok` | **1.18.32** | 编译期代码生成 |
| `easyexcel` | **3.3.4** | Excel 导入导出 |
| `knife4j-openapi3-jakarta-spring-boot-starter` | **4.5.0** | Swagger UI |
| `jjwt-api / impl / jackson` | **0.12.5** | JWT 签发 |
| `micrometer-registry-prometheus` | (随 Spring) | Prometheus 指标导出 |

### AI / 机器学习
| 依赖 | 版本 | 用途 |
|---|---|---|
| `onnxruntime` | **1.17.1** | ONNX 模型本地推理 |
| `djl-api` + `djl-pytorch` | **0.36.0** | DJL 训练 (PyTorch) |
| `tika-core` | **2.9.1** | 文档解析 (PDF/Word/Excel) |

### 安全
| 依赖 | 用途 |
|---|---|
| `spring-security` | JWT 鉴权 (UserDetails, Filter) |

### 事务
| 依赖 | 版本 | 用途 |
|---|---|---|
| `spring-tx` | 6.x (Spring Boot 自带) | 本地 `@Transactional` (够用) |

> 已移除 Seata 分布式事务, 全部走本地 `@Transactional` 事务.

### 编译期
| 依赖 | 用途 |
|---|---|
| `maven-compiler-plugin` | Java 17 编译 |
| `spring-boot-maven-plugin` | 打 fat-jar |
| `maven-surefire-plugin` | 跑单元测试 |

---

## 三、前端 npm 依赖 (frontend/package.json)

### 生产依赖
| 依赖 | 版本 | 用途 |
|---|---|---|
| `vue` | **^3.4.0** | 前端框架 |
| `vue-router` | **^4.3.0** | 路由 |
| `pinia` | **^2.1.7** | 状态管理 |
| `axios` | **^1.6.8** | HTTP 客户端 |
| `element-plus` | **^2.6.0** | UI 组件库 |
| `@element-plus/icons-vue` | **^2.3.1** | EP 图标 |
| `echarts` | **^6.1.0** | 图表库 (折线/柱/饼) |
| `vue-echarts` | **^8.0.1** | ECharts Vue 3 包装 |

### 开发依赖
| 依赖 | 版本 | 用途 |
|---|---|---|
| `vite` | **^5.2.0** | 构建工具 |
| `@vitejs/plugin-vue` | **^5.0.0** | Vite Vue 插件 |
| `sass` | **^1.74.0** | SCSS 预编译 |
| `unplugin-auto-import` | **^0.17.5** | 自动 import (ref, onMounted 等) |
| `unplugin-vue-components` | **^0.26.0** | Element Plus 按需引入 |

### 可选 (后续增强)
| 依赖 | 用途 |
|---|---|
| `@vueuse/core` | Vue 3 工具集 (debounce, throttle) |
| `dayjs` | 日期处理 (替代 moment) |
| `xlsx` | 浏览器端导出 Excel |
| `file-saver` | 浏览器下载 |

---

## 四、运行时中间件 (docker-compose)

| 中间件 | 镜像 | 端口 | 必需? |
|---|---|---|---|
| **MySQL** | `mysql:8.0` | 3306 | ✅ 必需 |
| **Redis** | `redis:7-alpine` | 6379 | ✅ 必需 |
| **Elasticsearch** | `docker.elastic.co/elasticsearch/elasticsearch:8.13.0` | 9200 | ✅ 必需 |
| **Nacos** | `nacos/nacos-server:v2.3.1` | 8848, 9848 | ⭐ 可选 (有 fallback 走本地配置) |
| **Nginx** | `nginx:alpine` | 80 | ⭐ 可选 (前端部署) |
| **Prometheus** | `prom/prometheus:v2.50.0` | 9090 | ⭐ 可选 (监控) |
| **Grafana** | `grafana/grafana:10.4.0` | 3000 | ⭐ 可选 (监控) |
| **Ollama** | `ollama/ollama:latest` | 11434 | ⭐ 可选 (AI backend 切换) |
| **AI 服务自建镜像** | `ai-gateway/auth/user/...` (12 个) | 9000-9011 | ✅ 必需 |

### Elasticsearch 插件
| 插件 | 用途 |
|---|---|
| `analysis-ik` | 中文分词 (知识库 RAG 必需) |
| `analysis-pinyin` | 拼音搜索 |

### Ollama 模型 (按需拉)
| 模型 | 用途 |
|---|---|
| `qwen2.5:7b` (4.7GB) | 默认 chat 模型 |
| `qwen2.5:1.5b` (1.1GB) | 轻量 chat |
| `nomic-embed-text` (274MB) | embedding |
| `llama3:8b` (4.7GB) | 英文替代 |

### ONNX 模型 (按需下载到 /opt/ai-platform/models/)
| 模型 | 用途 |
|---|---|
| `BAAI/bge-small-zh-v1.5` (93MB) | 中文 embedding (512 维) |
| `BAAI/bge-large-zh-v1.5` (324MB) | 中文 embedding (1024 维) |
| `BAAI/bge-reranker-base` (568MB) | rerank |
| `Qwen/Qwen2.5-1.5B-Instruct` (~1GB) | 本地 LLM |

---

## 五、IDE 插件 (推荐)

### VS Code
| 插件 | 用途 |
|---|---|
| **Vue - Official** (`vue.volar`) | Vue 3 单文件组件支持 |
| **ESLint** (`dbaeumer.vscode-eslint`) | JS/Vue 代码检查 |
| **Prettier** (`esbenp.prettier-vscode`) | 代码格式化 |
| **Java Extension Pack** (`vscjava.vscode-java-pack`) | Java 全套 (Maven, Test, Debug) |
| **Spring Boot Extension Pack** (`pivotal.vscode-spring-boot`) | Spring Boot 项目支持 |
| **Lombok Annotations** (`GabrielBB.vscode-lombok`) | Lombok 支持 |
| **Stylelint** (`stylelint.vscode-stylelint`) | SCSS 检查 |
| **Mermaid** (`bierner.markdown-mermaid`) | 看 mermaid 图 |
| **Markdown All in One** (`yzhang.markdown-all-in-one`) | 写文档 |
| **GitLens** (`eamodio.gitlens`) | Git 增强 |
| **Docker** (`ms-azuretools.vscode-docker`) | Docker 容器管理 |
| **MySQL** (`cweijan.vscode-mysql-client2`) | MySQL 客户端 |

### IntelliJ IDEA (Ultimate 推荐)
| 插件 | 用途 |
|---|---|
| **Vue.js** (内置) | Vue 3 支持 |
| **Lombok** (内置) | Lombok |
| **MyBatisX** | Mapper ↔ XML 跳转 |
| **MyBatis Log** | SQL 日志格式化 |
| **MyBatisCodeHelperPro** | MyBatis 代码生成 |
| **Alibaba Cloud Toolkit** | 一键部署到阿里云 |
| **Grep Console** | Log 高亮 |
| **Nacos** | Nacos 配置同步 |
| **.env files support** | .env.example 语法 |
| **Docker** | Dockerfile/compose 语法 |
| **PlantUML** | UML 图 |

---

## 六、浏览器扩展 (推荐)

| 扩展 | 浏览器 | 用途 |
|---|---|---|
| **Vue.js devtools** | Chrome/Firefox/Edge | Vue 3 调试 |
| **JSON Viewer** | 通用 | 格式化 JSON 响应 |
| **ModHeader** | Chrome | 改 HTTP header (测试 JWT) |
| **Talend API Tester** | Chrome | 手测 REST API (替代 Postman) |
| **Lighthouse** | Chrome | 性能 / 无障碍 / SEO 审计 |
| **Sentry** | Chrome | 前端错误监控 (可选) |
| **Bitwarden** | 通用 | 密码管理 (存 JWT secret 等) |

---

## 七、CLI 工具 (运维用)

| 工具 | 用途 | 必需? |
|---|---|---|
| `curl` | 调 API | ✅ |
| `jq` | JSON 解析 | ⭐ |
| `htop` / `top` | 系统监控 | ⭐ |
| `nethogs` / `iftop` | 网络监控 | ⭐ |
| `redis-cli` | Redis 操作 | ✅ (Redis 调试) |
| `mysql-client` | MySQL 操作 | ✅ |
| `kubectl` | K8s (Helm chart 部署时) | ⭐ |
| `helm` | K8s 包管理 | ⭐ |
| `k9s` | K8s TUI | ⭐ |
| `promtool` | Prometheus 规则检查 | ⭐ |
| `loki` | 日志聚合 | ⭐ |
| `vector` / `filebeat` | 日志采集 | ⭐ |

---

## 八、CDN / 第三方 API (可选用)

| 服务 | URL | 用途 |
|---|---|---|
| jsDelivr | `cdn.jsdelivr.net` | mermaid.js / element-plus icons |
| Hugging Face | `huggingface.co` | 下载 ONNX 模型 |
| ModelScope | `modelscope.cn` | 阿里模型镜像 (国内快) |
| OCI Container Registry | `container-registry.oracle.com` | docker 镜像拉取备用 |
| aliyun maven | `maven.aliyun.com` | 国内 Maven 加速 |
| npmmirror | `registry.npmmirror.com` | 国内 npm 加速 |

---

## 九、运维脚本 (项目自带)

| 脚本 | 用途 |
|---|---|
| `scripts/build.sh` | 一键构建所有后端 (打 Docker 镜像) |
| `scripts/dev-up.sh` | docker-compose 启动全平台 |
| `scripts/dev-frontend.sh` | 启前端 dev server |
| `scripts/backup-mysql.sh` | MySQL 自动备份 |
| `scripts/backup-redis.sh` | Redis 备份 |
| `scripts/restore-mysql.sh` | MySQL 恢复 |
| `scripts/rollback.sh` | 一键回滚 |
| `scripts/monitor.sh` | 系统监控 |
| `scripts/alert-webhook.sh` | 飞书/钉钉告警 |
| `scripts/gen-jwt-secret.sh` | 生成 JWT 密钥 |
| `scripts/render_mermaid.py` | mermaid → PNG |
| `scripts/build_arch_docx.py` | 生成架构 Word 文档 |
| `scripts/extend_mock_ai.py` | 扩展 mock 端点 |

---

## 十、最小可运行集 (Laptop 起步)

如果是个人笔记本起步, **最少装**:
1. **JDK 17** (Temurin)
2. **Maven 3.6+**
3. **Node.js 18+**
4. **Docker Desktop** (含 docker compose)
5. **MySQL 8** 容器
6. **Redis 7** 容器
7. **Elasticsearch 8.13** 容器
8. **VS Code** + Vue 插件 + Java 插件

**总下载量**: ~5GB
**总占用**: ~15GB
**启动时间**: 5-10 分钟

如果是生产集群, 加:
- Nacos Server
- Prometheus + Grafana
- K8s 集群 (Helm)
- 备份 / 监控 / 告警

---

## 十一、版本兼容性矩阵

| 组件 | 推荐版本 | 不兼容 |
|---|---|---|
| JDK 17 | Temurin 17.0.10+ | Java 8/11 编译失败 |
| Maven 3.6+ | Maven 3.9.x | Maven 3.2 装不下 Spring Boot 3 |
| Node 18+ | Node 20 LTS | Node 16 部分 ES 库不兼容 |
| MySQL 8.0+ | MySQL 8.0.33+ | MySQL 5.7 (utf8mb4 + JSON 支持差) |
| Redis 6+ | Redis 7 | Redis 5 (无 stream) |
| ES 8+ | ES 8.13 | ES 7 (API 差异大) |
| Nacos 2.x | Nacos 2.3.1 | Nacos 1.x (不同 namespace API) |
| Vue 3 | 3.4+ | Vue 2 (语法不兼容) |
| Element Plus 2+ | 2.6+ | Element UI 1.x (组件不兼容) |
| Spring Boot 3 | 3.2.5+ | Boot 2.x (Jakarta EE 9 vs javax) |
