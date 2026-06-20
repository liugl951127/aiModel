#!/usr/bin/env bash
# ★ AI Platform 运行时中间件一键部署 (bash 版本)
# 适用: Linux / macOS / WSL2 / Git Bash (Windows)
#
# 用法:
#   bash deploy-middleware.sh           # 启动 6 个中间件
#   bash deploy-middleware.sh status    # 看状态
#   bash deploy-middleware.sh stop      # 停止
#   bash deploy-middleware.sh logs      # 看日志
#   bash deploy-middleware.sh reset     # 删数据 (慎)
#   bash deploy-middleware.sh pull      # 拉镜像
#   bash deploy-middleware.sh health    # 健康检查
#   bash deploy-middleware.sh help
#
# Windows 提示: 推荐在 WSL2 或 Git Bash 中运行 (避免路径问题)

set -euo pipefail

# === 路径 ===
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# === 颜色 ===
if [ -t 1 ]; then
    RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
else
    RED=''; YELLOW=''; GREEN=''; CYAN=''; NC=''
fi

# === 端口 (Windows / Linux 通用) ===
declare -A PORTS=(
    [elasticsearch]=9200
    [nacos]=8848
    [nginx]=8080
    [prometheus]=9090
    [grafana]=3000
    [ollama]=11434
)

# === 工具函数 ===
print_header() {
    echo ""
    echo -e "${CYAN}====================================================${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}====================================================${NC}"
    echo ""
}

# 全局变量, 必须在 check_docker 之前初始化 (避免 set -u 报 unbound)
COMPOSE_CMD=""

print_ok() { echo -e "${GREEN}✓ $1${NC}"; }
print_warn() { echo -e "${YELLOW}⚠ $1${NC}"; }
print_err() { echo -e "${RED}✗ $1${NC}"; }

# === 检查 Docker ===
check_docker() {
    # 缓存: 避免 stop/logs/reset 重复检查 (首次调用走全部, 之后跳过)
    if [ "${_DOCKER_CHECKED:-0}" = "1" ]; then
        return 0
    fi
    print_header "检查 Docker 环境"

    # 1) docker 命令
    if ! command -v docker >/dev/null 2>&1; then
        print_err "docker 命令不存在"
        echo ""
        echo "请先安装 Docker:"
        echo "  Windows: 下载 Docker Desktop https://www.docker.com/products/docker-desktop/"
        echo "  macOS:   brew install --cask docker"
        echo "  Linux:   curl -fsSL https://get.docker.com | sh"
        echo ""
        echo "安装完确保 Docker Desktop 已启动 (Windows/macOS)."
        exit 1
    fi
    print_ok "docker: $(docker --version)"

    # 2) Docker daemon 是否在跑 (Windows 用 named pipe, Linux/Mac 用 socket)
    if ! docker info >/dev/null 2>&1; then
        print_err "Docker daemon 没起来"
        echo ""
        echo "Windows / macOS: 启动 Docker Desktop (右下角托盘图标 → Start)"
        echo "Linux:   sudo systemctl start docker"
        exit 1
    fi
    print_ok "Docker daemon: running"

    # 3) docker compose (v2 plugin, 替代老的 docker-compose)
    if docker compose version >/dev/null 2>&1; then
        COMPOSE_CMD="docker compose"
        print_ok "docker compose: $(docker compose version --short)"
    elif command -v docker-compose >/dev/null 2>&1; then
        COMPOSE_CMD="docker-compose"
        print_warn "docker-compose v1 (旧版, 推荐升级到 v2 plugin)"
    else
        print_err "docker compose / docker-compose 都不存在"
        exit 1
    fi

    # 4) 内存检查 (ES + Ollama 至少 4GB)
    if command -v free >/dev/null 2>&1; then
        TOTAL_MEM_MB=$(free -m | awk '/Mem:/ {print $2}')
        if [ "${TOTAL_MEM_MB}" -lt 4096 ]; then
            print_warn "总内存 ${TOTAL_MEM_MB}MB < 4GB, ES + Ollama 可能 OOM"
            echo "  建议: Docker Desktop Settings → Resources → Memory ≥ 4GB"
        else
            print_ok "内存: ${TOTAL_MEM_MB}MB (≥ 4GB 推荐)"
        fi
    fi

    # 5) WSL2 / Git Bash 检测
    if [ -n "${WSL_DISTRO_NAME:-}" ]; then
        print_ok "WSL2: ${WSL_DISTRO_NAME}"
    elif uname -r 2>/dev/null | grep -q "microsoft"; then
        print_ok "WSL: $(uname -r | head -c 60)"
    fi

    _DOCKER_CHECKED=1
}

