#!/usr/bin/env bash
# ★ OP-4 build with version tag (运维经理要求可回滚)
# 用法:
#   ./scripts/build.sh           # 自动生成版本 (2.0.YYYYMMDD)
#   ./scripts/build.sh 2.1.0     # 指定版本
set -e

cd "$(dirname "$0")/../backend"

if [ -n "$1" ]; then
  VERSION="$1"
else
  VERSION="2.0.$(date +%Y%m%d)"
fi

echo "==> Build version: ${VERSION}"
echo "==> Installing parent + common"
mvn install -N -DskipTests -q
mvn install -pl ai-platform-common -DskipTests -q

echo "==> Packaging services"
mvn package -DskipTests

# ★ 打 Docker 镜像 (版本标签, 方便 rollback)
DOCKER_DIR="$(dirname "$0")/../deploy/docker"
echo "==> Building Docker images with tag ${VERSION}"
for svc in gateway auth user system model agent knowledge inference trainer files workflow; do
  echo "  - ai-${svc}:${VERSION}"
  docker build -q -t "ai-${svc}:${VERSION}" -f "${DOCKER_DIR}/Dockerfile.${svc}" . \
    --build-arg VERSION="${VERSION}"
  docker tag "ai-${svc}:${VERSION}" "ai-${svc}:latest"
done

echo ""
echo "✓ Build done. 镜像列表:"
docker images | grep -E "^ai-" | head -15
echo ""
echo "回滚命令示例: ./scripts/rollback.sh gateway ${VERSION}"
