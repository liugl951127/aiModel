#!/usr/bin/env bash
# 启动前端 dev server (假设后端已跑)
# 用法: ./scripts/dev-frontend.sh
# 前端: http://127.0.0.1:5173
# 自动代理 /api/* 到 gateway 9000

set -e
cd "$(dirname "$0")/../frontend"

if [ ! -d node_modules ]; then
  echo "==> 安装前端依赖..."
  npm install
fi

echo "==> 启动 Vite dev server (端口 5173, 代理 /api → 9000)"
echo "    浏览器打开 http://127.0.0.1:5173"
echo "    控制台会显示 [proxy] 日志, 看到请求是否到达 gateway"
echo ""
echo "    按 Ctrl+C 停止"
echo ""

npm run dev
