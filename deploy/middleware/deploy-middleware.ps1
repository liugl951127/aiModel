# ★ AI Platform 运行时中间件一键部署 (PowerShell 版本)
# 适用: Windows 10/11 (PowerShell 5.1+)
#
# 用法 (在 PowerShell 中):
#   .\deploy-middleware.ps1           # 启动 7 个中间件
#   .\deploy-middleware.ps1 status    # 看状态
#   .\deploy-middleware.ps1 stop      # 停止
#   .\deploy-middleware.ps1 logs      # 看日志
#   .\deploy-middleware.ps1 health    # 健康检查
#   .\deploy-middleware.ps1 pull      # 拉镜像
#   .\deploy-middleware.ps1 reset     # 删数据 (慎)
#   .\deploy-middleware.ps1 help
#
# 首次运行若提示 "running scripts is disabled":
#   Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Bypass

[CmdletBinding()]
param(
    [Parameter(Position=0)]
    [ValidateSet("start","status","stop","logs","health","pull","reset","help")]
    [string]$Command = "start",

    [Parameter(Position=1)]
    [string]$ServiceName = ""
)

# 不使用 StrictMode, 避免未初始化变量报错 (docker 未装时优雅退出)
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

# 全局变量占位 (避免 strict mode 报 unbound)
$script:ComposeCmd = $null

# === 颜色 ===
function Write-Ok {
    param([string]$msg)
    Write-Host "✓ $msg" -ForegroundColor Green
}
function Write-Warn {
    param([string]$msg)
    Write-Host "⚠ $msg" -ForegroundColor Yellow
}
function Write-Err {
    param([string]$msg)
    Write-Host "✗ $msg" -ForegroundColor Red
}
function Write-Header {
    param([string]$msg)
    Write-Host ""
    Write-Host "====================================================" -ForegroundColor Cyan
    Write-Host "  $msg" -ForegroundColor Cyan
    Write-Host "====================================================" -ForegroundColor Cyan
    Write-Host ""
}

# === 端口 (Windows) ===
$Ports = @{
    "elasticsearch" = 9200
    "nacos"         = 8848
    "seata"         = 7091
    "nginx"         = 8080
    "prometheus"    = 9090
    "grafana"       = 3000
    "ollama"        = 11434
}

# === 检查 Docker ===
function Test-Docker {
    Write-Header "检查 Docker 环境"

    # 1) docker 命令
    try {
        $dockerVersion = docker --version
        Write-Ok "docker: $dockerVersion"
    } catch {
        Write-Err "docker 命令不存在"
        Write-Host ""
        Write-Host "请先安装 Docker Desktop:"
        Write-Host "  https://www.docker.com/products/docker-desktop/"
        Write-Host ""
        Write-Host "安装完启动 Docker Desktop, 等右下角图标变绿后再跑此脚本"
        exit 1
    }

    # 2) Docker daemon
    try {
        docker info | Out-Null
        Write-Ok "Docker daemon: running"
    } catch {
        Write-Err "Docker daemon 没起来"
        Write-Host ""
        Write-Host "启动 Docker Desktop (系统托盘图标 → Start), 等 'Docker is running' 后再试"
        exit 1
    }

    # 3) docker compose
    $script:ComposeCmd = $null
    try {
        $v = docker compose version --short
        $script:ComposeCmd = "docker compose"
        Write-Ok "docker compose: $v"
    } catch {
        try {
            $v = docker-compose --version
            $script:ComposeCmd = "docker-compose"
            Write-Warn "docker-compose v1 (旧版, 推荐升级到 v2 plugin)"
        } catch {
            Write-Err "docker compose / docker-compose 都不存在"
            Write-Host "请升级 Docker Desktop (v2 plugin 内置)"
            exit 1
        }
    }

    # 4) WSL2 检测
    $wsl = $null
    try { $wsl = wsl --status 2>$null } catch {}
    if ($wsl -match "WSL" -or $env:WSL_DISTRO_NAME) {
        Write-Ok "WSL: $($env:WSL_DISTRO_NAME ?? 'enabled')"
    }
}

# === 端口冲突检查 ===
function Test-Ports {
    Write-Header "检查端口占用"
    $conflicts = 0
    foreach ($name in $Ports.Keys) {
        $port = $Ports[$name]
        $inUse = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($inUse) {
            Write-Warn "端口 $port ($name) 已被占用 (PID: $($inUse.OwningProcess))"
            $conflicts++
        }
    }
    if ($conflicts -eq 0) {
        Write-Ok "7 个端口 (8080/8848/9200/3000/7091/9090/11434) 全部空闲"
    } else {
        Write-Warn "$conflicts 个端口冲突, 仍继续 (docker 会自动映射)"
    }
}

# === 拉镜像 ===
function Pull-Images {
    Write-Header "拉 Docker 镜像"
    & $script:ComposeCmd pull
    Write-Ok "所有镜像拉取完成"
}