# === 端口冲突检查 ===
check_ports() {
    print_header "检查端口占用"
    local has_conflict=0
    for name in "${!PORTS[@]}"; do
        local port=${PORTS[$name]}
        # 跨平台端口检测
        if command -v ss >/dev/null 2>&1; then
            if ss -lnt "sport = :${port}" 2>/dev/null | grep -q ":${port}"; then
                print_warn "端口 ${port} (${name}) 已被占用"
                has_conflict=1
            fi
        elif command -v netstat >/dev/null 2>&1; then
            if netstat -an 2>/dev/null | grep -q ":${port} "; then
                print_warn "端口 ${port} (${name}) 已被占用"
                has_conflict=1
            fi
        fi
    done
    if [ "${has_conflict}" -eq 0 ]; then
        print_ok "所有 6 个端口 (8080/8848/9200/3000/9090/11434) 空闲"
    else
        print_warn "有端口冲突, 仍继续 (docker 会自动映射到其他端口)"
    fi
}

# === 拉镜像 ===
pull_images() {
    print_header "拉 Docker 镜像"
    $COMPOSE_CMD pull
    print_ok "所有镜像拉取完成"
}

# === 启动 ===
start_all() {
    check_docker
    check_ports

    print_header "拉 Docker 镜像 (首次会较慢, 1-3 GB)"
    $COMPOSE_CMD pull || print_warn "部分镜像拉取失败, 继续尝试启动 (有缓存就 OK)"

    echo ""
    print_header "启动 6 个中间件"
    $COMPOSE_CMD up -d --remove-orphans

    echo ""
    print_ok "6 个中间件已起在后台, 等待健康 (30s)…"

    # 等待 30s 验准备 (全表) -- 如果某个服务未启, 提示
    local up_count=0
    for i in 1 2 3 4 5 6; do
        sleep 5
        # 查运行中的容器数
        if [ -n "${COMPOSE_CMD}" ]; then
            up_count=$($COMPOSE_CMD ps --services --filter "status=running" 2>/dev/null | wc -l)
        fi
        if [ "${up_count}" -ge 6 ]; then
            print_ok "6 个服务都起来了!"
            break
        fi
        printf "  [%d/6] 起来了 %d/6 个服务...\n" "$i" "$up_count"
    done
    if [ "${up_count}" -lt 6 ]; then
        print_warn "部分服务未起来 ($up_count/6). 查看: bash deploy-middleware.sh status"
        print_warn "或看日志: bash deploy-middleware.sh logs <service>"
    fi
    echo ""
    echo "  访问入口 (Windows: 浏览器输 localhost 也行, 或 127.0.0.1):"
    echo "    Nginx       http://localhost:8080"
    echo "    Grafana     http://localhost:3000  (admin/admin)"
    echo "    Prometheus  http://localhost:9090"
    echo "    ES          http://localhost:9200"
    echo "    Nacos       http://localhost:8848/nacos  (nacos/nacos)"
    echo "    Ollama      http://localhost:11434"
    echo ""
    echo "  等 30-60s 让 ES / Nacos 完全启动, 然后:"
    echo "    bash deploy-middleware.sh health    # 校验"
    echo "    bash deploy-middleware.sh logs es  # 看某服务日志"
}

# === 状态 ===
show_status() {
    if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
        print_warn "Docker 未运行, 无法看容器状态"
        print_warn "请先启动 Docker Desktop 或 Docker daemon"
        return 1
    fi
    # 补变量
    if [ -z "${COMPOSE_CMD}" ]; then
        if docker compose version >/dev/null 2>&1; then COMPOSE_CMD="docker compose"
        elif command -v docker-compose >/dev/null 2>&1; then COMPOSE_CMD="docker-compose"
        else print_err "docker compose 不存在"; return 1; fi
    fi
    print_header "中间件状态"
    $COMPOSE_CMD ps
    echo ""
    print_header "端口监听 (按服务)"
    for name in "${!PORTS[@]}"; do
        local port=${PORTS[$name]}
        local status
        if curl -fs -o /dev/null --max-time 2 "http://127.0.0.1:${port}" 2>/dev/null; then
            status="${GREEN}UP${NC}"
        else
            status="${RED}DOWN${NC}"
        fi
        printf "  %-15s port %-5s %b\n" "${name}" "${port}" "${status}"
    done
}

# === 停止 ===
stop_all() {
    if [ -z "${COMPOSE_CMD}" ]; then check_docker; fi
    print_header "停止所有中间件"
    $COMPOSE_CMD stop
    print_ok "已停止 (数据保留)"
}

