#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成系统图册所需的 9 张图（PNG，直接嵌入 Word）。

为什么用 Pillow 手绘而不是 Mermaid/PlantUML：
  本机没有可用的 mermaid-cli（需要 puppeteer/chromium），PlantUML 需要额外 jar；
  Pillow 是随环境自带的，且能精确控制中文换行与版式。
  图册附录里仍保留 Mermaid/PlantUML 源码，便于日后用专业工具重绘。

用法：
    python scripts/docs/make_diagrams.py --out docs/diagrams
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont

# ---------------------------------------------------------------- 视觉规范
BG = "#FFFFFF"
INK = "#1F2937"
PRIMARY = "#1F3864"
ACCENT = "#2E75B6"
LIGHT = "#DCE9F7"
MINT = "#E2F0E6"
WARN = "#FDECEA"
GRAY = "#F3F4F6"
BORDER = "#8EA9C1"
LINE = "#5B6B7F"

FONT_DIR = r"C:\Windows\Fonts"
F_REG = os.path.join(FONT_DIR, "msyh.ttc")
F_BOLD = os.path.join(FONT_DIR, "msyhbd.ttc")

SCALE = 2  # 2x 超采样，嵌进 Word 更清晰


def font(size, bold=False):
    return ImageFont.truetype(F_BOLD if bold else F_REG, size * SCALE)


