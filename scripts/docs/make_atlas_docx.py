#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成《TeamPulse 系统图册》DOCX：9 张图 + 图注 + 阅读提示 + 源码附录。

图由 scripts/docs/make_diagrams.py 生成（Pillow 直绘，2x 超采样）。
用法：
    python scripts/docs/make_atlas_docx.py --images docs/diagrams/v2 --out docs/TeamPulse_系统图册.docx
"""
import argparse
import os
import sys

from docx.shared import Inches

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import docx_common as C  # noqa: E402

FIGURES = [
    ("fig1-architecture.png", "图 1　系统架构图",
     "分层看：接入层（Nginx / Mosquitto）→ 应用层（Spring Boot 单体，按业务域分包）→ 数据层（MySQL / Redis）。"
     "左侧是三类访问者，右侧虚线是可观测组件。要点：**只有一个可执行 jar**，所有模块在同一进程内。"),
    ("fig2-deployment.png", "图 2　部署图",
     "一台 4C4G 云服务器上跑 5 个容器。只有 80（Web）与 1883（设备）对公网；"
     "8080/3306/6379 只在内网；3000/9090 只绑回环，看监控要 SSH 隧道。"),
    ("fig3-business-flow.png", "图 3　业务流程图（设备打卡）",
     "主流程从左到右：设备签名 → 接入 → 验签与去重 → 判定 → 落库与汇总。"
     "下方三条虚线是失败分支，每条都对应一个明确的错误码。"),
    ("fig4-use-case.png", "图 4　用例图（6 角色）",
     "按 RBAC 的 6 个角色画。审计专员的连线全部指向只读用例；普通员工不与管理端用例相连。"
     "角色—权限的精确矩阵见《角色与权限矩阵.md》与《接口文档》。"),
    ("fig5-sequence.png", "图 5　时序图（打卡上报）",
     "注意三个「自调用」：验签、幂等、指标，都是后端内部动作。"
     "Redis 的 SET NX 是防重放，MySQL 的唯一索引是防重复，两者职责不同。"),
    ("fig6-state.png", "图 6　状态图",
     "上：补卡单只有 pending → approved/rejected 两条出路，重复审批返回 409。"
     "下：设备状态与密钥轮换，轮换后旧密钥立即失效。"),
    ("fig7-class.png", "图 7　类图",
     "20 个实体里挑出核心 14 个。字段是简化的，完整字段见《数据库设计说明书》。"
     "所有连线都是逻辑关联（无物理外键）。"),
    ("fig8-component.png", "图 8　组件图",
     "前端只有 5 个通用组件，业务页面直接写在 views 里——单人项目刻意少一层抽象。"
     "后端 controller 只做参数与权限，业务全在 service。"),
    ("fig9-data-model.png", "图 9　数据模型图",
     "21 张表按域分四列：左侧权限与组织、中间协作、右中考勤、右侧评论/审计/基础设施。"
     "唯一索引与主键已在表内标注。"),
]

APPENDIX = [
    ("图 3 业务流程（Mermaid）", """flowchart LR
  D[ESP32 读卡] --> S[HMAC 签名] --> P[MQTT 发布] --> B[Backend 验签]
  B --> N{nonce 首次?}
  N -- 否 --> R1[60004 重放]
  N -- 是 --> U[工号→用户] --> C{规则存在?}
  C -- 否 --> R2[61002]
  C -- 是 --> J[判定状态] --> I[INSERT record]
  I --> K{唯一键冲突?}
  K -- 是 --> R3[幂等返回 200]
  K -- 否 --> A[刷新日汇总]"""),
    ("图 5 时序（PlantUML）", """@startuml
actor ESP32
participant Mosquitto
participant Backend
database Redis
database MySQL
ESP32 -> Mosquitto : publish attendance/checkin
Mosquitto -> Backend : deliver
Backend -> MySQL : select device by sn
Backend -> Backend : verify HMAC + drift(±300s)
Backend -> Redis : SET NX nonce EX 600
Backend -> MySQL : insert attendance_record
Backend -> MySQL : upsert attendance_daily
Backend --> Mosquitto : Ack
@enduml"""),
    ("图 9 数据模型（Mermaid ER，节选）", """erDiagram
    department ||--o{ user : 部门成员
    user ||--o{ attendance_record : 打卡
    iot_device ||--o{ attendance_record : 上报
    user ||--o{ attendance_daily : 汇总
    user ||--o{ makeup_request : 补卡
    role ||--o{ role_permission : 拥有
    permission ||--o{ role_permission : 归属"""),
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    doc = C.new_document(
        "TeamPulse 系统图册",
        "视觉化设计：架构 / 部署 / 流程 / 用例 / 时序 / 状态 / 类 / 组件 / 数据模型",
        ["共 9 张图，覆盖需求到部署的关键视角",
         "图由脚本直接绘制（Pillow，2x 超采样），附录保留可重绘的 Mermaid/PlantUML 源码",
         "配套文字说明见：总体设计说明书、详细设计说明书、数据库设计说明书",
         "文档版本 v1.0"],
    )

    C.h1(doc, "1　图册说明")
    C.para(doc, "本图册把散落在各设计文档里的图集中到一处，便于评审与答辩时快速浏览。"
                "每张图下面都有一段「怎么读」，说明该图想强调的重点。")
    C.table(doc, ["编号", "图名", "对应设计文档", "回答什么问题"],
            [["图 1", "系统架构图", "总体设计 §2", "系统由哪些层与组件构成"],
             ["图 2", "部署图", "总体设计 §2.2 / 部署手册", "跑在哪、端口怎么开"],
             ["图 3", "业务流程图", "详细设计 §3", "一次打卡从设备到落库经历什么"],
             ["图 4", "用例图", "需求规格 §4–5", "6 个角色各自能做什么"],
             ["图 5", "时序图", "详细设计 §3.2", "验签、防重放、幂等的先后顺序"],
             ["图 6", "状态图", "详细设计 §4", "补卡单与设备的状态流转"],
             ["图 7", "类图", "详细设计 §2", "核心领域对象与关系"],
             ["图 8", "组件图", "总体设计 §3", "前后端模块怎么拆、依赖方向"],
             ["图 9", "数据模型图", "数据库设计 §4", "21 张表怎么组织"]],
            widths=[0.6, 1.2, 1.7, 2.6])

    C.h1(doc, "2　图")
    for fname, title, note in FIGURES:
        path = os.path.join(args.images, fname)
        if not os.path.exists(path):
            C.note(doc, f"⚠ 缺少图片：{fname}（请先运行 make_diagrams.py）")
            continue
        C.h2(doc, title)
        doc.add_picture(path, width=Inches(6.2))
        doc.paragraphs[-1].alignment = 1  # 居中
        C.para(doc, note.replace("**", ""), size=9.5)

    C.h1(doc, "3　附录：可重绘的源码")
    C.para(doc, "下面三段源码可以在 VS Code（Mermaid 插件）或 plantuml.com 直接渲染，"
                "便于以后修改样式而不必动 Python 绘图代码。")
    for title, source in APPENDIX:
        C.h2(doc, title)
        C.code_block(doc, source, size=8)

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    doc.save(args.out)
    print(f"已生成 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
