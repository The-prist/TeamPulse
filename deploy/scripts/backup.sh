#!/usr/bin/env bash
# =====================================================================
# MySQL 备份脚本：每日全量 + 保留 7 天
#
# 用 docker exec 调容器内的 mysqldump，宿主机无需安装 mysql 客户端。
# 建议加入 crontab：
#   0 3 * * * /opt/teampulse/deploy/scripts/backup.sh >> /var/log/tp-backup.log 2>&1
# =====================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/deploy/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
CONTAINER="tp-mysql"

[[ -f .env ]] || { echo "[错误] 缺少 .env"; exit 1; }
DB_NAME="$(grep -E '^DB_NAME=' .env | cut -d= -f2-)"
DB_PASS="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
DB_NAME="${DB_NAME:-teampulse}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d_%H%M%S)"
OUT="${BACKUP_DIR}/${DB_NAME}_${STAMP}.sql.gz"

echo "[$(date '+%F %T')] 开始备份 ${DB_NAME} -> ${OUT}"

# --single-transaction：InnoDB 一致性快照，不锁表
# --routines --triggers：把存储过程/触发器一起带走
docker exec "$CONTAINER" sh -c \
  "exec mysqldump -uroot -p'${DB_PASS}' --single-transaction --quick --routines --triggers --events \
   --default-character-set=utf8mb4 '${DB_NAME}'" | gzip -9 > "$OUT"

SIZE="$(du -h "$OUT" | cut -f1)"
echo "[$(date '+%F %T')] 备份完成，大小 ${SIZE}"

# 清理过期备份
DELETED="$(find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -type f -mtime "+${RETENTION_DAYS}" -print -delete | wc -l)"
echo "[$(date '+%F %T')] 清理 ${DELETED} 个超过 ${RETENTION_DAYS} 天的旧备份"
