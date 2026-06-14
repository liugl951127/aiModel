#!/usr/bin/env bash
# Bring up the full platform via Docker Compose.
set -e

cd "$(dirname "$0")/../deploy/docker"

echo "==> Starting Nacos + MySQL + Redis + ES + 8 services + Nginx"
docker compose up -d --build

echo "==> Waiting for services to be healthy..."
sleep 30

echo "==> Done. Open http://localhost:8080"
echo "    Nacos console: http://localhost:8848/nacos (nacos/nacos)"
echo "    API gateway:   http://localhost:9000"
