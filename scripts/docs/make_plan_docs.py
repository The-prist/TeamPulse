#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成软件工程配套文档：可行性研究报告 / 项目开发计划书 / 用户手册 /
维护与运维手册 / 部署说明（由 deploy 手册 md 转换）。

五份文档共用 docx_common 的样式，内容全部基于本项目真实情况（云服务器 4C4G、
单人开发、6 角色 22 权限点、21 表、90 接口、腾讯云 ap-beijing）。
"""
import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import docx_common as C  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


# ============================================================ 1 可行性研究报告
def make_fs(outdir):
    doc = C.new_document(
        "TeamPulse 可行性研究报告",
        "团队协作与物联网考勤平台 · 立项与方案论证",
        ["评估维度：技术 / 经济 / 操作 / 进度 / 风险",
         "结论：可行，且已完成实施（线上运行于腾讯云 4C4G）",
         "文档版本 v1.0"],
    )

    C.h1(doc, "1　项目背景与问题定义")
    C.para(doc, "本项目起点是一个团队实训遗留的「今日事」系统：功能骨架在，但存在数据模型混乱、"
                "权限只是一个 role 字符串、无审计、无测试、无部署脚本等问题，无法支撑真实企业场景。")
    C.h2(doc, "1.1　需要解决的问题")
    C.table(doc, ["问题", "现状", "不解决的后果"],
            [["权限不可配", "user.role 只有 admin/member 两个值", "无法表达人事/经理/审计等真实岗位"],
             ["考勤靠手工", "Excel 登记，无法追溯", "代打卡、争议无法举证"],
             ["数据无留痕", "写操作不记录操作人与时间", "出问题查不到谁改的"],
             ["不可观测", "只有 print 日志", "线上故障只能靠用户反馈"],
             ["不可部署", "手工建库、手工启动", "换台机器就起不来"]],
            widths=[1.2, 2.2, 2.8])
    C.h2(doc, "1.2　拟建系统")
    C.para(doc, "TeamPulse：团队协作（日程/看板/日志/评论）+ 物联网考勤（ESP32 读卡 → MQTT/HTTP 上报 → "
                "服务端验签与判定）+ RBAC 权限（6 角色 22 权限点）+ 审计与可观测，单机容器化部署。")

    C.h1(doc, "2　技术可行性")
    C.table(doc, ["技术点", "选型", "成熟度", "本项目已验证的事实"],
            [["后端框架", "Spring Boot 3.2.4 + Java 17", "LTS，生态成熟", "编译通过、线上运行 5 容器 healthy"],
             ["持久层", "MyBatis-Plus 3.5.7 + MySQL 8.0", "主流组合", "21 表、V1~V8 迁移全部成功"],
             ["认证授权", "Spring Security 6 + jjwt + 方法级注解", "标准方案", "6 角色 × 关键端点权限矩阵实测通过"],
             ["缓存", "Redis 7（nonce 防重放）", "成熟", "SET NX 生效；不可用时降级本地缓存"],
             ["消息", "Mosquitto 2（MQTT）", "轻量、ESP32 生态常用", "后端以 teampulse-backend-1 稳定接入"],
             ["前端", "Vue 3 + Vite 7 + Element Plus 2.14", "主流", "37 页面构建通过，2362 模块"],
             ["部署", "Docker Compose + Nginx", "成熟", "腾讯云一次部署成功（构建 295s、启动 40s）"],
             ["可观测", "Micrometer + Prometheus + Grafana", "成熟", "指标抓取成功、看板自动加载"]],
            widths=[0.9, 1.9, 1.1, 2.3])
    C.para(doc, "结论：全部采用主流成熟组件，无自研底层；单机即可跑通，技术风险低。")

    C.h1(doc, "3　经济可行性")
    C.h2(doc, "3.1　成本核算（以运行 12 个月计）")
    C.table(doc, ["项目", "规格", "费用", "说明"],
            [["云服务器", "腾讯云轻量 4C4G / 40G", "约 ¥60~100 / 月", "本项目实际在用"],
             ["域名", ".com / .cn", "约 ¥50~80 / 年", "可选；IP 直连也能演示"],
             ["HTTPS 证书", "Let's Encrypt", "¥0", "certbot 自动续期"],
             ["软件栈", "Spring Boot / Vue / MySQL / Redis / Mosquitto / Prometheus", "¥0", "全部开源"],
             ["开发人力", "1 人 × 12 周", "机会成本", "本项目由开发者本人承担"],
             ["硬件（可选）", "ESP32 + RC522 读卡器", "约 ¥60~150", "无硬件时用官方模拟器验证链路"]],
            widths=[1.0, 2.2, 1.4, 1.9])
    C.h2(doc, "3.2　收益")
    for t in [
        "替代 Excel 手工登记：打卡数据自动入库、可追溯、可统计。",
        "权限与审计可配：满足「人事管人、经理管部门、审计只读」的真实分工。",
        "单人可维护：整套系统一套 compose 起停，运维成本接近 0。",
        "可复用：RBAC、审计、验签、容器化四块能力可直接迁移到其他项目。",
    ]:
        C.bullet(doc, t)
    C.para(doc, "结论：以每月百元级成本获得一套可演示、可运维的企业级系统，经济上可行。")

    C.h1(doc, "4　操作可行性")
    C.table(doc, ["使用者", "使用方式", "学习成本"],
            [["普通员工", "浏览器打开首页 → 登录 → 我的考勤/日程/日志", "低（界面即用，无需安装）"],
             ["部门经理/负责人", "管理端：部门打卡、日程、看板、补卡审批", "低（菜单按权限自动裁剪）"],
             ["人事", "考勤规则、设备、补卡审批、公司概览", "中（需理解规则与审批流）"],
             ["系统管理员", "用户、团队、权限、设备、敏感词", "中（需理解 RBAC）"],
             ["运维（开发者本人）", "SSH + docker compose + 部署脚本", "中（有手册可照做）"]],
            widths=[1.2, 3.2, 1.8])
    C.para(doc, "结论：Web 免安装、权限内界面自动收敛，操作门槛低。")

    C.h1(doc, "5　进度可行性")
    C.table(doc, ["阶段", "内容", "计划周数", "实际"],
            [["调研与设计", "旧系统梳理、数据模型、权限设计", "2 周", "已完成"],
             ["后端重建", "21 表迁移、协作域 + 考勤域接口", "4 周", "已完成"],
             ["前端重建", "37 页面、权限收敛、日历与图表", "3 周", "已完成"],
             ["物联网接入", "固件、MQTT/HTTP 双通道、验签与幂等", "1.5 周", "已完成（模拟器验证）"],
             ["质量与部署", "三层测试、压测、监控、容器化上云", "1.5 周", "已完成"]],
            widths=[1.1, 2.6, 0.9, 1.4])

    C.h1(doc, "6　风险分析")
    C.table(doc, ["风险", "可能性", "影响", "应对"],
            [["单人开发，知识集中（bus factor=1）", "高", "高", "文档齐全（本套 10 份文档 + 交接文档）、代码注释完整"],
             ["云上依赖外网（GitHub / Docker Hub）", "高", "中", "镜像源（腾讯云内网加速）、本地打包推送通道"],
             ["无真实硬件，固件未上板", "中", "中", "模拟器覆盖链路；采购硬件后补一次端到端"],
             ["MQTT 明文暴露公网", "中", "中", "密码认证 + 建议收敛来源 IP；后续升级 8883 TLS"],
             ["单机故障", "低", "高", "备份脚本 + 恢复演练；后续可加只读从库"]],
            widths=[2.0, 0.7, 0.6, 2.9])

    C.h1(doc, "7　结论")
    for t in [
        "技术：全部主流成熟组件，已验证可运行，风险低。",
        "经济：每月百元级，开源零授权费用，收益明确。",
        "操作：Web 免安装，权限内界面自动裁剪，学习成本低。",
        "进度：12 周内可完成，实际已按期交付并上线。",
    ]:
        C.bullet(doc, t)
    C.para(doc, "综上：本项目可行，建议实施——事实上已完成实施，线上地址 http://81.70.102.116/。", bold=True)
    doc.save(os.path.join(outdir, "TeamPulse_可行性研究报告.docx"))


# ============================================================ 2 项目开发计划书
def make_plan(outdir):
    doc = C.new_document(
        "TeamPulse 项目开发计划书",
        "范围 / 里程碑 / 分工 / 质量 / 配置管理 / 风险",
        ["形式：单人全栈开发（1 人承担需求、设计、编码、测试、部署）",
         "周期：约 12 周（含文档与上线）",
         "文档版本 v1.0"],
    )

    C.h1(doc, "1　项目概述")
    C.table(doc, ["项", "内容"],
            [["项目名称", "TeamPulse 团队协作与物联网考勤平台"],
             ["目标", "把实训遗留系统改造为「6 角色 RBAC + 物联网打卡 + 可观测 + 可部署」的可用系统"],
             ["交付形态", "Web 应用（前后端分离）+ 嵌入式固件 + 容器化部署包 + 文档集"],
             ["开发方式", "单人全栈，Git 主干开发"],
             ["代码规模", "后端 125 文件 / 约 1.1 万行；前端 37 页面 / 约 1.7 万行；固件约 1100 行"]],
            widths=[1.2, 5.0])

    C.h1(doc, "2　工作分解（WBS）")
    C.table(doc, ["编号", "工作包", "主要产出", "预估", "状态"],
            [["WP-01", "调研与重构设计", "数据模型 v2、权限矩阵、接口清单", "2 周", "完成"],
             ["WP-02", "数据库与迁移", "V1~V8 迁移、21 表、种子数据", "0.5 周", "完成"],
             ["WP-03", "认证与 RBAC", "JWT、方法级权限、数据范围下推", "1.5 周", "完成"],
             ["WP-04", "协作域", "日程/看板/日志/评论/敏感词/团队/用户", "3 周", "完成"],
             ["WP-05", "考勤域", "设备、规则、打卡判定、日汇总、补卡审批", "2 周", "完成"],
             ["WP-06", "物联网接入", "ESP32 固件（UART/JSON/HMAC/MQTT）+ 模拟器", "1.5 周", "完成（模拟器）"],
             ["WP-07", "前端重建", "37 页面、三套日历、检索体系、仪表盘", "3 周", "完成"],
             ["WP-08", "审计与可观测", "审计 AOP、traceId、Prometheus 指标、Grafana 看板", "1 周", "完成"],
             ["WP-09", "测试", "单测 11、集成 5、接口断言 46、权限审计、压测", "1 周", "完成"],
             ["WP-10", "部署与运维", "compose、部署脚本、部署手册、云上上线", "0.5 周", "完成"]],
            widths=[0.7, 1.5, 2.4, 0.7, 0.9])

    C.h1(doc, "3　里程碑")
    C.table(doc, ["里程碑", "判定标准", "实际结果"],
            [["M1 设计冻结", "数据模型、权限矩阵、接口清单评审通过", "已完成"],
             ["M2 后端可跑", "Flyway 建库 + 登录 + 核心 CRUD 通", "已完成"],
             ["M3 前端可用", "37 页面可访问，权限内菜单正确", "已完成"],
             ["M4 打卡闭环", "设备上报 → 判定 → 日汇总 → 前端可见", "已完成（模拟器）"],
             ["M5 质量达标", "接口断言全绿、无 P0/P1 缺陷", "已完成（46/46、0 失败）"],
             ["M6 上线", "云服务器 5 容器 healthy、公网可访问", "已完成（81.70.102.116）"]],
            widths=[1.2, 3.0, 1.8])

    C.h1(doc, "4　资源计划")
    C.table(doc, ["资源", "配置", "用途"],
            [["人力", "1 人（全栈）", "需求/设计/编码/测试/部署/文档"],
             ["开发机", "Windows 11 + JDK17 + MySQL 8 + Node 20", "本地开发与自测"],
             ["容器环境", "Docker Desktop（WSL2）", "镜像构建与编排验证"],
             ["云服务器", "腾讯云轻量 4C4G（Rocky Linux 10）", "演示与验收环境"],
             ["硬件（可选）", "ESP32 + RC522", "真实刷卡验证；无硬件用模拟器"]],
            widths=[1.0, 2.6, 2.4])

    C.h1(doc, "5　质量保证计划")
    C.table(doc, ["手段", "频率", "判定标准"],
            [["单元测试", "每次提交", "考勤判定算法全绿"],
             ["接口断言（46 项）", "每次改动 / 上线前", "0 失败"],
             ["权限审计（6 角色 × 16 路由）", "权限相关改动后", "无「看得到摸不到」"],
             ["压测", "上线前 / 改索引后", "P95 ≤ 100ms、0 失败"],
             ["代码评审", "单人项目：自检清单", "钩子拦截密钥、编译零警告目标"]],
            widths=[1.8, 1.6, 2.6])

    C.h1(doc, "6　配置管理与交付物")
    C.h2(doc, "6.1　配置管理")
    for t in [
        "Git 主干开发，提交信息带类型前缀（feat/fix/docs/test/chore）。",
        "pre-commit 钩子拦截硬编码密码、64 位十六进制密钥、私钥文件。",
        "敏感配置全部走 .env（不入库），compose 与 application-prod 均为 fail-closed。",
        "数据库变更只用 Flyway 增量脚本，不手工改线上表结构。",
    ]:
        C.bullet(doc, t)
    C.h2(doc, "6.2　交付物清单")
    C.table(doc, ["类别", "交付物"],
            [["源代码", "后端（Spring Boot）、前端（Vue 3）、固件（ESP32）、部署脚本"],
             ["文档", "可行性研究、开发计划、需求规格、总体设计、详细设计、数据库设计、接口文档、"
                      "测试计划与报告、用户手册、部署说明、运维手册、系统图册"],
             ["测试", "单元测试、集成测试、接口断言脚本、权限审计脚本、压测脚本"],
             ["部署", "docker-compose、Dockerfile、Nginx 配置、部署脚本、健康检查"],
             ["运维", "备份脚本、监控配置（Prometheus/Grafana）、部署验证记录"]],
            widths=[0.9, 5.3])

    C.h1(doc, "7　风险与变更控制")
    C.table(doc, ["风险", "触发信号", "应对预案"],
            [["范围膨胀", "不断加功能导致延期", "每条新需求先问「是否影响本期验收」，不进本期就记入待办"],
             ["依赖不可用", "GitHub/Docker Hub 拉取失败", "切镜像源；服务器改用本地打包推送通道"],
             ["线上故障", "健康检查失败", "按运维手册 SOP 回滚；数据库先备份"],
             ["进度滞后", "里程碑未按期", "砍非核心功能（如看板拖拽美化），保交付主线"]],
            widths=[1.4, 2.2, 2.6])
    doc.save(os.path.join(outdir, "TeamPulse_项目开发计划书.docx"))


# ============================================================ 3 用户手册
def make_user(outdir):
    doc = C.new_document(
        "TeamPulse 用户手册",
        "6 个角色的操作指引与常见问题",
        ["适用地址：http://81.70.102.116/",
         "演示账号：admin / zhangsan / sunqi / wangwu / zhaoliu / lisi（除 admin 外密码 123456）",
         "文档版本 v1.0"],
    )

    C.h1(doc, "1　登录与入口")
    C.h2(doc, "1.1　登录")
    C.para(doc, "打开首页 → 输入账号密码 → 登录。首次登录且具备管理权限的账号会进入「选择入口」页，"
                "可二选一：管理后台（按权限展示菜单）或我的工作台（个人视角）。")
    C.h2(doc, "1.2　两套界面")
    C.table(doc, ["界面", "地址前缀", "适合谁", "包含什么"],
            [["管理后台", "/dashboard、/attendance、/users …", "管理员/人事/经理/负责人/审计", "部门与全公司范围的管理功能"],
             ["我的工作台", "/u/…", "所有人（默认）", "我的打卡、我的日程、我的日志、我的团队、个人信息"]],
            widths=[1.0, 2.2, 1.5, 1.4])
    C.note(doc, "右上角「切换面板」可在两套界面间往返；菜单只显示你有权限的项，看不到说明该功能不在你的职责内。")

    C.h1(doc, "2　角色速查")
    C.table(doc, ["角色", "能看到的管理菜单", "能做的写操作"],
            [["系统管理员", "全部", "用户/团队/设备/规则/敏感词/权限相关全部"],
             ["人事", "公司概览、考勤看板与记录、设备、考勤规则、补卡审批、日志、评论、审计、用户、团队",
              "考勤规则、设备查看、补卡审批、评论删除（他人需 comment:manage，人事没有）"],
             ["部门经理", "考勤看板与记录、设备、补卡审批、日程、看板、日志、评论、用户、团队",
              "补卡审批、日程/看板/团队管理、评论删除"],
             ["团队负责人", "考勤看板与记录、日程、看板、日志、评论、团队", "团队日程与看板维护"],
             ["普通员工", "（仅我的工作台）", "我的补卡申请、我的日志、我的日程"],
             ["审计专员", "公司概览、考勤看板与记录、设备、日程、看板、日志、评论、审计、用户、团队（全部只读）", "无写操作"]],
            widths=[0.9, 3.1, 2.2], font_size=8)

    C.h1(doc, "3　常用操作")
    C.h2(doc, "3.1　查看我的考勤")
    C.for_each = None
    for step in [
        "左侧进入「我的工作台 → 我的考勤」。",
        "顶部可切换月份；日历上不同颜色表示 正常 / 迟到 / 早退 / 缺勤。",
        "点击某一天查看当日打卡明细（设备、时间、状态）。",
    ]:
        C.numbered(doc, step)
    C.h2(doc, "3.2　提交补卡申请（员工）")
    for step in [
        "「我的考勤」→ 缺卡那天 → 补卡申请。",
        "选择补卡类型（上班/下班）、填写原因。",
        "提交后在「补卡记录」里可看状态：待审批 / 已通过 / 已驳回。",
    ]:
        C.numbered(doc, step)
    C.note(doc, "审批通过后会自动补一条打卡记录（来源标记为 MANUAL），并重算当日考勤。")
    C.h2(doc, "3.3　审批补卡（人事 / 经理）")
    for step in [
        "管理端 →「补卡审批」。",
        "列表默认显示待审批；点「通过」或「驳回」，可填写意见。",
        "已处理的单据不会再次出现待办；重复提交审批会被拒绝（提示资源冲突）。",
    ]:
        C.numbered(doc, step)
    C.h2(doc, "3.4　登记设备与放行端口（管理员）")
    for step in [
        "管理端 →「打卡设备」→ 注册设备，填写 SN 与安装位置。",
        "注册成功后会**一次性显示设备密钥**，请立即填入固件 secrets.h；关闭弹窗后无法再次查看。",
        "密钥泄露时点「重置密钥」轮换，旧密钥立即失效。",
        "确认腾讯云控制台防火墙已放行 1883（MQTT）或 80（HTTP 兜底）。",
    ]:
        C.numbered(doc, step)
    C.h2(doc, "3.5　配置考勤规则（人事）")
    C.para(doc, "管理端 →「考勤规则」：设置上班/下班时间、迟到宽限分钟数。保存后对新打卡立即生效；"
                "历史记录不会被追溯修改。")
    C.h2(doc, "3.6　查审计日志（管理员 / 审计）")
    C.para(doc, "管理端 →「审计日志」：可按时间、模块、动作、结果（成功/失败）过滤。"
                "每条记录含操作人、目标、耗时与 traceId；失败操作也会留痕（例如越权尝试）。")

    C.h1(doc, "4　常见问题")
    C.table(doc, ["现象", "原因", "怎么办"],
            [["登录提示「用户名或密码错误」", "账号或密码不对", "确认大小写；演示账号密码见本手册开头"],
             ["菜单里没有某个功能", "该功能不在你的角色权限内", "找管理员确认职责；不要尝试直接输 URL（会被拦回首页）"],
             ["打卡后记录里没有", "设备未注册 / 工号不对 / 时钟偏差 > 300s / 网络未通", "看设备状态是否 OFFLINE；核对工号；校准设备时间"],
             ["设备显示离线", "心跳未上报", "检查 MQTT 端口放行、账号密码、设备电量与网络"],
             ["页面提示「没有该操作的权限」", "后端拦截（前端已隐藏按钮，这是直接调接口才会出现）", "换有权限的账号操作"],
             ["报错带了 traceId", "该错误已进审计与日志", "把 traceId 发给管理员，可在服务端日志中精确定位"],
             ["忘记密码", "—", "联系管理员在「用户管理」里重置"]],
            widths=[1.8, 2.2, 2.2], font_size=8)

    C.h1(doc, "5　界面约定")
    C.table(doc, ["约定", "说明"],
            [["颜色", "蓝=主要操作，绿=通过/正常，橙=警告，红=删除/异常"],
             ["空状态", "列表无数据时显示占位说明，不是空白页"],
             ["失败提示", "顶部横幅提示 + 可重试按钮；带 traceId 便于报障"],
             ["权限收敛", "没有权限的菜单与按钮不渲染，而不是点了才报错"],
             ["分页", "统一支持 50/100/200 每页，单页上限 200"]],
            widths=[1.2, 5.0])
    doc.save(os.path.join(outdir, "TeamPulse_用户手册.docx"))


# ============================================================ 4 运维手册
def make_ops(outdir):
    doc = C.new_document(
        "TeamPulse 维护与运维手册",
        "巡检 / 监控 / 备份 / 发布 / 回滚 / 故障处理",
        ["线上：腾讯云轻量 4C4G，Rocky Linux 10，http://81.70.102.116/",
         "应用目录 /opt/TeamPulse，容器前缀 tp-",
         "文档版本 v1.0"],
    )

    C.h1(doc, "1　系统构成与责任边界")
    C.table(doc, ["组件", "容器", "端口", "职责", "故障影响"],
            [["Web/反代", "tp-frontend", "80", "静态站 + /api 反代", "全站不可用"],
             ["应用", "tp-backend", "8080（内网）", "业务逻辑", "接口 502"],
             ["数据库", "tp-mysql", "127.0.0.1:3306", "业务数据", "全站不可用"],
             ["缓存", "tp-redis", "6379（内网）", "nonce 防重放", "降级为本地缓存，不影响主流程"],
             ["消息", "tp-mosquitto", "1883", "设备接入", "设备无法上报，HTTP 兜底仍可用"],
             ["监控", "tp-prometheus / tp-grafana", "127.0.0.1:9090 / 3000", "指标与看板", "无业务影响"]],
            widths=[0.9, 1.6, 1.3, 1.3, 1.6], font_size=8)

    C.h1(doc, "2　日常巡检清单")
    C.h2(doc, "2.1　每日")
    C.table(doc, ["检查项", "命令", "正常表现"],
            [["容器状态", "docker compose ps", "5 个 healthy"],
             ["健康检查", "curl -s localhost/actuator/health", "{\"status\":\"UP\"}"],
             ["错误日志", "docker compose logs --since 24h backend \\| grep -i error", "无持续性报错"],
             ["磁盘", "df -h /", "使用率 < 80%"],
             ["内存/Swap", "free -m", "可用内存 > 300MB，swap 使用率低"]],
            widths=[1.1, 3.3, 1.8])
    C.h2(doc, "2.2　每周")
    for t in [
        "看 Grafana「TeamPulse 概览」：请求速率、错误率、P95、打卡成功/失败、幂等命中。",
        "看审计日志里 result=FAILURE 的分布，异常增多说明有人在试越权或前端有 bug。",
        "确认备份文件生成（deploy/backups/），随机抽一个校验能否 gunzip 解压。",
    ]:
        C.bullet(doc, t)
    C.h2(doc, "2.3　每月")
    for t in [
        "docker image prune -f 清理悬空镜像；docker system df 看空间占用。",
        "检查磁盘增长趋势，按「日志 50MB×3/容器」估算是否够用。",
        "复核账号：离职人员禁用；演示账号密码是否仍是弱口令。",
        "做一次备份恢复演练（见第 5 章）。",
    ]:
        C.bullet(doc, t)

    C.h1(doc, "3　监控与告警")
    C.table(doc, ["指标", "含义", "建议阈值", "异常时先看"],
            [["teampulse_api_requests_total", "请求量（按路由）", "突降 50% 说明链路断", "Nginx 与后端日志"],
             ["teampulse_api_failures_total", "4xx/5xx 比例", "5xx 占比 > 1%", "审计失败记录 + 后端异常栈"],
             ["teampulse_api_latency_seconds", "接口耗时 P95", "P95 > 200ms", "慢 SQL / 连接池 / GC"],
             ["teampulse_attendance_punch_total", "打卡成功与失败", "失败率 > 5%", "设备在线状态、验签错误码"],
             ["teampulse_attendance_idempotent_total", "幂等命中", "持续升高", "设备是否在重发（网络抖动）"],
             ["jvm_memory_used_bytes", "堆内存", "持续 > 80% 上限", "是否有内存泄漏（看 GC 频率）"]],
            widths=[2.1, 1.5, 1.4, 1.2], font_size=8)
    C.note(doc, "Prometheus 默认未配 Alertmanager：需要告警时在 deploy/prometheus 增加 rule_files 并接入企业微信/邮件。")

    C.h1(doc, "4　日志")
    C.table(doc, ["日志", "位置", "保留", "用途"],
            [["容器 stdout", "docker compose logs <服务>", "由 docker json-file 限制 50MB×3", "实时排障"],
             ["应用日志", "容器内 /app/logs（挂载 backend-logs 卷）", "按天滚动", "历史排查，含 traceId"],
             ["Nginx 日志", "容器内 /var/log/nginx", "容器生命周期", "访问与反代问题"],
             ["审计日志", "MySQL audit_log 表", "永久", "追责与合规"]],
            widths=[1.1, 2.6, 1.6, 1.1])
    C.para(doc, "定位一次具体报错：把用户提供的 traceId 拿到后端日志里 grep，"
                "再在审计日志按 trace_id 查同一请求的写入记录。")

    C.h1(doc, "5　备份与恢复")
    C.h2(doc, "5.1　备份")
    C.code_block(doc, "cd /opt/TeamPulse\n./deploy/scripts/backup.sh\n"
                      "# 产出 deploy/backups/teampulse-YYYYmmdd-HHMMSS.sql.gz\n"
                      "# 建议 crontab：30 3 * * * cd /opt/TeamPulse && ./deploy/scripts/backup.sh", size=8.5)
    C.h2(doc, "5.2　恢复（演练过）")
    C.code_block(doc, "docker compose stop backend\n"
                      "gunzip -c deploy/backups/<文件>.sql.gz | \\\n"
                      "  docker exec -i tp-mysql mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" teampulse\n"
                      "docker compose start backend\n"
                      "curl -s localhost/actuator/health", size=8.5)
    C.para(doc, "恢复后必须核对：表数量 21、最新迁移版本、抽查一条近期打卡记录是否在。")

    C.h1(doc, "6　发布与回滚")
    C.table(doc, ["场景", "操作"],
            [["常规发布（服务器能连 GitHub）", "bash deploy/scripts/deploy-server.sh"],
             ["常规发布（GitHub 不稳）", "开发机执行 .\\scripts\\deploy-from-local.ps1（打包上传→解包→重建→健康检查）"],
             ["只重启", "docker compose restart backend"],
             ["回滚代码", "git checkout <上一个 commit> && docker compose up -d --build backend frontend"],
             ["回滚数据", "先停后端 → 恢复备份 → 启后端（Flyway 只前进，破坏性迁移必须连库一起回滚）"]],
            widths=[2.2, 4.0])

    C.h1(doc, "7　故障处理 SOP")
    C.table(doc, ["现象", "排查顺序", "处置"],
            [["全站打不开", "① 安全组 80 是否放行 ② docker ps ③ nginx 日志 ④ 磁盘满", "按需重启 frontend；磁盘满先清镜像/日志"],
             ["接口 502", "① backend 是否 healthy ② 内存是否打满 ③ 数据库连接", "重启 backend；必要时临时加 swap"],
             ["容器反复重启", "① docker compose logs <服务> 第一条 ERROR ② 配置语法", "mosquitto 配置错误是最常见原因"],
             ["登录 401", "① 密码 ② 用户状态 ③ JWT 过期 ④ 服务器时间", "校准时间（timedatectl）；重置密码"],
             ["能登录但接口 403", "① 角色 ② 权限点 ③ 数据范围", "核对 role_permission；不要直接改库，走迁移"],
             ["设备上报失败", "① 端口 1883 ② MQTT 账号 ③ 设备密钥 ④ 时钟偏差", "按错误码 60001~60006 逐项排除"],
             ["磁盘满", "docker system df；du -sh /var/lib/docker", "prune 镜像与日志；确认 daemon.json 限制生效"],
             ["数据异常", "① 审计日志 ② 备份时间点", "评估后按备份恢复，禁止手工 UPDATE 生产库"]],
            widths=[1.3, 2.6, 2.3], font_size=8)

    C.h1(doc, "8　安全加固")
    C.table(doc, ["项", "现状", "建议"],
            [["SSH", "密钥登录已启用，密码登录仍开", "关闭密码登录：PasswordAuthentication no"],
             ["数据库", "3306 只绑回环", "保持；禁止对公网开放"],
             ["MQTT", "1883 明文 + 密码", "收敛来源 IP，或升级 8883 + TLS"],
             ["HTTPS", "未启用（仅 HTTP）", "配域名后按部署手册第 8 章开 443"],
             ["监控端口", "3000/9090 仅回环", "保持；用 SSH 隧道访问"],
             ["密钥管理", ".env 权限 600，不入库", "定期轮换 JWT_SECRET 与设备密钥"],
             ["审计", "20 个写操作留痕", "定期导出归档，防止被清理"]],
            widths=[1.1, 2.3, 2.8])

    C.h1(doc, "9　容量与扩容")
    C.table(doc, ["资源", "当前", "增长估算", "扩容动作"],
            [["打卡记录", "200 人 × 2 次/天 ≈ 400 行/天", "约 14.6 万行/年", "3 年内无需分表"],
             ["磁盘", "40G，当前用 ~7G", "镜像 2G + 数据 2G/年 + 日志 150M/月", "先清镜像与日志，再考虑升配"],
             ["内存", "3.7G + 2G swap", "后端 1G 上限 + MySQL 1G", "升到 8G 更稳"],
             ["并发", "20 并发 P95 40ms", "百人规模足够", "多实例前需先解决定时任务重复执行"]],
            widths=[1.0, 2.3, 1.8, 1.9], font_size=8)

    C.h1(doc, "10　常用命令速查")
    C.code_block(doc, """cd /opt/TeamPulse                  # 应用目录
