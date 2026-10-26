#!/usr/bin/env bash
# =====================================================================
# 一键发布脚本：拉代码 → 重建镜像 → 滚动上线 → 健康检查 → 清理旧镜像
#
# 设计要点：
#   1. 先备份数据库再发布，出问题可回滚数据
#   2. 只重建有变化的服务（compose 会自动判断），减少停机时间
#   3. 发布后轮询健康状态，失败时打印最近日志而不是干等
#
# 用法：./deploy/scripts/deploy.sh [--skip-backup]
# =====================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

SKIP_BACKUP=false
if [[ "${1:-}" == "--skip-backup" ]]; then
  SKIP_BACKUP=true
fi

log() { echo -e "\033[32m[$(date '+%H:%M:%S')]\033[0m $*"; }
err() { echo -e "\033[31m[$(date '+%H:%M:%S')] 失败:\033[0m $*" >&2; }

[[ -f .env ]] || { err "缺少 .env 文件"; exit 1; }

log "1/6 拉取最新代码"
git pull --ff-only || log "跳过 git pull（非 Git 仓库或已是最新）"

if [[ "$SKIP_BACKUP" == "false" ]]; then
  log "2/6 备份数据库"
  ./deploy/scripts/backup.sh || { err "备份失败，中止发布"; exit 1; }
else
  log "2/6 跳过数据库备份（--skip-backup）"
fi

log "3/6 构建镜像（首次构建约 3~6 分钟，取决于服务器带宽）"
docker compose build --pull backend frontend

log "4/6 启动/更新服务"
docker compose up -d --remove-orphans

log "5/6 等待健康检查"
MAX_WAIT=180
WAITED=0
while (( WAITED < MAX_WAIT )); do
  # 只把 healthy（以及无健康检查时的空值）视为通过：
  # 用 grep -v 'healthy' 会把 "unhealthy" 也一起过滤掉，等于放过真正异常的服务。
  UNHEALTHY="$(docker compose ps --format json 2>/dev/null \
    | grep -o '"Health":"[a-z]*"' | grep -vE '"Health":"(healthy)?"' || true)"
  if [[ -z "$UNHEALTHY" ]]; then
    log "全部服务健康"
    break
  fi
  sleep 5
  WAITED=$((WAITED + 5))
  echo "  ...等待中 ${WAITED}s"
done

if (( WAITED >= MAX_WAIT )); then
  err "健康检查超时，输出后端最近 80 行日志："
  docker compose logs --tail=80 backend
  exit 1
fi

log "6/6 清理悬空镜像"
docker image prune -f >/dev/null

echo
log "发布完成，当前状态："
docker compose ps
echo
SERVER_IP="$(curl -s --max-time 3 ifconfig.me || echo '<服务器IP>')"
echo "  前端地址: http://${SERVER_IP}/"
echo "  健康检查: curl -s http://127.0.0.1/healthz"
echo "  接口文档: 需在 .env 中把 TPP_OPENAPI_ENABLED 设为 true 后重启 backend"
