#!/usr/bin/env bash
# clean-cache.sh - 清理 Vite 依赖缓存
set -e
echo "[clean-cache] 清理 Vite 依赖缓存..."
[ -d "node_modules/.vite" ] && rm -rf "node_modules/.vite" && echo "[clean-cache] ✓ 已删 node_modules/.vite" || echo "[clean-cache] - node_modules/.vite 不存在"
[ -d "dist" ] && rm -rf "dist" && echo "[clean-cache] ✓ 已删 dist"
[ -d ".nuxt" ] && rm -rf ".nuxt" && echo "[clean-cache] ✓ 已删 .nuxt"
echo
echo "[clean-cache] 完成! 现在可以: npm run dev / npm run build"
