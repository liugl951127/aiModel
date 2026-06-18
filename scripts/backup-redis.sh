#!/usr/bin/env bash
# ★ OP-6 Redis 备份 (RDB + AOF)
# 用法: crontab 加 0 4 * * * /opt/ai-platform/scripts/backup-redis.sh
set -e

BACKUP_DIR=${BACKUP_DIR:-/opt/ai-platform/backups/redis}
REDIS_HOST=${REDIS_HOST:-127.0.0.1}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_PASS=${REDIS_PASS:-}

TS=$(date +%Y%m%d_%H%M%S)
DEST="${BACKUP_DIR}/redis_${TS}.rdb"
LOG="${BACKUP_DIR}/backup.log"

mkdir -p "${BACKUP_DIR}"

echo "[$(date +'%F %T')] Redis 备份 ${REDIS_HOST}:${REDIS_PORT} → ${DEST}" | tee -a "${LOG}"

# 1) BGSAVE
if [ -n "${REDIS_PASS}" ]; then
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASS}" BGSAVE 2>/dev/null
else
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" BGSAVE
fi

# 2) 等待 save 完成
sleep 3

# 3) 复制 dump.rdb
DUMP_RDB=$(docker exec ai-redis cat /data/dump.rdb 2>/dev/null || redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" --rdb "${DEST}")
if [ -s "${DEST}" ]; then
  SIZE=$(stat -c%s "${DEST}" 2>/dev/null || stat -f%z "${DEST}")
  echo "[$(date +'%F %T')] ✓ Redis 备份成功, 大小: $((SIZE/1024/1024))MB" | tee -a "${LOG}"
else
  echo "[$(date +'%F %T')] ✗ Redis 备份失败" | tee -a "${LOG}"
  exit 1
fi

# 4) 保留 7 天
find "${BACKUP_DIR}" -name "redis_*.rdb" -mtime +7 -delete
echo "[$(date +'%F %T')] 清理完成" | tee -a "${LOG}"
