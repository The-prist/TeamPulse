# -*- coding: utf-8 -*-
"""统计所有文档里出现的状态符号，为统一替换做准备。"""
import collections
import glob
import os

from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph

DIRS = [
    os.path.join(os.environ["USERPROFILE"], "Desktop", "TeamPulse_交付文档", "文档"),
    os.path.join(os.environ["USERPROFILE"], "Desktop", "项目过程文档"),
]

# 关注的符号（含 emoji 与几何符号）
SYMBOLS = {
    "\u2705": "✅ 白色粗对钩（emoji）",
    "\u2714": "✔ 粗对钩",
    "\u2611": "☑ 带框对钩",
    "\u2713": "✓ 对钩（Unicode）",
    "\u274C": "❌ 叉（emoji）",
    "\u2717": "✗ 叉",
    "\u2718": "✘ 粗叉",
    "\u2715": "✕ 乘号叉",
    "\u2716": "✖ 加粗叉",
    "\u26A0": "⚠ 警告（emoji 风格）",
    "\u25D0": "◐ 半圆（部分实现）",
    "\u25CB": "○ 空心圆（未实现）",
    "\u25CF": "● 实心圆",
    "\uFE0F": "️ 变体选择符（让 emoji 彩色显示）",
    "\u221A": "√ 根号（中式对钩）",
    "\u00D7": "× 乘号",
    "\u2714\ufe0f": "✔️",
}


def blocks(doc):
    for c in doc.element.body.iterchildren():
        if c.tag.endswith('}p'):
            yield Paragraph(c, doc)
        elif c.tag.endswith('}tbl'):
            yield Table(c, doc)


def text_of(b):
    return b.text if isinstance(b, Paragraph) else " ".join(x.text for r in b.rows for x in r.cells)


def main():
    files = []
    for d in DIRS:
        files += sorted(glob.glob(os.path.join(d, "*.docx")))
    files = [f for f in files if not os.path.basename(f).startswith("~$")]

    grand = collections.Counter()
    print(f"扫描 {len(files)} 份文档\n")
    for path in files:
        doc = Document(path)
        cnt = collections.Counter()
        for b in blocks(doc):
            for ch, name in SYMBOLS.items():
                c = text_of(b).count(ch)
                if c:
                    cnt[ch] += c
        if cnt:
            print(f"■ {os.path.basename(path)}")
            for ch, c in cnt.most_common():
                print(f"    {ch!r} × {c:<4} {SYMBOLS[ch]}")
            grand.update(cnt)
    print("\n=== 全部文档合计 ===")
    for ch, c in grand.most_common():
        print(f"  {ch!r} × {c:<5} {SYMBOLS[ch]}")


if __name__ == "__main__":
    main()
