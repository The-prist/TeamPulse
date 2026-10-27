#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""静态检查：`<script setup>` 里用了 `x.value` 却从未声明 x。

这类错误的后果不是接口报错，而是**组件渲染时抛 ReferenceError**，
被 main.js 的全局错误处理捕获，整页显示「页面出现异常，请刷新后重试」——
接口级测试（e2e.mjs）完全测不到，所以单独做一个检查。

用法：
    python scripts/audit/check_undefined_refs.py
退出码 1 表示发现问题，可直接接入 CI。
"""
import glob
import os
import re
import sys

SRC = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "frontend", "src")

# 常被误用的短名（出现在 computed / 模板里但忘了声明）
WATCH = ["detail", "schedule", "kanban", "row", "form"]


def strip_comments(code: str) -> str:
    """去掉 // 行注释与 /* */ 块注释，避免注释里提到的名字被误判。"""
    code = re.sub(r"/\*[\s\S]*?\*/", "", code)
    code = re.sub(r"(?m)^\s*//.*$", "", code)
    code = re.sub(r"(?m)//.*$", "", code)
    return code


def main() -> int:
    issues = []
    for path in glob.glob(os.path.join(SRC, "**", "*.vue"), recursive=True):
        raw = open(path, encoding="utf-8").read()
        if "<script" not in raw:
            continue
        script = strip_comments(raw.split("<script", 1)[1])
        for name in WATCH:
            used = re.search(rf"\b{name}\.value\b", script)
            if not used:
                continue
            declared = re.search(rf"^(?:const|let|var)\s+{name}\s*=", script, re.M)
            # 也可能是箭头函数/回调的形参：item => ...、 (item) => ...、, item) =>
            if not declared:
                declared = re.search(rf"(?:\b{name}\s*=>|\(\s*{name}\s*[,)]|,\s*{name}\s*\))", script)
            if not declared:
                line = script[: used.start()].count("\n") + 1
                issues.append((os.path.basename(path), name, line))

    if issues:
        print("发现未声明的 ref 引用（会导致整页渲染异常）：")
        for f, n, l in issues:
            print(f"  {f:<28} 使用 {n}.value 但未声明（script 第 {l} 行）")
        return 1

    print("✓ 未发现「使用了未声明的 ref」这类渲染级错误")
    return 0


if __name__ == "__main__":
    sys.exit(main())
