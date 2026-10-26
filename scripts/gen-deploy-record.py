# -*- coding: utf-8 -*-
"""补「部署验证记录」文档，并更新 README 与交接文档的部署状态（本地容器化已跑通）。"""
import os

ROOT = r"E:\codestorge"
TODAY = "2026-10-27"

# ---------------------------------------------------------------- 1. 部署验证记录
doc = f"""# 部署验证记录（本机 Docker Desktop）

> 日期：{TODAY}　环境：Windows 11 家庭版 25H2 + Docker Desktop 4.97（WSL2 后端）
> 结论：**compose 五容器首次跑通**；镜像构建与启动链路已修复 4 个阻断问题 + 1 个全新库功能缺陷。
> 云服务器部署仍未执行（见文末「还没验证的」）。

## 一、环境准备过程（踩过的坑按顺序）

| # | 问题 | 处理 |
|---|---|---|
| 1 | 无管理员权限，winget 装 Docker Desktop 报 `4294967291`（拒绝访问） | 用管理员 PowerShell 启用 WSL 与 VirtualMachinePlatform，重启后装 |
| 2 | WSL 未安装，Docker 引擎起不来（`npipe dockerDesktopLinuxEngine` 不存在） | `wsl --update` + `wsl --set-default-version 2`，再启动 Docker Desktop（约 20s 引擎就绪） |
| 3 | `registry-1.docker.io` 连不上（IPv6 超时），镜像拉取全失败 | 配置 `~/.docker/daemon.json` 的 `registry-mirrors`（DaoCloud / 1ms / rat.dev），重启引擎后 `hello-world` 拉取成功 |

## 二、首次构建暴露并修复的问题

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | 前端镜像构建时上下文传输 130MB+ | 缺 `frontend/.dockerignore`，`node_modules`(193MB) 进了上下文 | 新增 `.dockerignore`，排除 `node_modules/dist/.vite/.env` |
| 2 | `COPY . .` 后构建失败（原生模块平台不匹配） | 同上：宿主 Windows 版 esbuild/rollup 覆盖了容器内 Linux 版 | 同上；上下文从 190MB 降到 **0.6MB** |
| 3 | `pnpm build` 报 `packages field missing or empty` | `frontend/pnpm-workspace.yaml` 只有 `onlyBuiltDependencies`、没有 `packages`，pnpm 当成工作区根 | 配置移入 `package.json` 的 `pnpm` 字段，删除该文件 |
| 4 | 依赖安装不可复现 | `Dockerfile` 用 `npm ci/npm install`，但仓库只有 `pnpm-lock.yaml` | 改为 `corepack prepare pnpm@9` + `pnpm install --frozen-lockfile` + `pnpm build` |

## 三、首次启动暴露的问题

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 5 | `tp-mosquitto` 持续重启 | `mosquitto.conf` 用了 `keepalive_interval`——**bridge 专用参数**，broker 配置里写它会直接启动失败 | 改为 `max_keepalive 120`；`message_size_limit` → `max_packet_size`（2.x 推荐） |
| 6 | 全新库下 admin 登录成功但**权限为 0**，除考勤外几乎全部 403 | `BootstrapDataInitializer` 用旧角色值 `"admin"` 建用户；RBAC 只认 `role` 表的 key（`sysadmin`），查不到角色 → 权限集合为空 | 改为 `sysadmin`；新增迁移 **V7** 把历史库的 `admin/member/空值` 归一为 `sysadmin/employee` |
| 7 | admin 的部门为空（`dept=undefined`） | 迁移先跑、bootstrap 后建用户，`V4` 的部门回填覆盖不到新用户 | bootstrap 建号时挂到根部门（`dept_code = ROOT`，即总经办） |

## 四、验证结果（清空数据卷后的全新启动）

```
docker compose down -v && docker compose up -d

NAME           STATUS                    PORTS
tp-backend     Up (healthy)              8080/tcp
tp-frontend    Up (healthy)              0.0.0.0:80->80/tcp
tp-mosquitto   Up (healthy)              0.0.0.0:1883->1883/tcp
tp-mysql       Up (healthy)              127.0.0.1:3306->3306/tcp
tp-redis       Up (healthy)              6379/tcp
```

| 检查项 | 结果 |
|---|---|
| 镜像构建 | `teampulse/backend:1.0.0`（478MB）、`teampulse/frontend:1.0.0`（78MB） |
| Flyway | V1~V7 全部 success，21 张表 |
| 种子数据 | 5 部门 / 6 角色 / 22 权限点 |
| 管理员登录 | HTTP 200，`role=sysadmin`、`perms=22`、`dept=总经办`、`scope=ALL` |
| 关键接口 | `/user/list`、`/audit`、`/devices`、`/attendance/rule`、`/dashboard/admin` 全部 200 |
| 前端 | `/` 返回 200 且含 `#app`；`/healthz` 返回 `ok` |
| MQTT | 后端以 `teampulse-backend-1` 成功连上 broker；设备订阅/发布可用 |
| 演示设备 | `TP-ESP32-DEMO`（OFFLINE，等第一次心跳/打卡） |

## 五、耗时记录（供后续部署估算）

| 阶段 | 耗时 |
|---|---|
| 前端镜像构建（含 pnpm 安装） | 约 40s |
| 后端镜像构建（首次，`dependency:go-offline` 269s） | 约 5~6 分钟 |
| 后端镜像重建（依赖已缓存） | 约 1~2 分钟 |
| `compose up` 到 5 容器 healthy | 约 60~90 秒 |
| 清空卷后的全新启动到 healthy | 约 90 秒 |

## 六、还没验证的（云服务器相关）

| 项 | 说明 |
|---|---|
| 云服务器本身 | 需要一台 4C4G Ubuntu；本机验证只证明「同一套资产能跑起来」 |
| 域名 + HTTPS | `frontend/nginx.conf` 的 443 段仍是注释状态，未用真实证书验证 |
| MQTT over TLS | 当前只有 1883 + 密码认证；公网暴露设备接入时应加 TLS |
| 公网安全组 | 需放行 80/1883（或 8883），3306 已绑回环 |
| 备份恢复演练 | `backup.sh` 未在容器环境实跑一次「备份→删库→恢复」 |
| 演示数据 | 全新库只有 admin 与一台演示设备；`zhangsan/lisi` 等演示账号来自开发库，未做成种子脚本 |

## 七、下一步（按顺序）

1. 在服务器上重复本流程（预计 1~2 小时，主要是镜像构建等待）
2. 配 Nginx 443 + 证书（有域名时）
3. 加 MQTT TLS（8883）与安全组收敛
4. 做一次备份恢复演练并记录
5. 把演示账号做成 `deploy/sql/seed-demo.sql`（非迁移，仅演示环境使用）
"""
open(os.path.join(ROOT, "docs", "部署验证记录.md"), "w", encoding="utf-8").write(doc)
print("已生成 docs/部署验证记录.md")

