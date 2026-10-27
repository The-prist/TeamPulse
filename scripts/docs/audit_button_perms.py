#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""扫描前端页面里的「写操作按钮」，找出没有权限守卫的那些。

判定标准：
  - 按钮文案匹配写操作关键词（新增/编辑/删除/审批/保存/停用/启用/重置/导入/导出/生成/轮换…）
  - 且该按钮所在行/所在 <el-button> 标签内没有 v-if 权限判断（hasPerm / canXxx）

输出：文件 + 行号 + 按钮文案 + 是否已有守卫
"""
import os
import re
import sys

SRC = r"E:\codestorge\frontend\src\views"
WRITE_WORDS = (
    "新增", "新建", "创建", "添加", "编辑", "修改", "删除", "移除", "清空",
    "审批", "通过", "驳回", "保存", "提交", "停用", "启用", "禁用", "重置",
    "导入", "导出", "生成", "轮换", "注册", "上移", "下移", "拖拽",
)
# 已有守卫的写法
GUARD_RE = re.compile(r'v-if="[^"]*(hasPerm|hasAnyPerm|can[A-Z]|isAdmin|isSysAdmin)[^"]*"')
BTN_RE = re.compile(r"<el-button\b[^>]*>", re.S)


def main() -> int:
    rows = []
    for dirpath, _, filenames in os.walk(SRC):
        for fn in sorted(filenames):
            if not fn.endswith(".vue"):
                continue
            path = os.path.join(dirpath, fn)
            src = open(path, encoding="utf-8", errors="replace").read()
            lines = src.split("\n")
            for i, line in enumerate(lines, start=1):
                if "<el-button" not in line and "<el-dropdown-item" not in line and "@click" not in line:
                    continue
                has_word = any(w in line for w in WRITE_WORDS)
                if not has_word:
                    continue
                # 同一行或紧邻上下 3 行是否有权限守卫
                window = "\n".join(lines[max(0, i - 4): min(len(lines), i + 2)])
                guarded = bool(GUARD_RE.search(window))
                label = re.sub(r"<[^>]+>", "", line).strip()[:24]
                rows.append((os.path.relpath(path, SRC).replace("\\", "/"), i, label, guarded))

    gaps = [r for r in rows if not r[3]]
    guarded = [r for r in rows if r[3]]
    print(f"写操作按钮/动作共 {len(rows)} 个：已有守卫 {len(guarded)}，无守卫 {len(gaps)}\n")
    cur = None
    for f, i, label, _ in gaps:
        if f != cur:
            print(f"\n--- {f} ---")
            cur = f
        print(f"  L{i:<4} {label}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
