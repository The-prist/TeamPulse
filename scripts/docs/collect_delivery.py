# -*- coding: utf-8 -*-
"""把桌面「项目过程文档」里指定文档与 9 张图归集到一个新文件夹，并生成清单。

结构：
  TeamPulse_交付文档/
  ├── 文档/   9 份 docx（8 类 + 附图册）
  ├── 图/     9 张 PNG（按标准命名）
  └── 文件清单.md
"""
import os
import shutil
from datetime import datetime

DESK = os.path.join(os.environ["USERPROFILE"], "Desktop")
SRC = os.path.join(DESK, "项目过程文档")
DST = os.path.join(DESK, "TeamPulse_交付文档")
DOC_DIR = os.path.join(DST, "文档")
FIG_DIR = os.path.join(DST, "图")
REPO_FIG = r"E:\codestorge\docs\diagrams\v2"

# 顺序即文档生命周期
DOCS = [
    ("01_可行性研究报告.docx", "TeamPulse_可行性研究报告.docx", "技术/经济/操作/进度/风险论证"),
    ("02_项目开发计划书.docx", "TeamPulse_项目开发计划书.docx", "WBS、里程碑、质量与风险计划"),
    ("03_需求规格说明书.docx", "TeamPulse_需求规格说明书.docx", "6 角色、22 权限点、90 接口、29 项标"),
    ("04_总体设计说明书.docx", "TeamPulse_总体设计说明书.docx", "分层架构、部署拓扑、模块划分"),
    ("05_详细设计说明书.docx", "TeamPulse_详细设计说明书.docx", "关键类、实现顺序、算法、10 条 ADR"),
    ("06_测试计划与报告.docx", "TeamPulse_测试计划与报告.docx", "五层策略、用例明细、权限审计、缺陷"),
    ("07_用户手册.docx", "TeamPulse_用户手册.docx", "6 角色操作指引与常见问题"),
    ("08_部署说明.docx", "TeamPulse_部署说明.docx", "从零到上线：装 Docker → .env → 验收"),
    ("09_维护与运维手册.docx", "TeamPulse_维护与运维手册.docx", "巡检、监控、备份、发布回滚、故障 SOP"),
    ("10_系统图册.docx", "TeamPulse_系统图册.docx", "9 张图的合订本（含「怎么读」说明）"),
]

FIGS = [
    ("01_系统架构图.png", "fig1-architecture.png", "三层架构 + 6 业务域 + 可观测"),
    ("02_部署图.png", "fig2-deployment.png", "云服务器内 7 容器与端口矩阵"),
    ("03_业务流程图.png", "fig3-business-flow.png", "打卡上报主流程 + 3 条失败分支"),
    ("04_用例图.png", "fig4-use-case.png", "6 个角色 × 15 个用例"),
    ("05_时序图.png", "fig5-sequence.png", "验签 → 防重放 → 幂等落库"),
    ("06_状态图.png", "fig6-state.png", "补卡状态机 + 设备状态"),
    ("07_类图.png", "fig7-class.png", "14 个核心领域类"),
    ("08_组件图.png", "fig8-component.png", "前后端组件与依赖方向"),
    ("09_数据模型图.png", "fig9-data-model.png", "21 表核心关系（无物理外键）"),
]


def main() -> int:
    for d in (DOC_DIR, FIG_DIR):
        os.makedirs(d, exist_ok=True)

    copied, missing = [], []
    for dst_name, src_name, note in DOCS:
        src = os.path.join(SRC, src_name)
        if not os.path.exists(src):
            missing.append(src_name)
            continue
        shutil.copy2(src, os.path.join(DOC_DIR, dst_name))
        copied.append((dst_name, os.path.getsize(os.path.join(DOC_DIR, dst_name)), note))

    fig_copied, fig_missing = [], []
    for dst_name, src_name, note in FIGS:
        src = os.path.join(REPO_FIG, src_name)
        if not os.path.exists(src):
            fig_missing.append(src_name)
            continue
        shutil.copy2(src, os.path.join(FIG_DIR, dst_name))
        fig_copied.append((dst_name, os.path.getsize(os.path.join(FIG_DIR, dst_name)), note))

    # 清单
    lines = [
        "# TeamPulse 交付文档清单",
        "",
        f"> 生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M')}　来源：桌面「项目过程文档」+ 仓库 docs/diagrams/v2",
        "> 线上地址：http://81.70.102.116/",
        "",
        "## 一、文档（文档/ 目录，9 份 + 1 份合订本）",
        "",
        "| 序号 | 文件 | 大小 | 内容 |",
        "|---|---|---|---|",
    ]
    for i, (name, size, note) in enumerate(copied, start=1):
        lines.append(f"| {i} | `{name}` | {size/1024:.1f} KB | {note} |")

    lines += ["", "## 二、图（图/ 目录，9 张）", "",
              "| 序号 | 文件 | 大小 | 说明 |", "|---|---|---|---|"]
    for i, (name, size, note) in enumerate(fig_copied, start=1):
        lines.append(f"| {i} | `{name}` | {size/1024:.1f} KB | {note} |")

    lines += [
        "", "## 三、阅读顺序建议", "",
        "1. **先看结论**：01 可行性研究报告 → 02 项目开发计划书",
        "2. **再看做什么**：03 需求规格说明书（角色与权限跳读第 4 章）",
        "3. **然后怎么做的**：04 总体设计 → 05 详细设计 → 10 系统图册（图比文字快）",
        "4. **最后看质量与交付**：06 测试计划与报告 → 07 用户手册 → 08 部署说明 → 09 运维手册",
        "",
        "> 图册（10）是 9 张图的合订本，每张图下都有一段「怎么读」；",
        "> 若只需图，直接取 `图/` 目录的 PNG（2x 清晰度，可直接插进 PPT）。",
        "",
        "## 四、补充说明",
        "",
        "- 文档里的数字（21 表 / 90 接口 / 46 项断言 / 3381 条打卡）均来自真实代码与运行数据，非估算。",
        "- 岗位相关的额外材料（数据库设计说明书、接口文档、角色权限矩阵、演示数据说明）在仓库 `docs/` 与桌面「项目过程文档」中，未纳入本交付集。",
        "",
    ]
    md = os.path.join(DST, "文件清单.md")
    with open(md, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))

    print(f"新文件夹：{DST}")
    print(f"  文档 {len(copied)} 份 → {DOC_DIR}")
    print(f"  图 {len(fig_copied)} 张 → {FIG_DIR}")
    print(f"  清单 → {md}")
    if missing:
        print(f"  ⚠ 缺文档：{missing}")
    if fig_missing:
        print(f"  ⚠ 缺图：{fig_missing}")
    return 0 if not (missing or fig_missing) else 1


if __name__ == "__main__":
    raise SystemExit(main())
