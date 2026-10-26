#!/usr/bin/env bash
# =====================================================================
# 生成 / 更新 Mosquitto 账号密码文件
#
# 为什么需要脚本：mosquitto 不接受明文密码配置，必须用 mosquitto_passwd
# 生成哈希文件；直接在宿主机装 mosquitto-clients 又多一份依赖，
# 用官方镜像跑一次最省事。
#
# 用法（在项目根目录执行）：
#   chmod +x deploy/scripts/gen-mosquitto-passwd.sh
#   ./deploy/scripts/gen-mosquitto-passwd.sh
# 账号密码从 .env 的 MQTT_USERNAME / MQTT_PASSWORD 读取
# =====================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  echo "[错误] 未找到 .env，请先执行：cp .env.example .env 并修改其中的密码"
  exit 1
fi

# 只读取需要的两个变量，避免 source 整个 .env 带来副作用
MQTT_USERNAME="$(grep -E '^MQTT_USERNAME=' .env | cut -d= -f2-)"
MQTT_PASSWORD="$(grep -E '^MQTT_PASSWORD=' .env | cut -d= -f2-)"

if [[ -z "${MQTT_USERNAME}" || -z "${MQTT_PASSWORD}" ]]; then
  echo "[错误] .env 中缺少 MQTT_USERNAME 或 MQTT_PASSWORD"
  exit 1
fi

echo "[1/3] 创建密码文件（用户: ${MQTT_USERNAME}）"
mkdir -p deploy/mosquitto
docker run --rm \
  -v "${ROOT_DIR}/deploy/mosquitto:/mosquitto/config" \
  eclipse-mosquitto:2 \
  mosquitto_passwd -c -b /mosquitto/config/passwd "${MQTT_USERNAME}" "${MQTT_PASSWORD}"

echo "[2/3] 修正文件属主（容器内 mosquitto 以 uid 1883 运行）"
# 权限给到 640 且属主为 1883，既能让 broker 读取，又不让其他用户读到哈希
sudo chown 1883:1883 deploy/mosquitto/passwd 2>/dev/null || chown 1883:1883 deploy/mosquitto/passwd
chmod 640 deploy/mosquitto/passwd

echo "[3/3] 完成，重启 broker 生效：docker compose restart mosquitto"
echo "      验证订阅：docker exec -it tp-mosquitto mosquitto_sub -t 'attendance/#' -u ${MQTT_USERNAME} -P '***' -v"
