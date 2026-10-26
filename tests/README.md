# 自动化测试

三层检查，一条命令跑完：

```powershell
.\scripts\run-tests.ps1
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
