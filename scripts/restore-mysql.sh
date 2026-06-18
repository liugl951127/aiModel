#!/usr/bin/env bash
# ★ OP-6 MySQL 一键恢复
# 用法: ./scripts/restore-mysql.sh /path/to/backup.sql.gz
set -e

if [ -z "$1" ]; then
  echo "用法: $0 <backup.sql.gz>"
  echo "示例: $0 /opt/ai-platform/backups/mysql/ai_platform_20260618_030000.sql.gz"
  exit 1
fi

BACKUP_FILE="$1"
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_USER=${MYSQL_USER:-root}
MYSQL_PASS=${MYSQL_PASS:-root}
MYSQL_DB=${MYSQL_DB:-ai_platform}

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "✗ 备份文件不存在: ${BACKUP_FILE}"
  exit 1
fi

echo "⚠️  即将恢复数据库 ${MYSQL_DB}@${MYSQL_HOST}:${MYSQL_PORT} 从 ${BACKUP_FILE}"
echo "   现有数据将被覆盖!"
read -p "确认继续? (yes/no): " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
  echo "已取消"
  exit 0
fi

echo "1) 备份当前数据库..."
./scripts/backup-mysql.sh

echo "2) 解压恢复..."
gunzip -c "${BACKUP_FILE}" | mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASS}" "${MYSQL_DB}"

echo "✓ 恢复完成! 建议立即执行: SELECT COUNT(*) FROM sys_user;"
