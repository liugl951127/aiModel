# clean-cache.ps1
# 清理 Vite 依赖缓存 (解决 optimize deps 残留导致的 "file does not exist" 报错)
# 用法: powershell -ExecutionPolicy Bypass -File clean-cache.ps1

$ErrorActionPreference = 'Stop'

Write-Host "[clean-cache] 清理 Vite 依赖缓存..." -ForegroundColor Cyan

# 1. 删 node_modules/.vite
if (Test-Path "node_modules\.vite") {
    Remove-Item -Recurse -Force "node_modules\.vite"
    Write-Host "[clean-cache] 已删 node_modules\.vite" -ForegroundColor Green
} else {
    Write-Host "[clean-cache] node_modules\.vite 不存在, 跳过" -ForegroundColor Yellow
}

# 2. 删 dist
if (Test-Path "dist") {
    Remove-Item -Recurse -Force "dist"
    Write-Host "[clean-cache] 已删 dist" -ForegroundColor Green
}

# 3. 删 .nuxt (如果存在)
if (Test-Path ".nuxt") {
    Remove-Item -Recurse -Force ".nuxt"
}

Write-Host ""
Write-Host "[clean-cache] 完成! 现在可以 npm run dev / npm run build" -ForegroundColor Green
Write-Host ""
Write-Host "建议顺序:" -ForegroundColor Yellow
Write-Host "  1. npm install        (如果删了 node_modules)"
Write-Host "  2. npm run dev        (dev 启动会自动重建 optimize deps)"
Write-Host "  3. npm run build      (prod build 会自动跳过 exclude 列表)"