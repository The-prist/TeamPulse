"""Word 手册生成入口。

用法：
    python docs/gen/make.py
产物：
    docs/TeamPulse_技术手册.docx
"""
import pathlib
import sys

# 允许直接以脚本方式运行（把当前目录加入 import 路径）
sys.path.insert(0, str(pathlib.Path(__file__).parent))

from word_builder import WordDoc  # noqa: E402

import content_00  # noqa: E402
import content_01  # noqa: E402
import content_02  # noqa: E402
import content_03  # noqa: E402
import content_04  # noqa: E402
import content_05  # noqa: E402
import content_06  # noqa: E402
import content_07  # noqa: E402
import content_08  # noqa: E402

CHAPTERS = [
    ("第 0 章  先跑起来，再读下去", "五分钟启动、第一次登录的操作顺序、术语表"),
    ("第 1 章  先建立全局认知", "项目定位、技术选型理由、架构图、请求链路、目录结构"),
    ("第 2 章  物联网考勤子系统", "端到端链路、报文契约、HMAC 签名、防重放、幂等、判定口径、双层存储、排障地图"),
    ("第 3 章  认证与安全", "旧方案的漏洞、JWT、Spring Security 配置、BCrypt 平滑迁移、三层权限、攻击面"),
    ("第 4 章  数据模型与后端工程实践", "15 张表设计、索引、逻辑删除、分层、统一响应、异常码、Flyway、定时任务、测试"),
    ("第 5 章  前端工程实践", "axios 拦截器、Pinia、双面板路由守卫、轮询、构建分包"),
    ("第 6 章  部署与运维", "服务器准备、内存预算、部署步骤、HTTPS、备份恢复、发布回滚、故障速查"),
    ("第 7 章  新旧对照与面试问答", "改造对照表、37 个面试问答、演示脚本、简历写法"),
    ("第 8 章  附录", "接口清单、错误码、环境变量、命令速查、学习路径"),
]


def main():
    out_dir = pathlib.Path(__file__).parent.parent
    out = out_dir / "TeamPulse_技术手册.docx"

    doc = WordDoc(
        title="TeamPulse 技术手册",
        subtitle="团队协作与物联网考勤平台 · 从代码到部署的完整梳理",
    )

    # ---------- 静态目录（不依赖 Word 域更新，任何环境都能正常显示） ----------
    doc.h1("目录")
    doc.table(
        ["章节", "内容"],
        [[title, desc] for title, desc in CHAPTERS],
        widths=[7.4, 8.6],
    )
    doc.muted(
        "说明：本文档为静态目录（非 Word 自动目录域），在任何阅读器里都能正常显示。"
        "若需要页码，可在 Word 中插入「引用 → 目录」并选择标题样式。"
    )
    doc.page_break()

    for module in (
        content_00,
        content_01,
        content_02,
        content_03,
        content_04,
        content_05,
        content_06,
        content_07,
        content_08,
    ):
        module.build(doc)

    # ---------- 收束 ----------
    doc.page_break()
    doc.h1("结语")
    doc.p(
        "这份手册的目的不是把代码抄一遍，而是让每个技术点都能回答三个问题："
        "**为什么这么做、不这么做会怎样、边界在哪里**。"
    )
    doc.p(
        "如果通读之后，你能在不看代码的情况下讲清楚「一次打卡从手指按下去到出现在看板上」的完整链路，"
        "并且说得出其中每一道校验是为了防什么——那这个项目就真正属于你了。"
    )
    doc.p(
        "最后提醒一句：文档里的所有取舍都是**在特定约束下**成立的（单机 4C4G、企业内部、"
        "低频打卡、单一考勤规则）。脱离约束谈「最佳实践」是没有意义的——"
        "面试时把约束讲清楚，比堆技术名词更能体现工程能力。",
        color=None,
    )

    doc.save(str(out))
    print(f"generated: {out}")


if __name__ == "__main__":
    main()
