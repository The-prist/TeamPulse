#!/usr/bin/env bash
# =====================================================================
# 本地一键启动 TeamPulse（后端 + 前端），用于查看页面原型
#
# 前提：本机有一个可用的 MySQL 8（脚本会自动建库建表）。
#       Redis / MQTT 不是必需的：
#         · 没有 Redis → nonce 去重降级为内存实现，打卡链路照常可用
#         · 没有 MQTT  → 后端告警并每 30 秒重试，Web 接口不受影响
#
# 用法：
#   DB_PASSWORD=你的密码 ./scripts/run-local.sh   # DB_PASSWORD 必须提供，脚本不内置任何默认密码
#   ./scripts/run-local.sh --stop               # 停止服务
#
# 为什么不给默认密码：本仓库会被推到 GitHub，脚本里写死密码等于把
# 数据库口令一并公开（历史提交里也永远删不掉）。
# =====================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOG_DIR="$ROOT_DIR/.run-logs"
mkdir -p "$LOG_DIR"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-teampulse}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:?请先设置 DB_PASSWORD 环境变量（脚本不再内置默认密码）}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

log()  { printf '\033[36m%s\033[0m\n' "$*"; }
ok()   { printf '\033[32m%s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*"; }
die()  { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

port_pid() { lsof -ti "tcp:$1" -sTCP:LISTEN 2>/dev/null || true; }

stop_all() {
  for pair in "$FRONTEND_PORT:前端" "$BACKEND_PORT:后端"; do
    port="${pair%%:*}"; label="${pair##*:}"
    pid="$(port_pid "$port")"
    if [ -n "$pid" ]; then
      kill $pid 2>/dev/null || true
      warn "已停止 $label (端口 $port, PID $pid)"
    else
      echo "  $label (端口 $port) 未在运行"
    fi
  done
}

if [ "${1:-}" = "--stop" ]; then
  log "停止 TeamPulse 本地服务..."
  stop_all
  ok "完成。"
  exit 0
fi

# ---------- 环境检查 ----------
log "[1/5] 检查工具链"
command -v java >/dev/null || die "找不到 java，请安装 JDK 17+"
command -v mvn  >/dev/null || die "找不到 mvn，请安装 Maven 3.9+"
command -v node >/dev/null || die "找不到 node，请安装 Node.js 20+"
echo "  java : $(command -v java)"
echo "  mvn  : $(command -v mvn)"
echo "  node : $(command -v node) ($(node -v))"

[ -d "$FRONTEND_DIR/node_modules" ] || warn "前端依赖未安装，请先执行：cd frontend && npm install"

log "[2/5] 检查端口"
for p in "$BACKEND_PORT" "$FRONTEND_PORT"; do
  if [ -n "$(port_pid "$p")" ]; then
    warn "  端口 $p 被占用，尝试释放"
    kill "$(port_pid "$p")" 2>/dev/null || true
    sleep 1
  else
    echo "  端口 $p 空闲"
  fi
done

mkdir -p "$BACKEND_DIR/target/tmp"

# ---------- 启动后端 ----------
log "[3/5] 启动后端（首次启动会自动建库建表，约 20~40 秒）"
export DB_HOST DB_PORT DB_NAME DB_USERNAME="$DB_USER" DB_PASSWORD
export MQTT_ENABLED=false
export SPRING_PROFILES_ACTIVE=dev
export SERVER_PORT="$BACKEND_PORT"

BE_LOG="$LOG_DIR/backend.log"
(
  cd "$BACKEND_DIR"
  nohup mvn -B spring-boot:run \
    "-Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=$BACKEND_DIR/target/tmp" \
    > "$BE_LOG" 2>&1 &
  echo $! > "$LOG_DIR/backend.pid"
)
echo "  后端 PID $(cat "$LOG_DIR/backend.pid")，日志：$BE_LOG"

for i in $(seq 1 120); do
  [ -n "$(port_pid "$BACKEND_PORT")" ] && break
  sleep 1
done
[ -n "$(port_pid "$BACKEND_PORT")" ] || { tail -40 "$BE_LOG"; die "后端启动失败"; }
echo "  后端健康状态: $(curl -s "http://127.0.0.1:$BACKEND_PORT/actuator/health" || echo '暂未就绪')"

# ---------- 启动前端 ----------
if [ "${NO_FRONTEND:-0}" != "1" ]; then
  log "[4/5] 启动前端开发服务器"
  [ -f "$FRONTEND_DIR/node_modules/vite/bin/vite.js" ] || die "找不到 vite，请先 cd frontend && npm install"
  FE_LOG="$LOG_DIR/frontend.log"
  (
    cd "$FRONTEND_DIR"
    nohup node node_modules/vite/bin/vite.js --host 127.0.0.1 --port "$FRONTEND_PORT" > "$FE_LOG" 2>&1 &
    echo $! > "$LOG_DIR/frontend.pid"
  )
  echo "  前端 PID $(cat "$LOG_DIR/frontend.pid")，日志：$FE_LOG"
  for i in $(seq 1 60); do
    [ -n "$(port_pid "$FRONTEND_PORT")" ] && break
    sleep 1
  done
  [ -n "$(port_pid "$FRONTEND_PORT")" ] || { tail -20 "$FE_LOG"; die "前端启动失败"; }
fi

ok "[5/5] 启动完成"
echo "=============================================================="
echo "  页面入口 : http://127.0.0.1:$FRONTEND_PORT/"
echo "  后端接口 : http://127.0.0.1:$BACKEND_PORT/api/v1"
echo "  接口文档 : http://127.0.0.1:$BACKEND_PORT/swagger-ui.html"
echo "  健康检查 : http://127.0.0.1:$BACKEND_PORT/actuator/health"
echo "=============================================================="
echo "  登录账号 : admin / admin123（首次登录后请修改）"
echo
echo "  查看演示设备密钥："
echo "    grep -A3 '演示打卡设备' '$BE_LOG'"
echo
echo "  停止服务："
echo "    ./scripts/run-local.sh --stop"
