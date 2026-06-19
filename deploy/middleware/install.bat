@echo off
REM ★ AI Platform 中间件一键部署 (Windows .bat 包装)
REM
REM 用法: 双击 install.bat, 或 cmd 里跑 install.bat
REM 这个 .bat 帮你绕过 PowerShell 脚本执行策略, 直接调 deploy-middleware.ps1

setlocal
chcp 65001 >nul

echo.
echo ====================================================
echo   AI Platform 中间件一键部署 (Windows)
echo ====================================================
echo.

REM 检查 PowerShell 是否可用
where powershell >nul 2>&1
if errorlevel 1 (
    echo [X] PowerShell 未找到, 请安装 PowerShell 5.1+
    echo     https://docs.microsoft.com/powershell/
    pause
    exit /b 1
)

echo [i] 检测到 PowerShell:
powershell -Command "$PSVersionTable.PSVersion"
echo.

REM 临时绕过执行策略 (本进程)
echo [i] 临时设置 ExecutionPolicy = Bypass (仅本次)
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { Set-Location -Path '%~dp0'; .\deploy-middleware.ps1 %* }"

endlocal
pause
