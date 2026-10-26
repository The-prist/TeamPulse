#!/usr/bin/env bash
# ============================================================================
# TeamPulse 服务器端部署脚本（Rocky Linux 10 / 腾讯云轻量）
#
# 做四件事：
#   1. 拉取或更新代码到 /opt/TeamPulse
#   2. 首次部署时生成 .env（所有密码随机，权限 600）
#   3. 生成 mosquitto 密码文件
#   4. 构建镜像并启动，最后做健康检查
#
# 幂等：重复执行只会 git pull + 重启，不会覆盖已存在的 .env
# ============================================================================
set -euo pipefail

APP_DIR=/opt/TeamPulse
REPO=https://github.com/The-prist/TeamPulse.git

echo "=== [1/5] 代码 ==="
mkdir -p /opt
if [ -d "$APP_DIR/.git" ]; then
    cd "$APP_DIR"
    # 国内机器访问 GitHub 常常间歇性失败，拉不到就用现有代码继续（不中断部署）
    if git fetch --quiet origin 2>/dev/null && git reset --hard --quiet origin/main 2>/dev/null; then
        echo "已更新到 $(git rev-parse --short HEAD) $(git log -1 --format='%s')"
    else
        echo "⚠ GitHub 不可达，跳过代码更新，继续用当前 $(git rev-parse --short HEAD)"
        echo "  如需推送新代码：在开发机执行 .\\scripts\\deploy-from-local.ps1"
    fi
elif [ -f "./docker-compose.yml" ]; then
    echo "当前目录已有代码（由 deploy-from-local.ps1 推送），跳过克隆"
else
    git clone --quiet "$REPO" "$APP_DIR"
    cd "$APP_DIR"
    echo "已克隆 $(git rev-parse --short HEAD)"
fi

echo
echo "=== [2/5] 环境变量 ==="
if [ -f .env ]; then
    echo ".env 已存在，保留不覆盖"
else
    MYSQL_PWD=$(openssl rand -hex 16)
    REDIS_PWD=$(openssl rand -hex 16)
    MQTT_PWD=$(openssl rand -hex 16)
    JWT_SECRET=$(openssl rand -hex 32)
    ADMIN_PWD="Tp$(openssl rand -hex 4)@$(openssl rand -hex 2)"
    GRAFANA_PWD="Gf$(openssl rand -hex 6)"
    cat > .env <<ENVEOF
# 由部署脚本自动生成，请勿提交到仓库
DB_NAME=teampulse
MYSQL_ROOT_PASSWORD=${MYSQL_PWD}
REDIS_PASSWORD=${REDIS_PWD}
MQTT_USERNAME=teampulse
MQTT_PASSWORD=${MQTT_PWD}
JWT_SECRET=${JWT_SECRET}
TPP_BOOTSTRAP_ENABLED=true
TPP_ADMIN_PASSWORD=${ADMIN_PWD}
TPP_OPENAPI_ENABLED=false
TPP_CORS_ORIGINS=
GRAFANA_ADMIN_PASSWORD=${GRAFANA_PWD}
ENVEOF
    chmod 600 .env
    echo ".env 已生成（权限 600）"
fi
chmod +x deploy/scripts/*.sh 2>/dev/null || true

echo
echo "=== [3/5] MQTT 密码文件 ==="
if [ -s deploy/mosquitto/passwd ]; then
    echo "passwd 已存在，跳过"
else
    MQTT_USER=$(grep '^MQTT_USERNAME=' .env | cut -d= -f2)
    MQTT_PASS=$(grep '^MQTT_PASSWORD=' .env | cut -d= -f2)
    echo "拉取 mosquitto 镜像（走腾讯云内网加速）..."
    docker pull -q eclipse-mosquitto:2 >/dev/null
    docker run --rm -v "$PWD/deploy/mosquitto:/m" eclipse-mosquitto:2 \
        sh -c "mosquitto_passwd -c -b /m/passwd '$MQTT_USER' '$MQTT_PASS' && chmod 644 /m/passwd"
    echo "passwd 已生成：$(wc -l < deploy/mosquitto/passwd) 行"
fi

echo
echo "=== [4/5] 构建镜像 ==="
START=$(date +%s)
docker compose build 2>&1 | tail -15
echo "构建耗时 $(( $(date +%s) - START )) 秒"

echo
echo "=== [5/5] 启动 ==="
docker compose up -d 2>&1 | tail -8

echo
echo "等待容器 healthy（最多 180 秒）..."
for i in $(seq 1 36); do
    sleep 5
    HEALTHY=$(docker ps --filter 'name=tp-' --format '{{.Status}}' | grep -c healthy || true)
    echo "  ${i}0s  健康容器 ${HEALTHY}/5"
    [ "$HEALTHY" -ge 5 ] && break
done

echo
echo "=== 容器状态 ==="
docker compose ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}'

echo
echo "=== 健康检查 ==="
curl -s -o /dev/null -w "  本机 /healthz        -> %{http_code}\n" http://127.0.0.1/healthz
curl -s -o /dev/null -w "  本机 /actuator/health -> %{http_code}\n" http://127.0.0.1/actuator/health
curl -s http://127.0.0.1/actuator/health || true
echo

echo "=== 迁移版本 ==="
MPWD=$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2)
docker exec tp-mysql mysql -uroot -p"$MPWD" -N -B \
  -e "SELECT CONCAT(version,' ',success) FROM teampulse.flyway_schema_history ORDER BY installed_rank" 2>/dev/null \
  | sed 's/^/  V/'

echo
echo "=== 部署完成 ==="
echo "  应用目录: $APP_DIR"
echo "  管理员账号: $(grep '^TPP_BOOTSTRAP' .env >/dev/null && echo admin)"
echo "  （密码等敏感信息请在本机执行: cat $APP_DIR/.env）"
