"""项目介绍报告生成入口。

用法：
    python docs/gen/make_report.py
产物：
    docs/TeamPulse_项目介绍报告.docx
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))

from word_builder import WordDoc  # noqa: E402

import report_a  # noqa: E402
import report_b  # noqa: E402
import report_c  # noqa: E402

CHAPTERS = [
    ("0  一页读懂这个项目", "关键数字、一句话回答常见疑问"),
    ("1  它为什么存在", "小公司的三个痛点、TeamPulse 的做法"),
    ("2  谁在用它，怎么用", "三类使用者、员工与管理员的一天、打卡机的工作方式"),
    ("3  功能全景", "协作域、考勤域、两个域怎么联动"),
    ("4  硬件部分", "打卡机的组成、一次打卡的旅程、服务器的 6 项检查、模拟器"),
    ("5  软件部分", "5 个容器的分工、技术选型白话解释、代码组织"),
    ("6  数据是怎么存的", "14 张表的关系、三张核心表、两个设计取舍"),
    ("7  安全是怎么保证的", "人的身份、设备的身份、其他防护"),
    ("8  怎么把它跑起来", "生产部署、本地开发、内存预算、日常运维"),
    ("9  项目现状与边界", "已验证的内容、明确没做的清单"),
    ("10 新同事最常问的 12 个问题", "硬件、补卡、断网、安全、容量"),
    ("11 术语表", "19 个术语的白话解释"),
    ("12 附录：速查", "代码目录、页面入口、排查动作、阅读顺序"),
]


def main():
    out_dir = pathlib.Path(__file__).parent.parent
    out = out_dir / "TeamPulse_项目介绍报告.docx"

    doc = WordDoc(
        title="TeamPulse 项目介绍报告",
        subtitle="给第一次接触这个系统的人 · 从业务问题到技术实现",
    )

    # ---- 目录 ----
    doc.h1("目录")
    doc.table(
        ["章节", "你会读到什么"],
        [[t, s] for t, s in CHAPTERS],
        widths=[7.6, 8.4],
    )
    doc.muted(
        "说明：本报告面向零背景读者，前 4 章不需要技术知识。"
        "若想深入了解实现细节，请参阅同目录下的《TeamPulse 技术手册》。"
    )
    doc.page_break()

    for module in (report_a, report_b, report_c):
        module.build(doc)

    doc.page_break()
    doc.h1("结语")
    doc.p(
        "如果读完这份报告，你能用自己的话回答下面四个问题，说明你已经理解了这套系统："
    )
    doc.numbered(
        [
            "**它解决什么问题？** —— 让协作与考勤共用一份人员数据，打卡自动进系统、自动判定、自动汇总。",
            "**一次打卡是怎么走完的？** —— 指纹 → 串口 → 设备拼 JSON 并签名 → 消息中转站 → 服务器 6 项校验 → 判定 → 入库 → 回执 → 网页看板。",
            "**为什么这么切分？** —— 硬件、消息中转、后端、数据库各自独立，任何一环换掉都不影响其他环。",
            "**它现在能做什么、不能做什么？** —— 能支撑几百人规模的公司日常协作与考勤；不支持多公司隔离、请假加班、移动端。",
        ]
    )
    doc.p(
        "这套系统的价值不在于用了多少技术名词，而在于它把「硬件上报的数据」变成了「可以直接用于管理和结算的可信记录」——"
        "签名、时间窗、去重、留痕、汇总，每一层都是为了这个目标。"
    )

    doc.save(str(out))
    print(f"generated: {out}")


if __name__ == "__main__":
    main()
