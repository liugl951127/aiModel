#!/usr/bin/env bash
# ★ OP-8 系统资源监控 (CPU/内存/磁盘/服务探活)
# 用法:
#   ./scripts/monitor.sh             # 单次检查
#   watch -n 30 ./scripts/monitor.sh  # 30s 周期
set -e

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'

echo "=========================================="
echo " AI Platform 监控 - $(date '+%F %T')"
echo "=========================================="

# 1) CPU 使用率
CPU=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'.' -f1)
echo -n "CPU:    ${CPU}% "
if [ "${CPU}" -gt 80 ]; then
  echo -e "${RED}[WARNING] 高负载${NC}"
elif [ "${CPU}" -gt 60 ]; then
  echo -e "${YELLOW}[注意] 中等${NC}"
else
  echo -e "${GREEN}[正常]${NC}"
fi

# 2) 内存
MEM_TOTAL=$(free -m | awk '/Mem:/ {print $2}')
MEM_USED=$(free -m | awk '/Mem:/ {print $3}')
MEM_PCT=$((MEM_USED * 100 / MEM_TOTAL))
echo -n "内存:   ${MEM_USED}/${MEM_TOTAL}MB (${MEM_PCT}%) "
if [ "${MEM_PCT}" -gt 85 ]; then
  echo -e "${RED}[WARNING]${NC}"
else
  echo -e "${GREEN}[正常]${NC}"
fi

# 3) 磁盘
DISK_PCT=$(df -h / | tail -1 | awk '{print $5}' | tr -d '%')
echo -n "磁盘:   / ${DISK_PCT}% "
if [ "${DISK_PCT}" -gt 85 ]; then
  echo -e "${RED}[WARNING]${NC}"
else
  echo -e "${GREEN}[正常]${NC}"
fi

# 4) 11 个服务探活
echo "----------"
echo "服务状态:"
for svc in gateway:9000 auth:9001 user:9002 system:9003 model:9004 \
           agent:9005 knowledge:9006 inference:9007 trainer:9008 \
           files:9010 workflow:9011; do
  NAME=${svc%:*}
  PORT=${svc#*:}
  HTTP_CODE=$(curl -fs -o /dev/null -w "%{http_code}" "http://127.0.0.1:${PORT}/actuator/health" 2>/dev/null || echo "DOWN")
  if [ "${HTTP_CODE}" = "200" ]; then
    echo -e "  ${NAME}:${PORT}  ${GREEN}UP${NC} (200)"
  else
    echo -e "  ${NAME}:${PORT}  ${RED}DOWN${NC} (${HTTP_CODE})"
  fi
done

# 5) Docker 容器
echo "----------"
echo "Docker:"
docker ps --format "  {{.Names}}: {{.Status}}" | head -15

# 6) 数据库连接
echo "----------"
echo "MySQL: $(docker exec ai-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-root} -e 'SELECT 1' 2>&1 | tail -1)"
echo "Redis: $(docker exec ai-redis redis-cli ping 2>&1 | tail -1)"

# 7) 最近的错误日志
echo "----------"
echo "ERROR 日志 (最近 5 条):"
for f in /opt/ai-platform/logs/*-error.log; do
  [ -f "$f" ] && tail -5 "$f" 2>/dev/null | grep -E "ERROR" | tail -3
done | head -5
