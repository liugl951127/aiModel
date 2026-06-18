#!/usr/bin/env bash
# ★ OP-6 MySQL 自动备份脚本
# 用法: crontab -e 加 0 3 * * * /opt/ai-platform/scripts/backup-mysql.sh
# 保留: 7 天本地, 30 天远程
set -e

BACKUP_DIR=${BACKUP_DIR:-/opt/ai-platform/backups/mysql}
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_USER=${MYSQL_USER:-root}
MYSQL_PASS=${MYSQL_PASS:-root}
MYSQL_DB=${MYSQL_DB:-ai_platform}
RETENTION_DAYS=${RETENTION_DAYS:-7}

TS=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/ai_platform_${TS}.sql.gz"
LOG_FILE="${BACKUP_DIR}/backup.log"

mkdir -p "${BACKUP_DIR}"

echo "[$(date +'%F %T')] 备份 ${MYSQL_DB}@${MYSQL_HOST}:${MYSQL_PORT} → ${BACKUP_FILE}" | tee -a "${LOG_FILE}"

# 1) 全库 mysqldump + gzip
if mysqldump -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASS}" \
     --single-transaction --quick --routines --triggers --events \
     --hex-blob --default-character-set=utf8mb4 \
     "${MYSQL_DB}" 2>>"${LOG_FILE}" | gzip > "${BACKUP_FILE}"; then
  SIZE=$(stat -c%s "${BACKUP_FILE}" 2>/dev/null || stat -f%z "${BACKUP_FILE}")
  echo "[$(date +'%F %T')] ✓ 备份成功, 大小: $((SIZE/1024/1024))MB" | tee -a "${LOG_FILE}"
else
  echo "[$(date +'%F %T')] ✗ 备份失败" | tee -a "${LOG_FILE}"
  exit 1
fi

# 2) 删除 7 天前的
find "${BACKUP_DIR}" -name "ai_platform_*.sql.gz" -mtime +${RETENTION_DAYS} -delete
echo "[$(date +'%F %T')] 已清理 ${RETENTION_DAYS} 天前旧备份" | tee -a "${LOG_FILE}"

# 3) (可选) 同步到远程
REMOTE_HOST=${REMOTE_BACKUP_HOST:-}
if [ -n "${REMOTE_HOST}" ]; then
  rsync -az "${BACKUP_FILE}" "${REMOTE_HOST}:/backups/ai-platform/mysql/" && \
    echo "[$(date +'%F %T')] ✓ 远程同步成功" | tee -a "${LOG_FILE}"
fi

# 4) 校验备份完整性 (必须能 gunzip)
if gunzip -t "${BACKUP_FILE}" 2>/dev/null; then
  echo "[$(date +'%F %T')] ✓ 备份校验通过" | tee -a "${LOG_FILE}"
else
  echo "[$(date +'%F %T')] ✗ 备份文件损坏!" | tee -a "${LOG_FILE}"
  exit 2
fi
