#!/usr/bin/env bash
# ★ OP-7 一键回滚脚本
# 用法: ./scripts/rollback.sh <service> <version>
# 示例: ./scripts/rollback.sh gateway 2.0.20260617
set -e

if [ -z "$1" ] || [ -z "$2" ]; then
  echo "用法: $0 <service> <version>"
  echo "服务列表: gateway auth user system model agent knowledge inference trainer files workflow"
  echo "版本列表:"
  docker images | grep "ai-" | awk '{print "  " $1 ":" $2}' | sort -u
  exit 1
fi

SERVICE=$1
VERSION=$2
IMAGE="ai-${SERVICE}:${VERSION}"
CONTAINER="ai-${SERVICE}"

if ! docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${IMAGE}$"; then
  echo "✗ 镜像不存在: ${IMAGE}"
  echo "  本服务现有镜像:"
  docker images | grep "ai-${SERVICE}" | head -5
  exit 1
fi

echo "⚠️  即将回滚 ${CONTAINER} → ${IMAGE}"
read -p "确认继续? (yes/no): " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
  echo "已取消"
  exit 0
fi

cd "$(dirname "$0")/../deploy/docker"

echo "1) 停止旧容器..."
docker compose stop "${SERVICE}"

echo "2) 拉起新版本..."
VERSION="${VERSION}" docker compose up -d --no-deps "${SERVICE}"

echo "3) 等待健康..."
for i in 1 2 3 4 5 6 7 8 9 10; do
  sleep 3
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' "${CONTAINER}" 2>/dev/null || echo "starting")
  echo "  [${i}/10] 状态: ${STATUS}"
  if [ "${STATUS}" = "healthy" ]; then
    echo "✓ 回滚成功! ${CONTAINER} 已运行 ${IMAGE}"
    docker logs --tail 20 "${CONTAINER}"
    exit 0
  fi
done

echo "✗ 健康检查失败, 请手动排查"
docker logs --tail 50 "${CONTAINER}"
exit 2
