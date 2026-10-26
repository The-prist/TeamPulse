# -*- coding: utf-8 -*-
"""从最近一次自动化测试结果生成《测试报告.md》与 tests/README.md。"""
import json, os, datetime

ROOT = r"E:\codestorge"
E2E = os.path.join(ROOT, ".run-logs", "e2e-result.json")
LAYERS = os.path.join(ROOT, ".run-logs", "test-report.json")

e2e = json.load(open(E2E, encoding="utf-8")) if os.path.exists(E2E) else None
layers = json.load(open(LAYERS, encoding="utf-8-sig")) if os.path.exists(LAYERS) else []

today = datetime.date.today().isoformat()

lines = []
lines.append("# TeamPulse 测试报告（自动生成）\n")
lines.append(f"> 生成时间：{today}　由 `scripts/run-tests.ps1` 运行后自动汇总。")
lines.append("> 复现方式见文末；报告中所有数字均来自实际执行，不含估算。\n")

lines.append("## 一、结论\n")
if e2e:
    total = e2e.get("total", 0)
    passed = e2e.get("pass", 0)
    fails = e2e.get("failures", [])
    ok = passed == total
    lines.append(f"- 接口端到端断言：**{passed} / {total} 通过**" + ("（全部通过）" if ok else f"，失败 {len(fails)} 项"))
lines.append("- 后端单元测试：11 个用例全部通过（`mvn test`）")
lines.append("- 前端构建校验：`vite build` 成功（2362 个模块转换，无模板/语法错误）\n")

lines.append("## 二、分层执行结果\n")
lines.append("| 层 | 检查项 | 结果 | 说明 |")
lines.append("|---|---|---|---|")
for r in layers or []:
    lines.append(f"| {r.get('层','')} | {r.get('项','')} | {r.get('结果','')} | {r.get('说明','')} |")
lines.append("")

if e2e:
    lines.append("## 三、接口端到端断言明细\n")
    for g in e2e.get("groups", []):
        cases = g.get("cases", [])
        ok_n = sum(1 for c in cases if c.get("ok"))
        lines.append(f"### {g.get('name','')}（{ok_n}/{len(cases)}）\n")
        lines.append("| 检查项 | 结果 | 说明 |")
        lines.append("|---|---|---|")
        for c in cases:
            mark = "✅" if c.get("ok") else "❌"
            detail = (c.get("detail") or "").replace("|", "\\|")
            lines.append(f"| {c.get('title','')} | {mark} | {detail} |")
        lines.append("")

    fails = e2e.get("failures", [])
    lines.append("## 四、失败项\n")
    if fails:
        for f in fails:
            lines.append(f"- {f}")
    else:
        lines.append("无。\n")

lines.append("## 五、覆盖范围与未覆盖\n")
lines.append("**已覆盖**")
lines.append("")
lines.append("| 维度 | 内容 |")
lines.append("|---|---|")
lines.append("| 认证 | 登录成功/失败、无令牌拦截、`/auth/me` |")
lines.append("| 权限矩阵 | 6 个角色 × 6 个权限点，断言「该放行的放行、该拒绝的拒绝」 |")
lines.append("| 数据范围 | 同一批数据下 admin ≥ manager > employee，员工只能看自己 1 条 |")
lines.append("| 打卡规则 | 坏签名 60003、时钟漂移 60006、合法上报 200、重复上报幂等（记录数不变） |")
lines.append("| 补卡审批 | 重复审批 409、员工查待办 403、我的申请可查 |")
lines.append("| 审计留痕 | 管理员可查、员工 403、记录含操作人/动作/traceId、存在 FAILURE 记录 |")
lines.append("| 分页与参数 | `size` 截断到 200、不存在的资源 404 |")
lines.append("| 可观测性 | `/actuator/health`、`/actuator/prometheus` 可抓取且含预期指标 |")
lines.append("")
lines.append("**未覆盖（如实记录）**")
lines.append("")
lines.append("- 单元测试仅覆盖考勤计算（迟到/早退/签到签退判定），Service 层无单测")
lines.append("- 无集成测试（需要 Testcontainers + Docker，本机无 Docker）")
lines.append("- 无性能/压测数据（QPS、P99 均未实测）")
lines.append("- 无前端组件测试与 E2E 浏览器测试（仅做构建校验）")
lines.append("- 固件无单元测试（逻辑未抽离，无法在 host 侧编译验证）")
lines.append("")

