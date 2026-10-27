#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把所有文档与 README 里的服务器地址统一成正式域名。

规则（按顺序替换，先长后短）：
  http://81.70.102.116/  → http://www.team-pulse.site/
  http://81.70.102.116   → http://www.team-pulse.site
  root@81.70.102.116     → root@www.team-pulse.site
  ssh root@…             （同上，自然覆盖）
  81.70.102.116          → www.team-pulse.site

特例：docs/部署验证记录.md 里有一行专门登记「公网 IP」，改成「访问域名（解析到 IP）」，
保留 IP 便于运维核对，其余一律换域名。

用法：
    python scripts/docs/unify_domain.py --dry-run
    python scripts/docs/unify_domain.py
"""
import argparse
import glob
import os

from docx import Document

DESK = os.path.join(os.environ["USERPROFILE"], "Desktop")
REPO = r"E:\codestorge"

DOMAIN = "www.team-pulse.site"
IP = "81.70.102.116"

# 按顺序：先整体 URL，再单独的 IP
RULES = [
    (f"http://{IP}/", f"http://{DOMAIN}/"),
    (f"http://{IP}", f"http://{DOMAIN}"),
    (f"https://{IP}", f"https://{DOMAIN}"),
    (f"root@{IP}", f"root@{DOMAIN}"),
    (f"{IP}:80", f"{DOMAIN}:80"),
    (IP, DOMAIN),
]

# 逐文件特例：(文件, 旧片段, 新片段)
SPECIAL = {
    "部署验证记录.md": [
        ("| 公网 IP | `81.70.102.116`（ap-beijing） |",
         "| 访问域名 | `www.team-pulse.site`（解析到 `81.70.102.116`，ap-beijing） |"),
    ],
}


def apply_rules(text):
    for old, new in RULES:
        text = text.replace(old, new)
    return text


# ------------------------------------------------------------------ Markdown
def fix_md(path):
    name = os.path.basename(path)
    raw = open(path, encoding="utf-8").read()
    original = raw
    for old, new in SPECIAL.get(name, []):
        raw = raw.replace(old, new)
    raw = apply_rules(raw)
    if raw != original:
        open(path, "w", encoding="utf-8", newline="\n").write(raw)
        return original.count(IP)
    return 0


# ------------------------------------------------------------------ DOCX
def fix_para(p):
    """返回替换次数；跨 run 的情况合并到首个 run（保留其格式）。"""
    text = p.text
    if IP not in text:
        return 0
    new_text = apply_rules(text)
    # 优先逐个 run 替换，保住行内格式
    changed = 0
    for r in p.runs:
        if IP in r.text:
            r.text = apply_rules(r.text)
            changed += 1
    if changed and IP not in p.text:
        return 1
    # 仍存在（IP 被拆到多个 run）：合并
    if p.runs:
        first = p.runs[0]
        for r in list(p.runs)[1:]:
            r._element.getparent().remove(r._element)
        first.text = new_text
        return 1
    return 0


def fix_docx(path):
    doc = Document(path)
    n = 0
    for p in doc.paragraphs:
        n += fix_para(p)
    for t in doc.tables:
        for row in t.rows:
            for cell in row.cells:
                for p in cell.paragraphs:
                    n += fix_para(p)
    for s in doc.sections:
        for part in (s.header, s.footer):
            if part is None:
                continue
            for p in part.paragraphs:
                n += fix_para(p)
    if n:
        doc.save(path)
    return n


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    md_targets = [os.path.join(REPO, "README.md")]
    md_targets += sorted(glob.glob(os.path.join(REPO, "docs", "*.md")))

    docx_targets = []
    docx_targets += sorted(glob.glob(os.path.join(REPO, "docs", "*.docx")))
    docx_targets += sorted(glob.glob(os.path.join(DESK, "TeamPulse_交付文档", "文档", "*.docx")))
    docx_targets += sorted(glob.glob(os.path.join(DESK, "项目过程文档", "*.docx")))
    docx_targets = [p for p in docx_targets if not os.path.basename(p).startswith("~$")]

    total_md = total_docx = 0
    print("=== Markdown ===")
    for p in md_targets:
        if not os.path.exists(p):
            continue
        if args.dry_run:
            raw = open(p, encoding="utf-8").read()
            c = raw.count(IP)
        else:
            c = fix_md(p)
        if c:
            print(f"  ✓ {os.path.relpath(p, REPO) if p.startswith(REPO) else os.path.basename(p):<40} {c} 处")
            total_md += c

    print("\n=== DOCX ===")
    for p in docx_targets:
        if args.dry_run:
            doc = Document(p)
            c = sum(1 for para in doc.paragraphs if IP in para.text)
            for t in doc.tables:
                for row in t.rows:
                    for cell in row.cells:
                        for para in cell.paragraphs:
                            if IP in para.text:
                                c += 1
        else:
            c = fix_docx(p)
        if c:
            print(f"  ✓ {os.path.basename(p):<34} {c} 段")
            total_docx += c

    print(f"\n合计：md {total_md} 处，docx {total_docx} 段{'（dry-run）' if args.dry_run else ''}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
