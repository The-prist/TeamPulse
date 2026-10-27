# -*- coding: utf-8 -*-
"""扫描所有 docx，找出与当前事实不符的表述。

事实基线（2026-10-27 实测）：
  21 表 / V1~V8 迁移 / 90 接口 / 16 控制器 / 22 权限点 / 6 角色
  28 账号（在用 27）/ 5 部门 / 6 团队 / 62 成员
  3381 打卡 / 449 日程 / 217 日志 / 235 评论 / 6 看板 / 16 补卡 / 153 审计
  11 单测 + 5 集成（本机跳过）+ 46 项接口断言（线上 42）+ 6×16 权限审计
  Prometheus + Grafana 已跑通；audit_log 已实现；CI 配置存在但按决策不运行
  部署：腾讯云 4L/4C4G Rocky Linux 10，5 容器 healthy，V1~V8
  代码：125 Java / 11,307 行；前端 37 页面 / 17,014 行
"""
import glob
import os
import re

from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph

DIRS = [
    os.path.join(os.environ["USERPROFILE"], "Desktop", "TeamPulse_交付文档", "文档"),
    os.path.join(os.environ["USERPROFILE"], "Desktop", "项目过程文档"),
]

# (正则, 期望的正确说法, 说明)
PATTERNS = [
    (r"9 个账号|9 个演示账号|8 个账号", "28 个账号（在用 27）", "账号数过时"),
    (r"14 张表|15 张表|18 张表|19 张表|20 张表", "21 张表", "表数过时"),
    (r"V1~V6|V1-V6|V1~V7(?!~V8)", "V1~V8", "迁移版本过时"),
    (r"87 个接口|80 个接口|70 个接口", "90 个接口（OpenAPI 70 路径 / 92 操作）", "接口数过时"),
    (r"4 张表.*RBAC|RBAC 新增 5 张表", "按 V4/V5 新增 RBAC 与部门表", "RBAC 表数说法过时"),
    (r"3 个角色|只有 admin|admin/member 两个", "6 个角色、22 个权限点", "角色模型过时"),
    (r"仅 1 个单元测试|1 个单元测试类|无单元测试", "11 个单测 + 5 个集成测试", "测试规模过时"),
    (r"无 Prometheus|未接入 Prometheus|无 Micrometer|无监控", "已接入 Prometheus + Grafana", "监控状态过时"),
    (r"无 CI|无 CI/CD|当前无 \.github|本期不做.*CI", "ci.yml 已存在（按决策不在 GitHub 运行）", "CI 状态过时"),
    (r"审计日志.*本期不做|操作审计日志.*本期不做|审计.*未实现", "audit_log 已实现（V6 + AOP + 查询页）", "审计状态过时"),
    (r"压测.*本期不做|尚无 QPS|无压测", "已完成压测（20 并发 P95 40.5ms）", "压测状态过时"),
    (r"云服务器未验证|compose 未真实运行|未在真实服务器", "已部署腾讯云并公网验收", "部署状态过时"),
    (r"未实现.*集成测试|集成测试.*未实现", "已实现 Testcontainers 集成测试（本机跳过）", "集成测试状态过时"),
    (r"100\+ 打卡|100 条打卡", "3381 条打卡", "数据量过时"),
    (r"15 张表|16 张表", "21 张表", "表数过时"),
    (r"用例图.*v1|ER 图.*v1|仅 3 个角色", "已更新为 6 角色 / 21 表（系统图册）", "图版本过时"),
]


def iter_blocks(doc):
    for child in doc.element.body.iterchildren():
        if child.tag.endswith('}p'):
            yield Paragraph(child, doc)
        elif child.tag.endswith('}tbl'):
            yield Table(child, doc)


def text_of(b):
    if isinstance(b, Paragraph):
        return b.text
    return " ".join(c.text for r in b.rows for c in r.cells)


def main():
    files = []
    for d in DIRS:
        files += sorted(glob.glob(os.path.join(d, "*.docx")))
    # 旧版参考文档不参与内容更新
    files = [f for f in files if not os.path.basename(f).startswith(("~$", "_参考_"))]

    print(f"扫描 {len(files)} 份文档（已排除 2 份「_参考_旧版」）\n")
    total = 0
    for path in files:
        doc = Document(path)
        hits = []
        for i, b in enumerate(iter_blocks(doc)):
            t = text_of(b)
            if not t.strip():
                continue
            for pat, expect, why in PATTERNS:
                m = re.search(pat, t)
                if m:
                    snippet = t.strip().replace("\n", " ")
                    pos = max(0, m.start() - 20)
                    hits.append((i, kind(b), m.group(0), snippet[pos:pos + 90], expect, why))
        if hits:
            print(f"■ {os.path.basename(path)}（{len(hits)} 处）")
            for i, k, got, snip, expect, why in hits[:12]:
                print(f"   [{i:>3}] {k} 「{got}」  ← {why}")
                print(f"        上下文: {snip}")
                print(f"        改为: {expect}")
            if len(hits) > 12:
                print(f"   …… 另有 {len(hits) - 12} 处")
            print()
            total += len(hits)
    print(f"共发现 {total} 处可疑表述")


def kind(b):
    if isinstance(b, Table):
        return "表格"
    return "标题" if b.style.name.startswith("Heading") else "正文"


if __name__ == "__main__":
    main()