docker compose ps                  # 容器状态
docker compose logs -f backend     # 跟踪后端日志
docker compose restart backend     # 重启后端
docker compose down && up -d       # 全量重启
bash deploy/scripts/deploy-server.sh   # 幂等发布
./deploy/scripts/backup.sh         # 备份
cat .env                           # 查看全部密码（权限 600）
docker exec -it tp-mysql mysql -uroot -p$(grep ^MYSQL_ROOT_PASSWORD= .env | cut -d= -f2) teampulse
curl -s localhost/actuator/health  # 健康检查
ssh -L 3000:127.0.0.1:3000 root@81.70.102.116   # 隧道看 Grafana""", size=8)
    doc.save(os.path.join(outdir, "TeamPulse_维护与运维手册.docx"))


# ============================================================ 5 部署说明（md → docx）
def md_to_docx(md_path, out_path, title, subtitle):
    doc = C.new_document(title, subtitle, ["由 docs/部署手册.md 自动转换", "文档版本 v1.0"])
    lines = open(md_path, encoding="utf-8").read().split("\n")
    buf, i = [], 0
    while i < len(lines):
        line = lines[i]
        # 代码块
        if line.startswith("```"):
            lang = line[3:].strip()
            i += 1
            code = []
            while i < len(lines) and not lines[i].startswith("```"):
                code.append(lines[i])
                i += 1
            C.code_block(doc, "\n".join(code), size=8)
            i += 1
            continue
        # 表格
        if line.strip().startswith("|") and i + 1 < len(lines) and re.match(r"^\s*\|[\s:|-]+\|\s*$", lines[i + 1]):
            header = [c.strip() for c in line.strip().strip("|").split("|")]
            i += 2
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append([c.strip().replace("`", "") for c in lines[i].strip().strip("|").split("|")])
                i += 1
            C.table(doc, header, rows, font_size=8)
            continue
        # 标题
        m = re.match(r"^(#{1,4})\s+(.*)$", line)
        if m:
            level, text = len(m.group(1)), m.group(2).strip()
            if level == 1:
                C.h1(doc, text)
            elif level == 2:
                C.h2(doc, text)
            else:
                C.h3(doc, text)
            i += 1
            continue
        # 引用
        if line.startswith(">"):
            C.note(doc, line.lstrip("> ").replace("**", ""))
            i += 1
            continue
        # 列表
        m = re.match(r"^\s*[-*]\s+(.*)$", line)
        if m:
            C.bullet(doc, m.group(1).replace("**", "").replace("`", ""))
            i += 1
            continue
        m = re.match(r"^\s*\d+\.\s+(.*)$", line)
        if m:
            C.numbered(doc, m.group(1).replace("**", "").replace("`", ""))
            i += 1
            continue
        # 段落
        if line.strip() and not line.startswith("---"):
            buf.append(line.strip())
            if i + 1 >= len(lines) or not lines[i + 1].strip():
                C.para(doc, " ".join(buf).replace("**", "").replace("`", ""))
                buf = []
        i += 1
    doc.save(out_path)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--outdir", default=os.path.join(ROOT, "docs"))
    args = ap.parse_args()
    os.makedirs(args.outdir, exist_ok=True)

    make_fs(args.outdir)
    print("  ✓ 可行性研究报告")
    make_plan(args.outdir)
    print("  ✓ 项目开发计划书")
    make_user(args.outdir)
    print("  ✓ 用户手册")
    make_ops(args.outdir)
    print("  ✓ 维护与运维手册")

    md = os.path.join(ROOT, "docs", "部署手册.md")
    if os.path.exists(md):
        md_to_docx(md, os.path.join(args.outdir, "TeamPulse_部署说明.docx"),
                   "TeamPulse 部署说明", "从零到上线的逐步操作指南")
        print("  ✓ 部署说明（由 部署手册.md 转换）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