# === 启动 ===
function Start-All {
    Test-Docker
    Test-Ports
    Write-Header "启动 7 个中间件"
    & $script:ComposeCmd up -d --remove-orphans

    Write-Host ""
    Write-Ok "7 个中间件已起在后台"
    Write-Host ""
    Write-Host "  访问入口 (浏览器):"
    Write-Host "    Nginx       http://localhost:8080"
    Write-Host "    Grafana     http://localhost:3000  (admin/admin)"
    Write-Host "    Prometheus  http://localhost:9090"
    Write-Host "    ES          http://localhost:9200"
    Write-Host "    Nacos       http://localhost:8848/nacos  (nacos/nacos)"
    Write-Host "    Seata       http://localhost:7091  (admin/admin)"
    Write-Host "    Ollama      http://localhost:11434"
    Write-Host ""
    Write-Host "  等 30-60s 后:"
    Write-Host "    .\deploy-middleware.ps1 health    # 校验"
    Write-Host "    .\deploy-middleware.ps1 logs elasticsearch"
}

# === 状态 ===
function Show-Status {
    Write-Header "中间件状态"
    & $script:ComposeCmd ps
    Write-Host ""
    Write-Header "端口监听 (按服务)"
    foreach ($name in $Ports.Keys) {
        $port = $Ports[$name]
        $conn = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue -InformationLevel Quiet
        $status = if ($conn) { "UP" } else { "DOWN" }
        $color = if ($conn) { "Green" } else { "Red" }
        Write-Host ("  {0,-15} port {1,-5} " -f $name, $port) -NoNewline
        Write-Host $status -ForegroundColor $color
    }
}

# === 停止 ===
function Stop-All {
    Write-Header "停止所有中间件"
    & $script:ComposeCmd stop
    Write-Ok "已停止 (数据保留)"
}

# === 删数据 ===
function Reset-All {
    Write-Warn "将删除所有数据卷 (ES 索引 / Nacos 配置 / Prometheus 指标 / Ollama 模型)"
    $confirm = Read-Host "确认? 输 YES 继续"
    if ($confirm -ne "YES") {
        Write-Warn "已取消"
        exit 0
    }
    & $script:ComposeCmd down -v
    Write-Ok "已删除所有容器 + 数据卷"
}

# === 日志 ===
function Show-Logs {
    param([string]$svc)
    if ([string]::IsNullOrEmpty($svc)) {
        Write-Header "所有服务日志 (Ctrl+C 退出)"
        & $script:ComposeCmd logs -f --tail=100
    } else {
        Write-Header "日志: $svc (Ctrl+C 退出)"
        & $script:ComposeCmd logs -f --tail=100 $svc
    }
}

# === 健康检查 ===
function Test-Health {
    Write-Header "健康检查"
    $urls = @{
        "Nginx"         = "http://127.0.0.1:8080"
        "Grafana"       = "http://127.0.0.1:3000/api/health"
        "Prometheus"    = "http://127.0.0.1:9090/-/ready"
        "Elasticsearch" = "http://127.0.0.1:9200/_cluster/health"
        "Nacos"         = "http://127.0.0.1:8848/nacos/"
        "Seata"         = "http://127.0.0.1:7091/"
        "Ollama"        = "http://127.0.0.1:11434/api/tags"
    }
    $allOk = $true
    foreach ($name in $urls.Keys) {
        $url = $urls[$name]
        try {
            $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            $code = $resp.StatusCode
        } catch {
            $code = 0
        }
        if ($code -ge 200 -and $code -lt 400) {
            Write-Ok "$name ($url) → $code"
        } else {
            Write-Warn "$name ($url) → $code (可能还在启动, 30s 后重试)"
            $allOk = $false
        }
    }
    Write-Host ""
    if ($allOk) {
        Write-Ok "全部健康 ✓"
    } else {
        Write-Warn "部分服务还没好, 等 30s 再试"
    }
}

# === 帮助 ===
function Show-Help {
    @"
AI Platform 运行时中间件一键部署 (PowerShell 版本)

用法:
  .\deploy-middleware.ps1 [command] [service]

命令:
  start      启动 7 个中间件 (默认)
  status     看状态
  stop       停止 (数据保留)
  logs       看所有日志 (可指定服务: logs elasticsearch)
  health     健康检查
  pull       只拉镜像
  reset      删数据 (慎)
  help       帮助

包含的中间件 (7 件套):
  1. Elasticsearch 8.13    端口 9200   知识库 RAG
  2. Nacos 2.3.1            端口 8848   配置/注册
  3. Seata 2.0.0 Server     端口 7091   分布式事务
  4. Nginx alpine           端口 8080   前端代理
  5. Prometheus 2.50        端口 9090   指标采集
  6. Grafana 10.4           端口 3000   可视化
  7. Ollama latest          端口 11434  本地 LLM

Windows 注意事项:
  1. 先装 Docker Desktop, 启动后右下角图标变绿
  2. 首次运行若提示 "running scripts is disabled":
     Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Bypass
  3. 资源: Settings → Resources → Memory 至少 4GB
  4. 端口被占用: 脚本会继续, docker 自动映射
"@
}

# === 入口 ===
switch ($Command) {
    "start"  { Start-All }
    "status" { Show-Status }
    "stop"   { Stop-All }
    "logs"   { Show-Logs -svc $ServiceName }
    "health" { Test-Health }
    "pull"   { Test-Docker; Pull-Images }
    "reset"  { Reset-All }
    "help"   { Show-Help }
    default  {
        Write-Err "未知命令: $Command"
        Show-Help
        exit 1
    }
}
