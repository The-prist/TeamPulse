#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""解析控制器源码，导出「HTTP 方法 + 完整路径 + 权限表达式」清单。

OpenAPI 规范里没有 @PreAuthorize 信息（springdoc 不反射自定义注解），
所以权限列必须从源码解析后合并。

用法：
    python scripts/docs/extract_endpoints.py --out .run-logs/endpoints.json
"""
import argparse
import json
import os
import re
import sys

BACKEND = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                       "backend", "src", "main", "java")

MAP_RE = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping(?:\(\s*(?:value\s*=\s*)?\"([^\"]*)\"[^)]*\))?")
PRE_RE = re.compile(r'@PreAuthorize\(\s*"([^"]+)"\s*\)')
BASE_RE = re.compile(r'@RequestMapping\(\s*(?:value\s*=\s*)?\"([^\"]+)\"')


def blank_comments(src: str) -> str:
    """把注释替换成等长空白，保持偏移量与行号不变。

    否则 Javadoc 里出现的 @PreAuthorize 示例（例如「不要用 hasRole('ADMIN')」）
    会被误当成真实注解，导致权限列写错。
    """
    src = re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), src, flags=re.S)
    src = re.sub(r"//[^\n]*", lambda m: " " * len(m.group(0)), src)
    return src


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    endpoints = []
    for dirpath, _, filenames in os.walk(BACKEND):
        for fn in sorted(filenames):
            if not fn.endswith("Controller.java"):
                continue
            path = os.path.join(dirpath, fn)
            src = blank_comments(open(path, encoding="utf-8", errors="replace").read())
            base = BASE_RE.search(src)
            base_path = base.group(1) if base else ""

            # 类级 @PreAuthorize（5 个控制器把权限写在类上，对全部方法生效）
            cls_match = re.search(r'@PreAuthorize\(\s*"([^"]+)"\s*\)[^;{]*?public\s+class', src, re.S)
            class_auth = cls_match.group(1) if cls_match else ""

            maps = list(MAP_RE.finditer(src))
            for idx, m in enumerate(maps):
                method = m.group(1).upper()
                sub = m.group(2) or ""
                full = (base_path.rstrip("/") + "/" + sub.lstrip("/")).rstrip("/") or "/"

                # 注解既可写在映射前也可写在映射后：取「本映射到下一个映射」之间的注解
                seg_end = maps[idx + 1].start() if idx + 1 < len(maps) else len(src)
                window = src[m.start():seg_end]
                pre = PRE_RE.findall(window)
                if not pre:
                    # 再向前看 200 字符（注解在映射之前的写法）
                    pre = PRE_RE.findall(src[max(0, m.start() - 200):m.start()])
                auth = pre[0] if pre else class_auth

                # 行号
                line = src.count("\n", 0, m.start()) + 1
                endpoints.append({
                    "method": method,
                    "path": full,
                    "preAuthorize": auth,
                    "controller": fn[:-5],
                    "line": line,
                })

    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(endpoints, fh, ensure_ascii=False, indent=2)

    with_auth = sum(1 for e in endpoints if e["preAuthorize"])
    print(f"已解析 {len(endpoints)} 个端点，其中 {with_auth} 个带 @PreAuthorize → {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