# ---------------------------------------------------------------- 2. README 更新
rp = os.path.join(ROOT, "README.md")
src = open(rp, encoding="utf-8").read()
n = 0

old = """> ⚠️ **诚实说明**：这套 compose 与镜像构建**尚未在真实服务器上跑通过**（本地无 Docker 环境）。
> 首次在服务器上部署时，预计需要处理 1~2 轮构建/依赖问题。详见第 12 章「项目状态」。"""
new = """> **已验证**：本机（Windows 11 + Docker Desktop/WSL2）已用它跑通 5 容器全链路，
> 清空数据卷后全新启动同样通过（Flyway V1~V7、21 表、admin 登录 22 权限点、关键接口全 200），
> 过程与踩坑见 [`docs/部署验证记录.md`](docs/部署验证记录.md)。
>
> **尚未验证**：真实云服务器、域名/HTTPS、MQTT TLS、备份恢复演练。"""
if old in src:
    src = src.replace(old, new); n += 1

old = "| **云端部署** | ❌ **未执行**：资产齐全但从未在真实服务器跑通（缺服务器/域名/HTTPS） |"
new = ("| **本地容器化** | ✅ **已跑通**：镜像构建 + 5 容器 healthy + 全新库启动验证（见部署验证记录） |\n"
       "| **云端部署** | ❌ **未执行**：需要服务器；本机验证只证明同一套资产能起来 |")
