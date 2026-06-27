# start-trainer.ps1
# Trainer 启动 (Windows) - 绕过 DJL cuda 探测
#
# DJL 0.36 在 Windows 启动时会探测 cuda flavor, 即使只引 cpu native 也报 WARN.
# 设 PYTORCH_FLAVOR=cpu 跳过探测.

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $ScriptDir '..\backend'

Set-Location $BackendDir

# 默认 cpu flavor (TrainerApplication.java 静态块也会设, 这里再加环境变量级别保险)
if (-not $env:PYTORCH_FLAVOR) {
    $env:PYTORCH_FLAVOR = 'cpu'
}

$Jar = 'ai-platform-trainer\target\ai-platform-trainer.jar'
if (-not (Test-Path $Jar)) {
    Write-Host "[X] $Jar 不存在, 先 mvn package -DskipTests" -ForegroundColor Red
    exit 1
}

$JavaOpts = if ($env:JAVA_OPTS) { $env:JAVA_OPTS } else { '-Xmx512m -Xms128m' }
$AppOpts = if ($env:APP_OPTS) { $env:APP_OPTS } else { '--spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false' }

Write-Host "[i] 启动 trainer: PYTORCH_FLAVOR=$env:PYTORCH_FLAVOR" -ForegroundColor Cyan

# 用 Start-Process 后台启动
$Process = Start-Process -FilePath 'java' `
    -ArgumentList @($JavaOpts -split ' '; '-jar'; $Jar; ($AppOpts -split ' ')) `
    -PassThru -NoNewWindow -RedirectStandardOutput "$ScriptDir\..\logs\trainer.out.log" `
    -RedirectStandardError "$ScriptDir\..\logs\trainer.err.log"

Write-Host "[i] Trainer PID: $($Process.Id)" -ForegroundColor Green
Write-Host "[i] 日志: logs\trainer.out.log" -ForegroundColor Yellow