#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成《TeamPulse 接口文档》DOCX。

两份事实源合并：
  1. .run-logs/openapi.json    —— 由 springdoc 从运行中的应用导出（路径/参数/模型）
  2. .run-logs/endpoints.json  —— 从控制器源码解析的 @PreAuthorize（OpenAPI 里没有）

用法：
    python scripts/docs/make_api_docx.py --openapi .run-logs/openapi.json \\
        --endpoints .run-logs/endpoints.json --out docs/TeamPulse_接口文档.docx
"""
import argparse
import json
import os
import re
import sys
from collections import Counter, OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import docx_common as C  # noqa: E402

METHODS = ("get", "post", "put", "delete", "patch")

# 模块中文名（按路径前缀）
MODULE_CN = {
    "auth": "认证", "user": "用户", "department": "部门", "role": "角色",
    "team": "团队", "schedule": "日程", "kanban": "看板", "worklog": "工作日志",
    "comment": "评论", "sensitive-word": "敏感词", "dashboard": "概览统计",
    "attendance": "考勤", "devices": "打卡设备", "makeup": "补卡审批", "audit": "审计日志",
}

ERROR_CODES = [
    (0, "ok", "成功"),
    (40000, "请求参数不合法", "参数校验失败（字段级 message 见响应）"),
    (40100, "未登录或登录已过期", "无 token / token 过期 / 密码错误"),
    (40300, "没有该操作的权限", "权限点不匹配或数据范围外"),
    (40400, "资源不存在", "目标记录已被删除或不存在"),
    (40900, "资源冲突", "重复审批、重复创建等状态冲突"),
    (42900, "请求过于频繁", "预留的限流错误码"),
    (50000, "服务器内部错误", "未捕获异常，响应带 traceId"),
    (60001, "设备未注册", "MQTT/HTTP 上报的 deviceSn 不存在"),
    (60002, "设备已被禁用", "设备状态为 DISABLED"),
    (60003, "设备报文签名校验失败", "HMAC 校验不通过"),
    (60004, "重复报文（重放保护命中）", "nonce 已被使用"),
    (60005, "设备报文格式不合法", "JSON 缺字段/字段类型错"),
    (60006, "设备时间戳超出允许范围", "设备时钟漂移超过 ±300 秒"),
    (61001, "员工工号不存在或已离职", "考勤上报时工号无效"),
    (61002, "未配置考勤规则", "首次打卡前必须配置规则"),
    (61003, "该员工在该时刻已有打卡记录", "业务层判重命中"),
]

# 需要详细展开的核心接口（方法, 路径关键字）
DETAIL_KEYS = [
    ("POST", "/api/v1/auth/login"),
    ("GET", "/api/v1/attendance/records"),
    ("POST", "/iot/v1/punch"),
    ("POST", "/api/v1/makeup"),
    ("POST", "/api/v1/makeup/{requestId}/review"),
    ("GET", "/api/v1/audit"),
    ("GET", "/api/v1/dashboard/admin"),
    ("POST", "/api/v1/devices/{deviceId}/secret"),
]


def resolve(schema, spec, depth=0):
    """解析 $ref，返回 (标题, 字段列表[(名, 类型, 说明)])"""
    if not schema or depth > 3:
        return None, []
    if "$ref" in schema:
        name = schema["$ref"].split("/")[-1]
        target = spec.get("components", {}).get("schemas", {}).get(name, {})
        props = target.get("properties", {})
        rows = []
        for k, v in props.items():
            t = v.get("type")
            if not t and "$ref" in v:
                t = v["$ref"].split("/")[-1]
            elif t == "array":
                inner = v.get("items", {})
                t = "array<" + (inner.get("$ref", "").split("/")[-1] or inner.get("type", "?")) + ">"
            rows.append((k, t or "object", v.get("description", "") or ""))
        return name, rows
    return schema.get("type", "object"), []


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--openapi", required=True)
    ap.add_argument("--endpoints", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    spec = json.load(open(args.openapi, encoding="utf-8"))
    eps = json.load(open(args.endpoints, encoding="utf-8"))
    perm_by_key = {(e["method"], e["path"]): e["preAuthorize"] for e in eps}

    paths = spec["paths"]
    ops = []
    for path, item in paths.items():
        for m, op in item.items():
            if m not in METHODS:
                continue
            seg = [s for s in path.split("/") if s]
            module = "iot-device" if seg and seg[0] == "iot" else (seg[1] if len(seg) > 1 else "other")
            ops.append({
                "method": m.upper(),
                "path": path,
                "summary": op.get("summary") or (op.get("operationId") or ""),
                "module": module,
                "tags": op.get("tags") or [],
                "params": op.get("parameters") or [],
                "body": (op.get("requestBody", {}).get("content", {}) or {}),
                "responses": op.get("responses", {}),
                "perm": perm_by_key.get((m.upper(), path), ""),
                "security": bool(op.get("security")),
            })

    by_module = OrderedDict()
    for module in list(MODULE_CN.keys()) + ["iot-device", "other"]:
        group = [o for o in ops if o["module"] == module]
        if group:
            by_module[module] = sorted(group, key=lambda x: (x["path"], x["method"]))

    doc = C.new_document(
        "TeamPulse 接口文档",
        "团队协作与物联网考勤平台 · HTTP / IoT 接口说明",
        [f"接口规模：{len(paths)} 个路径 / {len(ops)} 个操作 / "
         f"{len(spec.get('components', {}).get('schemas', {}))} 个数据模型",
         "鉴权：业务接口用 JWT Bearer；设备接口用 HMAC-SHA256 验签",
         "规范来源：/v3/api-docs（springdoc 导出）　权限列解析自控制器源码",
         "文档版本 v1.0"],
    )

    # ---------------------------------------------------------------- 1
    C.h1(doc, "1　通用说明")
    C.h2(doc, "1.1　服务地址与版本")
    C.table(doc, ["项", "值"],
            [["线上地址", "http://81.70.102.116/ （经 Nginx 同源访问，前端与接口同域）"],
             ["本地开发", "http://127.0.0.1:8080 （直连后端）或 http://127.0.0.1:5173 （Vite 代理）"],
             ["路径前缀", "业务接口 /api/v1/**；设备接口 /iot/v1/**"],
             ["内容类型", "application/json; charset=utf-8"],
             ["时间格式", "yyyy-MM-dd HH:mm:ss（东八区）"]],
            widths=[1.0, 5.2])

    C.h2(doc, "1.2　两种认证方式")
    C.table(doc, ["方式", "适用", "做法"],
            [["JWT Bearer", "所有 /api/v1/** 业务接口",
              "登录后把返回的 token 放进请求头 Authorization: Bearer <token>"],
             ["HMAC 验签", "/iot/v1/** 设备接口",
              "签名串 = deviceSn|employeeNo|punchTime|nonce，用设备密钥做 HMAC-SHA256，放 sign 字段"]],
            widths=[1.0, 1.5, 3.7])
    C.note(doc, "OpenAPI 里 /iot/** 刻意不声明 Bearer 安全方案：按 JWT 调用它会 401，"
                "它只认 HMAC 签名。")

    C.h2(doc, "1.3　统一响应结构")
    C.code_block(doc, """{
  "code": 0,                 // 0=成功，其余为业务错误码
  "message": "ok",           // 面向用户的提示，可直接展示
  "data": { },               // 业务数据，分页时为 { records, total, page, size }
  "timestamp": "2026-10-27 07:40:00"
}""", size=8.5)

    # ---------------------------------------------------------------- 2 错误码
    C.h1(doc, "2　错误码")
    C.para(doc, "错误码分段：0 成功；4xxxx 客户端/权限问题；5xxxx 服务端问题；6xxxx 设备与考勤业务。"
                "前端据 code 决定提示方式（40100 跳登录、40300 显示无权限、5xxxx 展示 traceId）。")
    C.table(doc, ["code", "message", "触发场景"],
            [[c, m, d] for c, m, d in ERROR_CODES],
            widths=[0.7, 2.0, 3.5])

    # ---------------------------------------------------------------- 3 总览
    C.h1(doc, "3　接口清单总览")
    rows = []
    for module, group in by_module.items():
        name = MODULE_CN.get(module, "设备接入" if module == "iot-device" else module)
        methods = Counter(o["method"] for o in group)
        prefix = "/iot/v1" if module == "iot-device" else f"/api/v1/{module}"
        rows.append([name, prefix, len(group),
                     " ".join(f"{k}×{v}" for k, v in sorted(methods.items())),
                     sum(1 for o in group if o["perm"])])
    C.table(doc, ["模块", "路径前缀", "操作数", "方法分布", "带权限注解"],
            rows + [["合计", "—", len(ops), "—", sum(1 for o in ops if o["perm"])]],
            widths=[1.0, 1.9, 0.7, 1.9, 1.1])

    # ---------------------------------------------------------------- 4 明细
    C.h1(doc, "4　接口明细")
    C.para(doc, "按模块列出。权限列直接来自控制器上的 @PreAuthorize；"
                "为空表示「登录即可」或「在 Service 层按本人/团队收窄」，两种情况都在备注里说明。")
    for module, group in by_module.items():
        name = MODULE_CN.get(module, "设备接入" if module == "iot-device" else module)
        C.h2(doc, f"4.{list(by_module).index(module) + 1}　{name}（{len(group)} 个）")
        rows = []
        for o in group:
            perm = o["perm"].replace("hasAuthority('", "").replace("')", "") or (
                "HMAC 验签" if module == "iot-device" else "登录即可")
            rows.append([o["method"], o["path"], o["summary"], perm])
        C.table(doc, ["方法", "路径", "说明", "权限"], rows,
                widths=[0.65, 2.35, 2.35, 1.25], font_size=8)

    # ---------------------------------------------------------------- 5 核心接口详解
    C.h1(doc, "5　核心接口详解")
    C.para(doc, "挑出 8 个最关键的接口，给出参数、请求体与响应字段（模型定义见第 6 章）。")
    for method, key in DETAIL_KEYS:
        hit = next((o for o in ops if o["method"] == method and o["path"] == key), None)
        if not hit:
            continue
        C.h2(doc, f"5.{DETAIL_KEYS.index((method, key)) + 1}　{method} {key}")
        C.para(doc, f"说明：{hit['summary']}", size=10)
        if hit["perm"]:
            C.para(doc, f"权限：{hit['perm']}", size=9.5)
        else:
            C.para(doc, "权限：" + ("HMAC 验签（无 JWT）" if hit["module"] == "iot-device" else "登录即可"), size=9.5)

        if hit["params"]:
            rows = []
            for p in hit["params"]:
                schema = p.get("schema", {})
                ref_name, ref_fields = resolve(schema, spec)
                t = schema.get("$ref", "").split("/")[-1] or schema.get("type", "object")
                rows.append([p.get("name", ""), p.get("in", ""), t, "是" if p.get("required") else "否",
                             p.get("description", "") or ""])
                # 查询/请求参数常被包装成 DTO（如 RecordQuery），展开成 query.xxx 以便直接照着调
                if ref_fields:
                    for fname, ftype, fdesc in ref_fields:
                        rows.append([f"{p.get('name', '')}.{fname}", p.get("in", ""), ftype, "否", fdesc or ""])
            C.table(doc, ["参数", "位置", "类型", "必填", "说明"], rows,
                    widths=[1.3, 0.55, 1.25, 0.45, 2.65], font_size=8)

        for ctype, media in hit["body"].items():
            name, fields = resolve(media.get("schema", {}), spec)
            if name:
                C.para(doc, f"请求体 {name}：", size=9.5, bold=True)
                if fields:
                    C.table(doc, ["字段", "类型", "说明"], [[f[0], f[1], f[2]] for f in fields],
                            widths=[1.5, 1.5, 3.2], font_size=8)

        for status, resp in hit["responses"].items():
            content = (resp.get("content") or {})
            if not content:
                continue
            schema = list(content.values())[0].get("schema", {})
            name, fields = resolve(schema, spec)
            if name and name not in ("object",):
                C.para(doc, f"响应 {status} {name}：", size=9.5, bold=True)
                if fields:
                    C.table(doc, ["字段", "类型", "说明"], [[f[0], f[1], f[2]] for f in fields],
                            widths=[1.5, 1.5, 3.2], font_size=8)

    # ---------------------------------------------------------------- 6 数据模型
    C.h1(doc, "6　主要数据模型")
    C.para(doc, "以下模型来自 OpenAPI 的 components.schemas，字段名为 JSON 中的键。")
    key_models = ["ApiResponse", "PageResult", "LoginResponse", "AttendanceRecordVO",
                  "MakeupVO", "IotDeviceVO", "AuditLogVO", "ScheduleVO", "UserVO"]
    schemas = spec.get("components", {}).get("schemas", {})
    for name in key_models:
        if name not in schemas:
            continue
        C.h2(doc, f"6.{key_models.index(name) + 1}　{name}")
        props = schemas[name].get("properties", {})
        rows = []
        for k, v in props.items():
            t = v.get("type") or ""
            if not t and "$ref" in v:
                t = v["$ref"].split("/")[-1]
            elif t == "array":
                inner = v.get("items", {})
                t = "array<" + (inner.get("$ref", "").split("/")[-1] or inner.get("type", "?")) + ">"
            rows.append([k, t or "object", v.get("description", "") or ""])
        C.table(doc, ["字段", "类型", "说明"], rows, widths=[1.7, 1.5, 3.0], font_size=8)

    # ---------------------------------------------------------------- 7 设备接口
    C.h1(doc, "7　设备接入接口（/iot/v1）")
    C.para(doc, "ESP32 既可通过 MQTT 上报（attendance/checkin），也可在断网时走 HTTP 兜底；"
                "两者共用同一套验签与幂等逻辑。")
    C.h2(doc, "7.1　签名规则")
    C.code_block(doc, """canonical = deviceSn + "|" + employeeNo + "|" + punchTime + "|" + nonce
