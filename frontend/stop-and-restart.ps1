# stop-and-restart.ps1
# 杀掉旧 vite/node 进程, 清缓存, 重启 dev server
$ErrorActionPreference = 'Stop'

Write-Host "[stop-and-restart] 杀掉占用 5173 端口的旧 vite/node..." -ForegroundColor Cyan

# 找并杀占用 5173 的进程
$netstat = netstat -ano | Select-String ":5173"
foreach ($line in $netstat) {
    $parts = $line -split '\s+'
    $pid = $parts[-1]
    if ($pid -match '^\d+$') {
        Write-Host "  杀掉 PID $pid" -ForegroundColor Yellow
        taskkill /PID $pid /F 2>&1 | Out-Null
    }
}

# 杀掉所有 vite 进程
Get-Process node -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*vite*' } | ForEach-Object {
    Write-Host "  杀掉 vite PID $($_.Id)" -ForegroundColor Yellow
    Stop-Process -Id $_.Id -Force
}

Start-Sleep -Seconds 2

Write-Host "[stop-and-restart] 清缓存..." -ForegroundColor Cyan
if (Test-Path "node_modules\.vite") {
    Remove-Item -Recurse -Force "node_modules\.vite"
    Write-Host "  ✓ 已删 node_modules\.vite" -ForegroundColor Green
}
if (Test-Path "dist") {
    Remove-Item -Recurse -Force "dist"
}

Write-Host "[stop-and-restart] 重启 dev server..." -ForegroundColor Cyan
Write-Host ""
Write-Host "现在请手动运行: npm run dev" -ForegroundColor Green
Write-Host ""
Write-Host "如果仍然 5173 占用, 再跑一次这个脚本" -ForegroundColor Yellow