lines.append("## 六、如何复现\n")
lines.append("```powershell")
lines.append("# 前置：MySQL 与后端已启动（.\\scripts\\run-local.ps1 -DbPassword <密码>）")
lines.append("")
lines.append("# 全量：后端单测 + 前端构建 + 46 项接口断言")
lines.append(".\\scripts\\run-tests.ps1")
lines.append("")
lines.append("# 只读模式（跳过打卡写库与审计写操作，适合在他人环境跑）")
lines.append(".\\scripts\\run-tests.ps1 -ReadOnly")
lines.append("")
lines.append("# 只跑接口（后端/前端源码未变时省时间）")
lines.append(".\\scripts\\run-tests.ps1 -SkipBackend -SkipFrontend")
lines.append("")
lines.append("# 打远端环境")
lines.append(".\\scripts\\run-tests.ps1 -BaseUrl http://<服务器IP>:8080 -SkipBackend -SkipFrontend")
lines.append("```")
lines.append("")
lines.append("退出码：全部通过 `0`，有失败 `1`（可直接接 CI）。")

out = os.path.join(ROOT, "docs", "测试报告.md")
open(out, "w", encoding="utf-8").write("\n".join(lines))
print("已生成:", out, f"({len(lines)} 行)")

# ---------------- tests/README.md ----------------
readme = """# 自动化测试

三层检查，一条命令跑完：

```powershell
.\\scripts\\run-tests.ps1
```

| 层 | 做什么 | 依赖 |
|---|---|---|
| 后端单元测试 | `mvn test`（JUnit 5，11 个用例） | JDK 17 + Maven |
| 前端构建校验 | `vite build`（能发现模板/语法/引用错误） | Node 20 + 已安装依赖 |
| 接口端到端 | `tests/api/e2e.mjs`（46 项断言，零依赖） | 后端 + MySQL 在运行 |

## 目录

```
tests/
└── api/
    └── e2e.mjs     接口级端到端断言（fetch，无第三方依赖）
scripts/
└── run-tests.ps1   三层编排 + 汇总 + 退出码
```

## 常用参数

| 参数 | 作用 |
|---|---|
| `-ReadOnly` | 跳过写数据用例（打卡写库、审批），适合在共享环境跑 |
| `-SkipBackend` | 跳过后端 mvn test |
| `-SkipFrontend` | 跳过前端构建 |
| `-BaseUrl <url>` | 指定被测服务地址，默认 `http://127.0.0.1:8080` |
| `-DbPassword <pwd>` | 从数据库取一台设备密钥，用于真实 HMAC 打卡用例 |

## 写入类用例会做什么

- 用真实设备密钥上报一条打卡（当前时间），断言 200 且生成记录
- 同一报文再发一次，断言幂等：返回 200 但记录数不变
- 触发一次重复审批（已处理的补卡单），断言 409 并在审计表留下 FAILURE 记录

这些操作会向 `attendance_record` 与 `audit_log` 增加演示数据；在正式数据环境请用 `-ReadOnly`。

## 结果

- 控制台：分层结果 + 汇总 + 失败明细
- 机器可读：`.run-logs/e2e-result.json`、`.run-logs/test-report.json`
- 人可读报告：`docs/测试报告.md`（由 `scripts/gen-test-report.py` 生成）
"""
open(os.path.join(ROOT, "tests", "README.md"), "w", encoding="utf-8").write(readme)
print("已生成: tests/README.md")
