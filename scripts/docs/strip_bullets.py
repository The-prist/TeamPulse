#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""去掉段落前的圆点（项目符号）。

做法：把 List Bullet 系列的段落改回 Normal 样式（圆点来自样式里的编号定义），
并清掉段落级的缩进/编号残留，重新按「首行缩进 2 字符」排版。

保留：List Number（用户手册里的 1. 2. 3. 步骤编号，去掉会丢失操作顺序）；
      表格单元格里的 — → 等符号（它们是内容，不是项目符号）。
"""
import argparse
import glob
import os

from docx import Document
from docx.oxml.ns import qn
from docx.shared import Pt

BULLET_STYLES = {"List Bullet", "List Bullet 2", "List Bullet 3", "List Paragraph"}
SIZE_BODY = 10.5


def strip_bullet(p, normal_style):
    changed = False
    # 1) 样式改回 Normal（样式里的 numPr 随之消失）
    if p.style.name in BULLET_STYLES:
        p.style = normal_style
        changed = True
    # 2) 段落级 numPr 残留
    ppr = p._p.pPr
    if ppr is not None:
        numpr = ppr.find(qn("w:numPr"))
        if numpr is not None:
            ppr.remove(numpr)
            changed = True
        ind = ppr.find(qn("w:ind"))
        if ind is not None:
            ppr.remove(ind)
            changed = True
    # 3) 重新设置首行缩进
    pf = p.paragraph_format
    pf.left_indent = None
    pf.first_line_indent = Pt(SIZE_BODY * 2)
    return changed


def process(path, dry_run=False):
    doc = Document(path)
    normal_style = doc.styles["Normal"]
    n = 0
    for p in doc.paragraphs:
        if p.text.strip():
            if strip_bullet(p, normal_style):
                n += 1
    if not dry_run and n:
        doc.save(path)
    return n


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", action="append", default=[])
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    targets = []
    for d in args.dir:
        targets += sorted(glob.glob(os.path.join(d, "*.docx")))
    targets = [t for t in targets if not os.path.basename(t).startswith("~$")]

    total = 0
    for path in targets:
        n = process(path, dry_run=args.dry_run)
        total += n
        print(f"  {'✓' if n else '·'} {os.path.basename(path):<40} 去点 {n:>3} 段")
    print(f"\n合计 {total} 段{'（dry-run）' if args.dry_run else ''}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
