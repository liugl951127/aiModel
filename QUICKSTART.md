# Quickstart

> 5 分钟把平台跑起来。

## 1. 准备环境

| 工具 | 版本 |
| --- | --- |
| JDK | 17+ |
| Maven | 3.8+ |
| Python | 3.11+ |
| Node | 18+ |
| Docker / Docker Compose | 24+ |

## 2. 一键拉起（推荐）

```bash
git clone <repo>
cd ai-agent-platform/deploy/docker
docker compose up -d
```

> 等待约 60 秒，浏览器打开 [http://localhost:8080](http://localhost:8080)
> 登录：admin / admin123 / 租户 1

## 3. 仅启动推理微服务（最轻量）

```bash
# 训练
cd ai-model
pip install numpy
python3 -m src.training.train --data data/sample_corpus.txt --max-iters 100
python3 -m src.export.export_onnx --in checkpoints/mini_gpt.npz --out exports/mini_gpt.bundle
sudo mkdir -p /opt/ai-platform/inference-bundles
sudo cp -r exports/mini_gpt.bundle /opt/ai-platform/inference-bundles/default

# 启动
cd ../backend
mvn install -N
mvn install -pl ai-platform-common -DskipTests
mvn package -pl ai-platform-inference -DskipTests
java -jar ai-platform-inference/target/ai-platform-inference.jar

# 测试
curl http://localhost:9007/api/inference/models
curl -X POST http://localhost:9007/api/inference/generate \
  -H 'Content-Type: application/json' \
  -d '{"modelCode":"default","prompt":"hi","maxTokens":15}'
```

## 4. 启动全栈（开发模式）

参考 README 的「方式 B：本地开发模式」。