# === 删数据 (慎) ===
reset_all() {
    if [ -z "${COMPOSE_CMD}" ]; then check_docker; fi
    print_warn "将删除所有数据卷 (ES 索引 / Nacos 配置 / Prometheus 指标 / Ollama 模型)"
    read -p "确认? 输 YES 继续: " confirm
    if [ "${confirm}" != "YES" ]; then
        print_warn "已取消"
        exit 0
    fi
    $COMPOSE_CMD down -v
    print_ok "已删除所有容器 + 数据卷"
}

# === 日志 ===
show_logs() {
    if [ -z "${COMPOSE_CMD}" ]; then check_docker; fi
    local svc="${1:-}"
    if [ -z "${svc}" ]; then
        print_header "所有服务日志 (Ctrl+C 退出)"
        $COMPOSE_CMD logs -f --tail=100
    else
        print_header "日志: ${svc} (Ctrl+C 退出)"
        $COMPOSE_CMD logs -f --tail=100 "${svc}"
    fi
}


# === 健康检查 ===
health_check() {
    print_header "健康检查"
    local all_ok=1
    declare -A URLS=(
        [Nginx]="http://127.0.0.1:8080"
        [Grafana]="http://127.0.0.1:3000/api/health"
        [Prometheus]="http://127.0.0.1:9090/-/ready"
        [Elasticsearch]="http://127.0.0.1:9200/_cluster/health"
        [Nacos]="http://127.0.0.1:8848/nacos/"
        [Ollama]="http://127.0.0.1:11434/api/tags"
    )
    for name in "${!URLS[@]}"; do
        local url=${URLS[$name]}
        local resp
        # 使用临时文件避免 curl 返多行 0 问题
        local code_file=$(mktemp)
        curl -s --max-time 3 -o /dev/null -w "%{http_code}" "${url}" > "${code_file}" 2>/dev/null || true
        resp=$(cat "${code_file}" 2>/dev/null | head -c 3)
        rm -f "${code_file}"
        resp="${resp:-000}"
        if [ "${resp}" = "200" ] || [ "${resp}" = "302" ]; then
            print_ok "${name} (${url}) → ${resp}"
        else
            print_warn "${name} (${url}) → ${resp} (可能还在启动, 30s 后重试)"
            all_ok=0
        fi
    done
    echo ""
    if [ "${all_ok}" -eq 1 ]; then
        print_ok "全部健康 ✓"
    else
        print_warn "部分服务还没好, 等待 30s 再试"
    fi
}

# === 帮助 ===
show_help() {
    cat <<'EOF'
AI Platform 运行时中间件一键部署 (bash 版本)

用法:
  bash deploy-middleware.sh           启动 6 个中间件
  bash deploy-middleware.sh status    看状态
  bash deploy-middleware.sh stop      停止 (数据保留)
  bash deploy-middleware.sh logs      看所有日志
  bash deploy-middleware.sh logs es   看 elasticsearch 日志
  bash deploy-middleware.sh health    健康检查 (6 个端口 + URL)
  bash deploy-middleware.sh pull      只拉镜像不启动
  bash deploy-middleware.sh reset     删数据 (慎)
  bash deploy-middleware.sh help      帮助

包含的中间件 (6 件套):
  1. Elasticsearch 8.13    (端口 9200, 知识库 RAG)
  2. Nacos 2.3.1            (端口 8848, 配置/注册)
  3. Nginx alpine           (端口 8080, 前端代理)
  4. Prometheus 2.50        (端口 9090, 指标采集)
  5. Grafana 10.4           (端口 3000, 可视化)
  6. Ollama latest          (端口 11434, 本地 LLM)

Windows 注意事项:
  1. 先装 Docker Desktop (https://www.docker.com/products/docker-desktop/)
  2. 启动 Docker Desktop, 等待右下角图标变绿
  3. 推荐在 WSL2 (Ubuntu) 或 Git Bash 中跑本脚本
  4. 资源: Settings → Resources → Memory 至少 4GB (ES + Ollama 需要)
  5. 端口冲突: 如果被占用, docker 自动映射到其他端口

EOF
}

# === 入口 ===
case "${1:-start}" in
    start|"")  start_all ;;
    status)    show_status ;;
    stop)      stop_all ;;
    logs)      show_logs "${2:-}" ;;
    health)    health_check ;;
    pull)      check_docker && pull_images ;;
    reset)     reset_all ;;
    help|-h|--help) show_help ;;
    *)
        print_err "未知命令: $1"
        echo ""
        show_help
        exit 1
        ;;
esac