sign      = HEX( HMAC-SHA256(key=device.secret, msg=canonical) )   # 小写十六进制

约束：
  punchTime  "yyyy-MM-dd HH:mm:ss"，与服务端时差不得超过 ±300 秒
  nonce      每条报文唯一；服务端 SET NX EX 600 记录，重复即判定重放
  secret     64 位十六进制，由设备注册/轮换接口下发，仅注册与轮换时可见""", size=8)
    C.h2(doc, "7.2　调用示例")
    C.code_block(doc, """curl -X POST http://81.70.102.116/iot/v1/punch \\
  -H "Content-Type: application/json" \\
  -d '{
    "deviceSn": "TP-ESP32-DEMO",
    "employeeNo": "E1000",
    "punchTime": "2026-10-27 09:01:12",
    "nonce": "3f6a1c9e",
    "sign": "<按上面规则算出的 64 位小写十六进制>"
  }'""", size=8)
    C.h2(doc, "7.3　设备侧错误码对照")
    C.table(doc, ["code", "含义", "设备侧建议动作"],
            [[60001, "设备未注册", "检查 deviceSn 是否与后台一致"],
             [60002, "设备已禁用", "联系管理员启用设备"],
             [60003, "签名校验失败", "检查 secret 与签名串顺序、是否小写十六进制"],
             [60004, "重复报文", "正常重发导致，可忽略"],
             [60005, "报文格式不合法", "检查 JSON 字段名与类型"],
             [60006, "时间戳超范围", "联网对时（NTP）或校准 RTC"]],
            widths=[0.7, 1.9, 3.6])

    # ---------------------------------------------------------------- 8 附录
    C.h1(doc, "8　附录：常用调用示例")
    C.h2(doc, "8.1　登录并取 token")
    C.code_block(doc, """curl -X POST http://81.70.102.116/api/v1/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{"username":"admin","password":"<密码>"}'""", size=8)
    C.h2(doc, "8.2　查询打卡记录（分页 + 日期范围）")
    C.code_block(doc, """curl -G http://81.70.102.116/api/v1/attendance/records \\
  -H "Authorization: Bearer <token>" \\
  --data-urlencode "page=1" --data-urlencode "size=20" \\
  --data-urlencode "from=2026-10-01" --data-urlencode "to=2026-10-27\"""", size=8)
    C.h2(doc, "8.3　补卡审批")
    C.code_block(doc, """# 员工提交
curl -X POST http://81.70.102.116/api/v1/makeup \\
  -H "Authorization: Bearer <员工token>" -H "Content-Type: application/json" \\
  -d '{"punchDate":"2026-10-26","punchType":"CHECK_IN","reason":"忘记打卡"}'

# 经理/HR 审批
curl -X POST http://81.70.102.116/api/v1/makeup/1/review \\
  -H "Authorization: Bearer <经理token>" -H "Content-Type: application/json" \\
  -d '{"approved":true,"comment":"已核实"}'""", size=8)
    C.note(doc, "参数名以后端 DTO 为准；本文档的请求示例按当前实现书写，"
                "字段如有出入请以第 5、6 章的模型定义为准。")

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    doc.save(args.out)
    print(f"已生成 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