if old in src:
    src = src.replace(old, new); n += 1

old = "| 文档 | 内容 |"
# 文档表里补一行部署验证记录
old2 = "| `docs/diagrams/` | 用例图、ER 图（PlantUML 源文件 + PNG/SVG，v1 尚未包含 RBAC 新增表） |"
new2 = ("| [`docs/部署验证记录.md`](docs/部署验证记录.md) | 本机首次容器化的环境准备、7 个问题的根因与修复、验证证据、耗时、未验证项 |\n"
        "| `docs/diagrams/` | 用例图、ER 图（PlantUML 源文件 + PNG/SVG，v1 尚未包含 RBAC 新增表） |")
if old2 in src:
    src = src.replace(old2, new2); n += 1

open(rp, "w", encoding="utf-8", newline="\n").write(src)
print(f"README 更新 {n} 处")

# ---------------------------------------------------------------- 3. 交接文档
hp = os.path.join(ROOT, "docs", "TeamPulse_全过程交接文档.md")
src = open(hp, encoding="utf-8").read()
old = "| **是否真跑过** | ❌ **从未**：本机无 Docker，镜像构建与 compose 启动均未验证 |"
new = "| **是否真跑过** | ✅ 2026-10-27 本机首次跑通（详见 `docs/部署验证记录.md`）；云服务器仍未验证 |"
n2 = 0
if old in src:
    src = src.replace(old, new); n2 += 1

add = """

### 16.6 本机容器化验证（2026-10-27 补充）

首次在 Windows 11 + Docker Desktop（WSL2）跑通 compose，修掉 7 个问题，完整记录见 `docs/部署验证记录.md`。摘要：

| 分类 | 问题 | 修复 |
|---|---|---|
| 环境 | winget 安装被拒（无管理员）、WSL 未装、Docker Hub 不可达 | 管理员启用 WSL → 重启 → `wsl --update`；`daemon.json` 配 registry-mirrors |
| 构建 | 缺 `frontend/.dockerignore`（193MB 上下文 + 平台不匹配） | 新增 .dockerignore，上下文降到 0.6MB |
| 构建 | `pnpm-workspace.yaml` 只有 `onlyBuiltDependencies` 无 `packages` | 移入 `package.json` 的 `pnpm` 字段并删除该文件 |
| 构建 | Dockerfile 用 npm 安装，绕过 pnpm 锁文件 | 改 corepack + `pnpm install --frozen-lockfile` |
| 运行 | mosquitto 用 bridge 专用参数 `keepalive_interval` 导致重启循环 | 改 `max_keepalive 120` + `max_packet_size` |
| 功能 | **全新库 admin 权限为 0**（bootstrap 写旧角色值 `admin`） | 改 `sysadmin`，并新增 V7 归一历史角色值 |
| 功能 | admin 部门为空 | bootstrap 建号时挂到根部门（总经办） |

验证证据：清空数据卷后 `compose up` → 5 容器 healthy；Flyway V1~V7；admin 登录返回 `role=sysadmin`、22 权限点、`dept=总经办`、`scope=ALL`；`/user/list`、`/audit`、`/devices`、`/attendance/rule`、`/dashboard/admin` 全 200；后端以 `teampulse-backend-1` 连上 MQTT。
"""
if "16.6 本机容器化验证" not in src:
    src = src.rstrip() + add
    n2 += 1

open(hp, "w", encoding="utf-8", newline="\n").write(src)
print(f"交接文档更新 {n2} 处")