class Canvas:
    def __init__(self, w, h, title=None):
        self.w, self.h = w, h
        self.img = Image.new("RGB", (w * SCALE, h * SCALE), BG)
        self.d = ImageDraw.Draw(self.img)
        if title:
            self.text(w // 2, 26, title, size=20, bold=True, anchor="mm", color=PRIMARY)

    def text(self, x, y, s, size=11, bold=False, color=INK, anchor="la"):
        self.d.text((x * SCALE, y * SCALE), s, font=font(size, bold), fill=color, anchor=anchor)

    def wrap(self, s, max_chars):
        lines, cur = [], ""
        for ch in s:
            cur += ch
            if len(cur) >= max_chars:
                lines.append(cur)
                cur = ""
        if cur:
            lines.append(cur)
        return lines

    def box(self, x, y, w, h, label, sub=None, fill=LIGHT, border=BORDER, radius=6,
            size=11, bold=False, color=INK, sub_size=9, align="center"):
        self.d.rounded_rectangle([x * SCALE, y * SCALE, (x + w) * SCALE, (y + h) * SCALE],
                                 radius=radius * SCALE, fill=fill, outline=border, width=SCALE)
        lines = self.wrap(label, max(4, int(w / (size * 0.62))))
        total = len(lines) * (size + 3) + (sub.count("\n") + 1) * (sub_size + 2) if sub else len(lines) * (size + 3)
        cy = y + h / 2 - total / 2 + size / 2
        for ln in lines:
            self.text(x + w / 2, cy, ln, size=size, bold=bold, color=color, anchor="mm")
            cy += size + 4
        if sub:
            for ln in sub.split("\n"):
                self.text(x + w / 2, cy + 2, ln, size=sub_size, color="#4B5563", anchor="mm")
                cy += sub_size + 3

    def band(self, x, y, w, h, label, fill=GRAY, border="#D1D5DB"):
        self.d.rounded_rectangle([x * SCALE, y * SCALE, (x + w) * SCALE, (y + h) * SCALE],
                                 radius=8 * SCALE, fill=fill, outline=border, width=SCALE)
        self.text(x + 12, y + 10, label, size=10, bold=True, color=PRIMARY)

    def arrow(self, x1, y1, x2, y2, label=None, color=LINE, width=1, dashed=False, size=9):
        if dashed:
            self._dashed_line(x1, y1, x2, y2, color, width)
        else:
            self.d.line([x1 * SCALE, y1 * SCALE, x2 * SCALE, y2 * SCALE], fill=color,
                        width=max(1, int(width * SCALE)))
        self._arrow_head(x1, y1, x2, y2, color, width)
        if label:
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2
            self.text(mx, my - 9, label, size=size, color="#374151", anchor="mm")

    def _dashed_line(self, x1, y1, x2, y2, color, width):
        import math
        dx, dy = x2 - x1, y2 - y1
        dist = math.hypot(dx, dy)
        if dist == 0:
            return
        ux, uy = dx / dist, dy / dist
        step, gap, pos = 0, 8, 0
        while pos < dist:
            end = min(pos + gap, dist)
            self.d.line([(x1 + ux * pos) * SCALE, (y1 + uy * pos) * SCALE,
                         (x1 + ux * end) * SCALE, (y1 + uy * end) * SCALE], fill=color,
                        width=max(1, int(width * SCALE)))
            pos = end + 6

    def _arrow_head(self, x1, y1, x2, y2, color, width):
        import math
        ang = math.atan2(y2 - y1, x2 - x1)
        size = 9
        for sign in (0.4, -0.4):
            xa = x2 - size * math.cos(ang - sign)
            ya = y2 - size * math.sin(ang - sign)
            self.d.line([x2 * SCALE, y2 * SCALE, xa * SCALE, ya * SCALE], fill=color,
                        width=max(1, int(width * SCALE)))

    def actor(self, cx, cy, name, size=10):
        r = 7
        self.d.ellipse([(cx - r) * SCALE, (cy - 22) * SCALE, (cx + r) * SCALE, (cy - 8) * SCALE],
                       outline=PRIMARY, width=2 * SCALE, fill=BG)
        self.d.line([cx * SCALE, (cy - 8) * SCALE, cx * SCALE, (cy + 8) * SCALE], fill=PRIMARY, width=2 * SCALE)
        self.d.line([(cx - 12) * SCALE, (cy - 2) * SCALE, (cx + 12) * SCALE, (cy - 2) * SCALE], fill=PRIMARY, width=2 * SCALE)
        self.d.line([cx * SCALE, (cy + 8) * SCALE, (cx - 10) * SCALE, (cy + 20) * SCALE], fill=PRIMARY, width=2 * SCALE)
        self.d.line([cx * SCALE, (cy + 8) * SCALE, (cx + 10) * SCALE, (cy + 20) * SCALE], fill=PRIMARY, width=2 * SCALE)
        self.text(cx, cy + 30, name, size=size, bold=True, color=PRIMARY, anchor="mm")

    def ellipse(self, cx, cy, rx, ry, label, fill=MINT, border=BORDER, size=10):
        self.d.ellipse([(cx - rx) * SCALE, (cy - ry) * SCALE, (cx + rx) * SCALE, (cy + ry) * SCALE],
                       fill=fill, outline=border, width=SCALE)
        lines = self.wrap(label, max(6, int(rx / (size * 0.55))))
        cy0 = cy - (len(lines) - 1) * (size // 2)
        for i, ln in enumerate(lines):
            self.text(cx, cy0 + i * (size + 1), ln, size=size, anchor="mm")

    def cylinder(self, x, y, w, h, label, sub=None, size=11):
        e = 10
        self.d.rectangle([x * SCALE, (y + e) * SCALE, (x + w) * SCALE, (y + h - e) * SCALE],
                         fill=MINT, outline=BORDER, width=SCALE)
        self.d.ellipse([x * SCALE, y * SCALE, (x + w) * SCALE, (y + e * 2) * SCALE], fill=MINT, outline=BORDER, width=SCALE)
        self.d.ellipse([x * SCALE, (y + h - e * 2) * SCALE, (x + w) * SCALE, (y + h) * SCALE], fill=MINT, outline=BORDER, width=SCALE)
        self.text(x + w / 2, y + h / 2 - (4 if sub else 0), label, size=size, bold=True, anchor="mm")
        if sub:
            self.text(x + w / 2, y + h / 2 + 12, sub, size=9, color="#4B5563", anchor="mm")

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.resize((self.w, self.h), Image.LANCZOS).save(path, "PNG")
        return path


# ---------------------------------------------------------------- 1. 系统架构图
def arch(path):
    c = Canvas(1500, 820, "图 1　TeamPulse 系统架构图")
    # 用户与设备
    c.actor(120, 120, "员工 / 经理 / HR / 审计")
    c.box(60, 210, 320, 54, "浏览器（Vue 3 SPA）", "Element Plus · Pinia · ECharts", fill=GRAY)
    c.box(60, 300, 320, 54, "ESP32 打卡机", "UART 读卡 · HMAC 签名", fill=GRAY)
    c.box(60, 390, 320, 54, "设备模拟器 / curl", "联调与回归用", fill=GRAY)

    # 接入层
    c.band(430, 190, 300, 300, "接入层")
    c.box(455, 240, 250, 60, "Nginx", "静态托管 · 反向代理 · 同源")
    c.box(455, 325, 250, 60, "Mosquitto", "MQTT 1883 · 密码认证")
    c.box(455, 410, 250, 52, "HTTP 兜底 /iot/v1", "同一套验签", fill=WARN)

    # 应用层
    c.band(780, 120, 420, 480, "应用层（Spring Boot 单体）")
    c.box(805, 165, 370, 46, "安全过滤器链 JWT + @PreAuthorize + 数据范围", fill=LIGHT)
    c.box(805, 225, 175, 56, "协作域", "用户/团队/日程\n看板/日志/评论")
    c.box(1000, 225, 175, 56, "考勤域", "设备/规则/打卡\n补卡审批")
    c.box(805, 300, 175, 56, "权限域", "角色/权限\n数据范围", )
    c.box(1000, 300, 175, 56, "审计域", "AOP 切面\n审计查询")
    c.box(805, 375, 175, 56, "概览统计", "仪表盘/公司概览")
    c.box(1000, 375, 175, 56, "横切能力", "traceId · 指标\n统一异常")
    c.box(805, 450, 370, 46, "MyBatis-Plus 持久层", fill=GRAY)
    c.box(805, 515, 370, 52, "定时结算 00:05 · 日汇总重算", fill=GRAY)

    # 数据层
    c.band(1250, 190, 220, 300, "数据层")
    c.cylinder(1265, 250, 190, 80, "MySQL 8.0", "21 表 · utf8mb4")
    c.cylinder(1265, 355, 190, 80, "Redis 7", "nonce 防重放")

    # 观测
    c.band(430, 560, 300, 200, "可观测（可选）")
    c.box(455, 610, 250, 52, "Prometheus", "抓取 /actuator/prometheus")
    c.box(455, 685, 250, 52, "Grafana", "7 个面板 · 只绑回环")

    # 连线
    c.arrow(380, 235, 455, 265, "HTTP 80")
    c.arrow(380, 327, 455, 350, "MQTT 1883")
    c.arrow(380, 415, 455, 430, "HTTP")
    c.arrow(705, 270, 780, 240, "静态 + /api")
    c.arrow(705, 355, 805, 250, "attendance/checkin")
    c.arrow(705, 436, 805, 470, "")
    c.arrow(1195, 300, 1265, 285, "JDBC")
    c.arrow(1195, 340, 1265, 380, "SET NX")
    c.arrow(705, 630, 805, 500, "指标", dashed=True)
    c.arrow(1250, 290, 1195, 290, "", dashed=True)
    return c.save(os.path.join(path, "fig1-architecture.png"))


# ---------------------------------------------------------------- 2. 部署图
def deploy(path):
    c = Canvas(1500, 880, "图 2　部署图（腾讯云轻量 4C4G · Rocky Linux 10）")
    c.d.rounded_rectangle([40 * SCALE, 90 * SCALE, 1460 * SCALE, 800 * SCALE],
                          radius=14 * SCALE, outline=PRIMARY, width=2 * SCALE, fill="#FAFCFF")
    c.text(60, 105, "云服务器 81.70.102.116（公网 IP）· ap-beijing · Docker 29.9", size=12, bold=True, color=PRIMARY)
    c.text(60, 130, "系统盘 40G · 内存 3.7G + 2G swap · firewalld 关闭（用腾讯云控制台防火墙）", size=9, color="#4B5563")

    c.band(70, 175, 1390, 330, "Docker 网络 teampulse_default")
    c.box(95, 235, 250, 110, "tp-frontend", "nginx:1.27-alpine\n80:80（唯一对公网）\n托管 SPA + 反代 /api /iot", fill=LIGHT)
    c.box(370, 235, 250, 110, "tp-backend", "teampulse/backend:1.0.0\n8080（仅内网 expose）\nSpring Boot 单体", fill=LIGHT)
    c.box(645, 235, 250, 110, "tp-mysql", "mysql:8.0\n127.0.0.1:3306\n数据卷 mysql-data", fill=MINT)
    c.box(920, 235, 250, 110, "tp-redis", "redis:7-alpine\n6379（仅内网）\n128M LRU", fill=MINT)
    c.box(1195, 235, 240, 110, "tp-mosquitto", "eclipse-mosquitto:2\n1883（对公网）\n密码文件挂载", fill=MINT)
    c.box(95, 375, 250, 100, "tp-prometheus", "profile=monitoring\n127.0.0.1:9090", fill=GRAY)
    c.box(370, 375, 250, 100, "tp-grafana", "profile=monitoring\n127.0.0.1:3000", fill=GRAY)
    c.box(645, 375, 490, 100, "数据卷", "mysql-data / redis-data / mosquitto-data / backend-logs / prometheus-data / grafana-data", fill=GRAY)

    c.arrow(345, 265, 370, 265)
    c.arrow(620, 265, 645, 265)
    c.arrow(620, 300, 920, 300, "缓存")
    c.arrow(895, 265, 920, 265)
    c.arrow(1195, 300, 1195, 375)
    c.arrow(345, 425, 370, 425)

    # 外部
    c.actor(200, 640, "浏览器")
    c.box(320, 600, 300, 60, "公网访问 http://81.70.102.116/", "80 端口 · 防火墙已放行", fill=WARN)
    c.actor(760, 640, "ESP32 打卡机")
    c.box(880, 600, 320, 60, "MQTT 1883", "需在腾讯云控制台放行", fill=WARN)
    c.box(1240, 600, 220, 60, "SSH 22", "密钥登录 · 运维通道", fill=GRAY)
    c.arrow(240, 660, 320, 630)
    c.arrow(800, 660, 880, 630)
    c.arrow(1260, 620, 1195, 350)

    c.text(70, 740, "说明：8080/3000/9090 均不对公网开放；查看监控请用 SSH 隧道 "
                    "ssh -L 3000:127.0.0.1:3000 root@81.70.102.116", size=9, color="#4B5563")
    return c.save(os.path.join(path, "fig2-deployment.png"))


# ---------------------------------------------------------------- 3. 业务流程图（泳道）
def flow(path):
    c = Canvas(1500, 1000, "图 3　业务流程图：设备打卡上报（含异常分支）")
    lanes = [("设备（ESP32）", 120), ("消息/接入", 250), ("后端服务", 560), ("存储", 820)]
    for name, y in lanes:
        c.d.rectangle([60 * SCALE, y * SCALE, 1440 * SCALE, (y + 120) * SCALE], fill="#FBFCFE", outline="#D1D5DB", width=SCALE)
        c.text(72, y + 10, name, size=10, bold=True, color=PRIMARY)

    steps = [
        (120, "读卡 → 组 JSON", LIGHT), (120, "HMAC 签名", LIGHT), (120, "写入 nonce", LIGHT),
        (250, "MQTT 发布\nattendance/checkin", MINT), (250, "Nginx / Mosquitto", MINT),
        (560, "查设备密钥", LIGHT), (560, "验签 + 时钟漂移(±300s)", WARN),
        (560, "nonce 去重（SET NX）", WARN), (560, "工号 → 用户", LIGHT),
        (560, "按规则判定状态", LIGHT), (560, "唯一索引防重复", WARN),
        (820, "写 attendance_record", MINT), (820, "刷新 attendance_daily", MINT),
    ]
    positions = {}
    x = 100
    for lane_y, label, fill in steps:
        y = lane_y + 40
        w = 190 if lane_y != 560 else 200
        c.box(x, y, w, 62, label, fill=fill, size=10)
        positions[label] = (x, y, w, 62)
        x += w + 26
        if x > 1300:
            x = 100

    # 主流程箭头（按阅读顺序）
    order = [s[1] for s in steps]
    for a, b in zip(order, order[1:]):
        xa, ya, wa, ha = positions[a]
        xb, yb, wb, hb = positions[b]
        c.arrow(xa + wa, ya + ha / 2, xb, yb + hb / 2, width=1)

    # 失败分支
    c.box(100, 700, 220, 56, "失败：60003 签名错\n60006 时钟漂移", fill=WARN, size=9)
    c.box(350, 700, 220, 56, "失败：60004 重放\n或 幂等返回 200", fill=WARN, size=9)
    c.box(600, 700, 240, 56, "失败：61001 工号不存在\n61002 未配置规则", fill=WARN, size=9)
    c.arrow(positions["验签 + 时钟漂移(±300s)"][0] + 60, 100 + 40 + 62, 210, 700, "不通过", dashed=True)
    c.arrow(positions["nonce 去重（SET NX）"][0] + 60, 100 + 40 + 62, 460, 700, "已存在", dashed=True)
    c.arrow(positions["工号 → 用户"][0] + 60, 100 + 40 + 62, 720, 700, "查无", dashed=True)
    c.text(100, 800, "注：MQTT 与 HTTP 两个入口共用同一段处理逻辑（AttendanceService.handlePunch），"
                     "source 字段区分 MQTT/HTTP。", size=9, color="#4B5563")
    return c.save(os.path.join(path, "fig3-business-flow.png"))


# ---------------------------------------------------------------- 4. 用例图
def usecase(path):
    c = Canvas(1500, 1000, "图 4　用例图（6 个角色）")
    roles = [("系统管理员\nsysadmin", 120), ("人事\nhr", 300), ("部门经理\nmanager", 480),
             ("团队负责人\nleader", 660), ("普通员工\nemployee", 830), ("审计专员\nauditor", 960)]
    for name, y in roles:
        c.actor(150, y + 30, name)

    cases = [
        ("登录 / 个人信息", 120, 0), ("用户与角色管理", 120, 1), ("设备注册与密钥轮换", 120, 2),
        ("考勤规则配置", 300, 0), ("补卡审批", 300, 1), ("公司考勤概览", 300, 2),
        ("部门成员打卡查询", 480, 0), ("团队日程管理", 480, 1), ("评论与敏感词", 480, 2),
        ("团队与成员维护", 660, 0), ("看板协作", 660, 1), ("日程 / 日志查看", 660, 2),
        ("我的打卡与补卡申请", 830, 0), ("我的日程 / 日志 / 看板", 830, 1), ("审计日志查询（全公司只读）", 960, 0),
    ]
    xs = [430, 830, 1180]
    for label, y, col in cases:
        cx = xs[col]
        c.ellipse(cx, y + 40, 150, 34, label, size=10)
        # 角色 → 用例
        for rname, ry in roles:
            pass

    # 连线：按业务对应关系
    links = [
        (120, 0), (120, 1), (120, 2),
        (300, 0), (300, 1), (300, 2),
        (480, 0), (480, 1), (480, 2),
        (660, 0), (660, 1), (660, 2),
        (830, 0), (830, 1),
        (960, 0), (960, 1),
    ]
    role_y = {0: 150, 1: 330, 2: 510, 3: 690, 4: 860, 5: 990}
    for r_idx, col in [(0, 0), (0, 1), (0, 2), (1, 0), (1, 1), (1, 2), (2, 0), (2, 1), (2, 2),
                       (3, 0), (3, 1), (3, 2), (4, 0), (4, 1), (5, 0), (5, 0)]:
        pass
    mapping = [
        ((0, 0), (120, 0)), ((0, 0), (120, 1)), ((0, 0), (120, 2)),
        ((1, 0), (300, 0)), ((1, 0), (300, 1)), ((1, 0), (300, 2)),
        ((2, 0), (480, 0)), ((2, 0), (480, 1)), ((2, 0), (480, 2)),
        ((3, 0), (660, 0)), ((3, 0), (660, 1)), ((3, 0), (660, 2)),
        ((4, 0), (830, 0)), ((4, 0), (830, 1)),
        ((5, 0), (960, 0)),
    ]
    case_pos = {(y, col): (xs[col], y + 40) for label, y, col in cases}
    role_x = 150
    for (r_idx, _), (cy, col) in mapping:
        ry = role_y[r_idx]
        cx, cyy = case_pos[(cy, col)]
        c.arrow(role_x + 24, ry, cx - 150, cyy, width=1)
    c.text(70, 940, "说明：连到「补卡审批」的角色为人事/经理/超管；普通员工只能提交补卡申请。"
                    "审计专员全部为只读。", size=9, color="#4B5563")
    return c.save(os.path.join(path, "fig4-use-case.png"))


# ---------------------------------------------------------------- 5. 时序图
def sequence(path):
    c = Canvas(1500, 900, "图 5　时序图：打卡上报（验签 → 防重放 → 幂等落库）")
    actors = [("ESP32", 120), ("Mosquitto", 380), ("Backend", 660), ("Redis", 940), ("MySQL", 1200)]
    for name, x in actors:
        c.box(x - 80, 70, 160, 46, name, fill=LIGHT, size=11, bold=True)
        c.d.line([x * SCALE, 116 * SCALE, x * SCALE, 830 * SCALE], fill="#9CA3AF", width=SCALE)

    msgs = [
        (0, 1, "publish checkin{sn,emp,time,nonce,sign}", 170, False),
        (1, 2, "投递（订阅者 teampulse-backend-1）", 215, False),
        (2, 4, "按 deviceSn 取 secret", 265, False),
        (2, 2, "校验 HMAC 与时钟漂移(±300s)", 315, True),
        (2, 3, "SET NX nonce EX 600", 365, False),
        (3, 2, "首次=true", 410, True),
        (2, 4, "INSERT attendance_record", 465, False),
        (4, 2, "唯一键冲突 → 幂等返回 200", 515, True),
        (2, 4, "刷新 attendance_daily", 565, False),
        (2, 2, "指标 punch_total{result}", 615, True),
        (2, 1, "Ack(id,status,workDate)", 665, True),
    ]
    for src, dst, label, y, dashed in msgs:
        x1, x2 = actors[src][1], actors[dst][1]
        if abs(x1 - x2) < 5:  # 自调用
            c.d.line([x1 * SCALE, y * SCALE, (x1 + 60) * SCALE, y * SCALE], fill=LINE, width=SCALE)
            c.d.line([(x1 + 60) * SCALE, y * SCALE, (x1 + 60) * SCALE, (y + 26) * SCALE], fill=LINE, width=SCALE)
            c.arrow(x1 + 60, y + 26, x1, y + 26)
            c.text(x1 + 70, y - 6, label, size=9, color="#374151")
        else:
            c.arrow(x1, y, x2, y, label, dashed=dashed, size=9)
    c.text(110, 730, "① 幂等三层：message_id 唯一索引（报文重投）/ nonce 防重放（攻击）/ "
                     "employee_no+punch_time 唯一索引（业务重复）", size=9, color="#4B5563")
    c.text(110, 760, "② 验签失败 60003、时钟漂移 60006、重放 60004、工号不存在 61001、规则缺失 61002", size=9, color="#4B5563")
    return c.save(os.path.join(path, "fig5-sequence.png"))


# ---------------------------------------------------------------- 6. 状态图
def state(path):
    c = Canvas(1500, 820, "图 6　状态图：补卡审批 与 设备状态")
    # 补卡
    c.text(90, 80, "（a）补卡申请状态机", size=12, bold=True, color=PRIMARY)
    c.box(120, 210, 150, 60, "● 提交", "pending", fill=LIGHT)
    c.box(390, 210, 150, 60, "审批中", "pending", fill=WARN)
    c.box(690, 140, 160, 60, "已通过", "approved", fill=MINT)
    c.box(690, 290, 160, 60, "已驳回", "rejected", fill=WARN)
    c.box(960, 140, 180, 60, "回写打卡记录", "source=MANUAL\n刷新日汇总", fill=LIGHT)
    c.arrow(270, 240, 390, 240, "提交补卡")
    c.arrow(540, 220, 690, 175, "approved=true")
    c.arrow(540, 260, 690, 315, "approved=false")
    c.arrow(850, 170, 960, 170, "事务内")
    c.arrow(960, 200, 690, 200, "重复审批 → 409", dashed=True)

    # 设备
    c.text(90, 440, "（b）设备状态", size=12, bold=True, color=PRIMARY)
    c.box(180, 520, 170, 60, "未注册", "出厂/新设备", fill=GRAY)
    c.box(460, 520, 170, 60, "已注册", "active / OFFLINE", fill=MINT)
    c.box(760, 520, 170, 60, "已禁用", "DISABLED", fill=WARN)
    c.box(1060, 520, 200, 60, "密钥轮换", "旧密钥立即失效", fill=LIGHT)
    c.arrow(350, 550, 460, 550, "后台注册 → 下发密钥")
    c.arrow(630, 540, 760, 540, "停用")
    c.arrow(760, 560, 630, 570, "启用", dashed=True)
    c.arrow(630, 610, 1060, 590, "轮换密钥（仅 active）")
    c.text(90, 690, "在线状态由心跳更新（MQTT attendance/heartbeat，60s 一次）；"
                    "超过 180s 无心跳标记 OFFLINE。", size=9, color="#4B5563")
    return c.save(os.path.join(path, "fig6-state.png"))


# ---------------------------------------------------------------- 7. 类图
def classdiag(path):
    c = Canvas(1500, 800, "图 7　类图：核心领域模型（节选，字段已简化）")
    def cls(x, y, w, name, fields, color=LIGHT):
        h = 30 + len(fields) * 17
        c.d.rectangle([x * SCALE, y * SCALE, (x + w) * SCALE, (y + h) * SCALE], fill=color, outline=BORDER, width=SCALE)
        c.d.line([x * SCALE, (y + 30) * SCALE, (x + w) * SCALE, (y + 30) * SCALE], fill=BORDER, width=SCALE)
        c.text(x + w / 2, y + 15, name, size=11, bold=True, anchor="mm", color=PRIMARY)
        for i, f in enumerate(fields):
            c.text(x + 10, y + 36 + i * 17, f, size=9, color=INK)
        return (x, y, w, h)

    u = cls(80, 120, 250, "User", ["userId: Long", "username: String", "role: String", "departmentId: Long", "employeeNo: String"])
    d = cls(80, 280, 250, "Department", ["departmentId: Long", "parentId: Long", "deptCode: String", "leaderId: Long"])
    r = cls(80, 430, 250, "Role", ["roleKey: String", "roleName: String", "dataScope: ALL/DEPT/SELF"])
    p = cls(80, 580, 250, "Permission", ["permKey: String", "permName: String", "module: String"])

    t = cls(420, 120, 250, "Team", ["teamId: Long", "teamName: String", "leaderId: Long"])
    tm = cls(420, 260, 250, "TeamMember", ["teamId: Long", "userId: Long", "teamRole: leader/member"])
    s = cls(420, 400, 250, "Schedule", ["scheduleId: Long", "creatorId: Long", "teamId: Long", "startTime: Date"])
    k = cls(420, 540, 250, "Kanban", ["kanbanId: Long", "teamId: Long", "creatorId: Long"])

    dev = cls(760, 120, 260, "IotDevice", ["deviceSn: String", "secret: String(64hex)", "status: active/DISABLED"])
    rec = cls(760, 250, 260, "AttendanceRecord", ["recordId: Long", "userId: Long", "deviceSn: String", "punchTime: DateTime", "messageId: String(UK)"])
    day = cls(760, 420, 260, "AttendanceDaily", ["userId: Long", "workDate: Date", "status: NORMAL/LATE/…"])
    mk = cls(760, 560, 260, "MakeupRequest", ["requestId: Long", "userId: Long", "status: pending/approved", "approverId: Long"])

    au = cls(1100, 120, 280, "AuditLog", ["auditId: Long", "operatorId: Long", "module: String", "action: String", "result: SUCCESS/FAILURE", "traceId: String"])
    wl = cls(1100, 300, 280, "Worklog", ["worklogId: Long", "creatorId: Long", "content: Text"])
    cm = cls(1100, 430, 280, "Comment", ["commentId: Long", "creatorId: Long", "relatedType: String", "relatedId: Long", "content: Text"])
    rp = cls(1100, 600, 280, "RolePermission", ["roleKey: String", "permKey: String"])

    # 关系
    c.arrow(205, 280, 205, 230, "1:N")            # Department → User
    c.arrow(205, 430, 205, 390, "N:1")            # User → Role
    c.arrow(420, 290, 330, 195, "成员", dashed=True)   # Team → TeamMember
    c.arrow(670, 160, 760, 160, "", dashed=True)
    c.arrow(760, 180, 670, 430, "上报", dashed=True)   # Device → Schedule(占位)
    c.arrow(1015, 300, 760, 330, "外键逻辑关联", dashed=True)  # Record → Device
    c.arrow(890, 420, 890, 560, "汇总", dashed=True)
    c.arrow(1015, 590, 1100, 470, "审批通过→写记录", dashed=True)
    c.text(80, 740, "说明：全库无物理外键，上面所有连线都是「逻辑关联」（见数据库设计说明书第 6 章）。"
                    "唯一索引承担业务约束：attendance_record(message_id)、(employee_no,punch_time)。", size=9, color="#4B5563")
    return c.save(os.path.join(path, "fig7-class.png"))


# ---------------------------------------------------------------- 8. 组件图
def component(path):
    c = Canvas(1500, 900, "图 8　组件图：前端组件与后端模块依赖")
    c.band(60, 90, 660, 700, "前端（Vue 3）")
    c.box(90, 140, 280, 70, "layouts", "AdminLayout\nUserLayout", fill=LIGHT)
    c.box(90, 240, 280, 90, "views（37 个页面）", "attendance / schedule / kanban\nteams / worklog / comment\nusers / audit / overview / user", fill=LIGHT)
    c.box(90, 360, 280, 70, "components（5 个）", "布局 · 图表 · 空状态", fill=GRAY)
    c.box(90, 460, 280, 80, "router（31 路由）", "meta.perms 逐页权限\n守卫拦截", fill=MINT)
    c.box(90, 570, 280, 80, "stores（Pinia）", "auth：用户/权限/hasPerm\nui：偏好面板", fill=MINT)
    c.box(410, 140, 280, 80, "api（Axios 封装）", "统一 token 注入\n统一错误分层", fill=MINT)
    c.box(410, 250, 280, 90, "utils", "netErrors · 字典 · 格式化", fill=GRAY)
    c.box(410, 370, 280, 70, "echarts / element-plus", "图表与组件库", fill=GRAY)

    c.band(780, 90, 660, 700, "后端（Spring Boot）")
    c.box(810, 140, 280, 70, "controller（16 个）", "@PreAuthorize 34 处\n统一 ApiResponse", fill=LIGHT)
    c.box(810, 240, 280, 130, "service（业务域）", "attendance · rbac · kanban\nteam · schedule · worklog\ncomment · dashboard · audit", fill=LIGHT)
    c.box(810, 400, 280, 80, "mapper + entity", "MyBatis-Plus\n21 张表", fill=GRAY)
    c.box(1120, 140, 280, 90, "config / security", "JWT 过滤链 · SecurityConfig\n可观测性 · OpenAPI · 启动初始化", fill=MINT)
    c.box(1120, 260, 280, 110, "横切组件", "AuditLogAspect（AOP）\nGlobalExceptionHandler\ntraceId + Micrometer", fill=MINT)
    c.box(1120, 400, 280, 80, "定时任务", "@Scheduled 00:05\n日汇总重算", fill=GRAY)

    c.arrow(370, 180, 410, 180, "")
    c.arrow(370, 280, 410, 300, "")
    c.arrow(550, 220, 810, 175, "HTTP /api/v1")
    c.arrow(690, 300, 810, 300, "调用")
    c.arrow(1090, 300, 1120, 300, "")
    c.arrow(950, 370, 950, 400, "")
    c.arrow(370, 500, 550, 500, "")
    c.arrow(550, 500, 1120, 500, "携带 token", dashed=True)
    c.text(60, 830, "说明：components 目录只有 5 个通用组件，业务页面直接写在 views 里——"
                    "这是单人项目刻意的取舍（少一层抽象，改起来更快）。", size=9, color="#4B5563")
    return c.save(os.path.join(path, "fig8-component.png"))


# ---------------------------------------------------------------- 9. 数据模型图
def datamodel(path):
    c = Canvas(1520, 1020, "图 9　数据模型图（21 表的核心关系，无物理外键）")
    def table(x, y, w, name, cols, color=LIGHT):
        h = 28 + len(cols) * 16
        c.d.rectangle([x * SCALE, y * SCALE, (x + w) * SCALE, (y + h) * SCALE], fill=color, outline=BORDER, width=SCALE)
        c.d.rectangle([x * SCALE, y * SCALE, (x + w) * SCALE, (y + 28) * SCALE], fill=PRIMARY, outline=BORDER, width=SCALE)
        c.text(x + w / 2, y + 14, name, size=10, bold=True, color="#FFFFFF", anchor="mm")
        for i, col in enumerate(cols):
            c.text(x + 8, y + 33 + i * 16, col, size=8.5, color=INK)
        return (x, y, w, h)

    dept = table(60, 110, 200, "department", ["department_id PK", "parent_id", "dept_name / dept_code", "leader_id"])
    user = table(60, 300, 200, "user", ["user_id PK", "username / password", "role (RBAC key)", "department_id", "employee_no"])
    role = table(60, 520, 200, "role", ["role_id PK", "role_key", "data_scope"])
    perm = table(60, 680, 200, "permission", ["permission_id PK", "perm_key", "module"])
    rp = table(60, 830, 200, "role_permission", ["id PK", "role_key", "perm_key"])

    team = table(340, 110, 210, "team", ["team_id PK", "team_name", "leader_id", "creator_id"])
    tm = table(340, 260, 210, "team_member", ["id PK", "team_id", "user_id", "team_role"])
    sched = table(340, 420, 210, "schedule", ["schedule_id PK", "creator_id", "team_id", "start_time / end_time"])
    kanban = table(340, 600, 210, "kanban", ["kanban_id PK", "team_id", "creator_id"])
    kcol = table(340, 740, 210, "kanban_column", ["column_id PK", "kanban_id", "sort_order"])
    kitem = table(340, 870, 210, "kanban_item", ["item_id PK", "column_id", "assignee_id"])

    dev = table(620, 110, 230, "iot_device", ["id PK", "device_sn (UK)", "secret (64 hex)", "status"])
    rec = table(620, 270, 230, "attendance_record", ["id PK", "user_id / employee_no", "device_sn", "punch_time", "message_id (UK)", "work_date + status"])
    daily = table(620, 480, 230, "attendance_daily", ["id PK", "user_id + work_date (UK)", "status / late_minutes"])
    rule = table(620, 640, 230, "attendance_rule", ["id PK", "work_start / work_end", "late_grace_minutes"])
    mk = table(620, 790, 230, "makeup_request", ["request_id PK", "user_id / approver_id", "status (pending/…)", "punch_date"])

    au = table(920, 110, 240, "audit_log", ["audit_id PK", "operator_id", "module / action", "result", "trace_id"])
    wl = table(920, 290, 240, "worklog", ["worklog_id PK", "creator_id", "content"])
    cm = table(920, 430, 240, "comment", ["comment_id PK", "creator_id", "related_type / related_id", "reply_to"])
    sw = table(920, 600, 240, "sensitive_word", ["word_id PK", "word", "enabled"])
    fh = table(920, 730, 240, "flyway_schema_history", ["installed_rank PK", "version", "success"], color=GRAY)

    # 关系连线
    c.arrow(160, 300, 160, 190, "1:N")
    c.arrow(160, 520, 160, 430, "N:1")
    c.arrow(260, 720, 260, 560, "N:N")
    c.arrow(260, 830, 260, 740, "")
    c.arrow(340, 140, 260, 190, "")
    c.arrow(340, 290, 260, 360, "团队成员")
    c.arrow(445, 260, 445, 420, "")
    c.arrow(445, 600, 445, 600, "")
    c.arrow(550, 180, 620, 180, "1:N")
    c.arrow(735, 270, 735, 250, "")
    c.arrow(660, 350, 620, 400, "设备上报")
    c.arrow(850, 350, 850, 480, "按天汇总")
    c.arrow(735, 480, 735, 480, "")
    c.arrow(850, 790, 850, 600, "审批通过写记录")
    c.arrow(260, 330, 920, 200, "操作留痕", dashed=True)
    c.arrow(260, 360, 920, 450, "发表评论", dashed=True)
    c.text(60, 960, "主键统一 bigint 自增；逻辑删除 deleted；无 FOREIGN KEY（见 ADR-02）。"
                    "唯一索引：attendance_record(message_id)、(employee_no,punch_time)、user(username/employee_no)、"
                    "attendance_daily(user_id,work_date)。", size=9, color="#4B5563")
    return c.save(os.path.join(path, "fig9-data-model.png"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    makers = [("架构图", arch), ("部署图", deploy), ("业务流程图", flow), ("用例图", usecase),
              ("时序图", sequence), ("状态图", state), ("类图", classdiag),
              ("组件图", component), ("数据模型图", datamodel)]
    for name, fn in makers:
        p = fn(args.out)
        print(f"  ✓ {name}: {os.path.basename(p)} ({os.path.getsize(p) // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
