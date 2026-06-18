#!/usr/bin/env bash
# ★ OP-12 启动全平台 (带 healthcheck, secret 检查, 版本标签)
# 用法: ./scripts/dev-up.sh
set -e

cd "$(dirname "$0")/../deploy/docker"

# 1) 加载 .env (没有就提示生成)
if [ ! -f "../../.env" ]; then
  echo "⚠️  未发现 .env 文件, 复制模板..."
  cp ../../.env.example ../../.env
  echo ""
  echo "请编辑 ../../.env 设置 JWT_SECRET 和 MYSQL_ROOT_PASSWORD"
  echo "生成 JWT_SECRET: ./scripts/gen-jwt-secret.sh"
  echo ""
  read -p "已设置完毕? 继续? (yes/no): " CONFIRM
  if [ "${CONFIRM}" != "yes" ]; then
    echo "已取消"
    exit 0
  fi
fi

# 2) 加载环境变量
set -a
. ../../.env
set +a

# 3) JWT_SECRET 强制校验
if [ -z "${JWT_SECRET}" ] || [ "${JWT_SECRET}" = "ai-platform-default-secret-key-please-change-32+" ] || [ "${#JWT_SECRET}" -lt 32 ]; then
  echo "✗ JWT_SECRET 不安全, 启动中止"
  echo "  修法: ./scripts/gen-jwt-secret.sh, 然后写入 .env"
  exit 1
fi

# 4) 启动
echo "==> Starting Nacos + MySQL + Redis + ES + 11 services + Nginx + Prometheus + Grafana"
echo "    镜像版本: ${VERSION:-latest}"
docker compose up -d --build

echo ""
echo "==> 等待服务就绪..."
echo "    (查看进度: docker compose ps)"
echo ""

# 5) 等待 + 探活
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  sleep 5
  HEALTHY=$(docker ps --filter "health=healthy" --format "{{.Names}}" | wc -l)
  TOTAL=$(docker ps --format "{{.Names}}" | wc -l)
  echo "  [$((i*5))s] 健康: ${HEALTHY}/${TOTAL}"
  if [ "${HEALTHY}" -ge 11 ]; then
    echo "✓ 所有服务已就绪!"
    break
  fi
done

echo ""
echo "==> 访问入口:"
echo "    前端:   http://localhost:8080"
echo "    网关:   http://localhost:9000"
echo "    Nacos:  http://localhost:8848/nacos (nacos/nacos)"
echo "    Prom:   http://localhost:9090 (默认 profile 不启动, 见 --profile monitoring)"
echo "    Graf:   http://localhost:3000 (admin/admin)"
echo ""
echo "    监控:   ./scripts/monitor.sh"
echo "    备份:   ./scripts/backup-mysql.sh"
echo "    回滚:   ./scripts/rollback.sh <svc> <version>"
