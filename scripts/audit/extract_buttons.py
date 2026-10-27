# -*- coding: utf-8 -*-
"""从前端源码抽取所有交互控件（按钮/链接/开关）及其权限门控，生成测试清单。"""
import glob
import json
import os
import re

SRC = r"E:\codestorge\frontend\src"
OUT = r"E:\codestorge\.run-logs\button-inventory.json"

# 组件里常见写法：@click="xxx" / @confirm="xxx" / @change="xxx" / @command="xxx"
HANDLER_RE = re.compile(r'@(click|confirm|change|command|submit)="([^"]{1,120})"')
LABEL_RE = re.compile(r'>\s*([^<>{}\n]{1,20}?)\s*<')
PERM_RE = re.compile(r"hasPerm\(\s*'([^']+)'\s*\)")
ANYPERM_RE = re.compile(r"hasAnyPerm\(\s*\[([^\]]+)\]")

items = []
for path in sorted(glob.glob(os.path.join(SRC, "**", "*.vue"), recursive=True)):
    rel = os.path.relpath(path, SRC).replace("\\", "/")
    lines = open(path, encoding="utf-8").read().split("\n")
    for idx, line in enumerate(lines):
        for m in HANDLER_RE.finditer(line):
            event, handler = m.group(1), m.group(2)
            # 找同一行或上下 3 行内的权限门控与按钮文案
            window = "\n".join(lines[max(0, idx - 3): idx + 4])
            perms = PERM_RE.findall(window)
            anyperms = []
            for g in ANYPERM_RE.findall(window):
                anyperms += re.findall(r"'([^']+)'", g)
            label = ""
            lm = LABEL_RE.search(line)
            if lm:
                label = lm.group(1).strip()
            if not label:
                for back in range(idx, max(0, idx - 6), -1):
                    lm2 = LABEL_RE.search(lines[back])
                    if lm2:
                        label = lm2.group(1).strip()
                        break
            items.append({
                "view": rel,
                "line": idx + 1,
                "event": event,
                "handler": handler,
                "label": label,
                "perm": perms[0] if perms else None,
                "anyPerm": anyperms,
            })

# 去重（同一 view + handler 只留一条）
seen = set()
uniq = []
for it in items:
    key = (it["view"], it["handler"], it["label"])
    if key in seen:
        continue
    seen.add(key)
    uniq.append(it)

with open(OUT, "w", encoding="utf-8") as fh:
    json.dump(uniq, fh, ensure_ascii=False, indent=2)

print(f"共抽取 {len(uniq)} 个交互控件，分布在 {len({i['view'] for i in uniq})} 个视图")
print("\n=== 按视图统计 ===")
by_view = {}
for it in uniq:
    by_view.setdefault(it["view"], []).append(it)
for v, arr in sorted(by_view.items(), key=lambda kv: -len(kv[1])):
    print(f"  {v:<42} {len(arr):>2} 个")
print(f"\n清单已写入 {OUT}")
