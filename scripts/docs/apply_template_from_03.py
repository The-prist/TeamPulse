#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""以《03 需求规格说明书》为模板，把同一套格式套到其他所有文档。

从 03 提取并统一的项目：
  页面     A4 210×297mm，页边距 22mm，页眉/页脚距离 12.7mm（03 原为 Letter，属 Word 默认，统一为 A4）
  样式     Normal：宋体 + Times New Roman，五号(10.5pt)，行距 1.5，首行缩进 21pt
           Heading 1/2/3：黑体 + Times New Roman，16/14/12pt，加粗，纯黑，段前 12/6/6pt
           docDefaults 字号同步为 21 半磅（避免未显式设字号的文本走 11pt）
  表格     2 行以上的表格统一挂 Table Grid 样式（0.5pt 单线），表头行：
           底色 #E8EEF6、文字加粗居中、跨页重复
  正文     每个 run 绝对赋值：正文/表格 宋体五号，标题黑体，代码 Consolas 9pt
  符号     ✓ △ ✗ — 等状态符号统一 Times New Roman + 宋体
  页脚     居中页码（宋体五号）

用法：
    python scripts/docs/apply_template_from_03.py --dir <目录> [--dir ...] [--template <03.docx>] [--dry-run]
"""
import argparse
import glob
import os

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Mm, Pt, RGBColor

HEI = "黑体"
SONG = "宋体"
TNR = "Times New Roman"
MONO = ("Consolas", "Courier New", "Courier")

SIZE_BODY = 10.5
SIZE_TABLE = 10.5
SIZE_CODE = 9
SIZE_FOOTER = 10.5
SIZES_H = {"Heading 1": 16, "Heading 2": 14, "Heading 3": 12}

PAGE_W, PAGE_H = 210, 297
MARGIN = 22
HEADER_FILL = "E8EEF6"
BULLET_STYLES = {"List Bullet", "List Bullet 2", "List Bullet 3", "List Paragraph"}


# ------------------------------------------------------------------ 字体工具
def rfonts(run):
    return run._element.get_or_add_rPr().get_or_add_rFonts()


def set_run(run, east_asia, size, bold=None, mono=False):
    name = MONO[0] if mono else TNR
    run.font.name = name
    rf = rfonts(run)
    rf.set(qn("w:ascii"), name)
    rf.set(qn("w:hAnsi"), name)
    rf.set(qn("w:eastAsia"), east_asia)
    run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold


def set_style(style, east_asia, size, bold=None):
    style.font.name = TNR
    style.font.size = Pt(size)
    if bold is not None:
        style.font.bold = bold
    rf = style.element.get_or_add_rPr().get_or_add_rFonts()
    rf.set(qn("w:ascii"), TNR)
    rf.set(qn("w:hAnsi"), TNR)
    rf.set(qn("w:eastAsia"), east_asia)


def is_mono(run):
    return any(m.lower() in (run.font.name or "").lower() for m in MONO)


# ------------------------------------------------------------------ 页面
def fix_page(doc):
    for s in doc.sections:
        s.page_width = Mm(PAGE_W)
        s.page_height = Mm(PAGE_H)
        s.top_margin = s.bottom_margin = Mm(MARGIN)
        s.left_margin = s.right_margin = Mm(MARGIN)
        s.header_distance = Mm(12.7)
        s.footer_distance = Mm(12.7)


def fix_footer(doc):
    for s in doc.sections:
        footer = s.footer
        has = any("PAGE" in p._p.xml for p in footer.paragraphs)
        if has:
            for p in footer.paragraphs:
                for r in p.runs:
                    set_run(r, SONG, SIZE_FOOTER)
            continue
        p = footer.paragraphs[0] if footer.paragraphs else footer.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p.add_run()
        for tag, attr in (("w:fldChar", {"w:fldCharType": "begin"}),
                          ("w:instrText", None),
                          ("w:fldChar", {"w:fldCharType": "end"})):
            node = OxmlElement(tag)
            if attr:
                for k, v in attr.items():
                    node.set(qn(k), v)
            else:
                node.set(qn("xml:space"), "preserve")
                node.text = " PAGE "
            run._r.append(node)
        set_run(run, SONG, SIZE_FOOTER)


# ------------------------------------------------------------------ 样式
def fix_styles(doc):
    styles = doc.styles
    set_style(styles["Normal"], SONG, SIZE_BODY)
    pf = styles["Normal"].paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    pf.space_before = Pt(0)
    pf.space_after = Pt(0)
    pf.first_line_indent = Pt(SIZE_BODY * 2)

    for name, size in SIZES_H.items():
        try:
            st = styles[name]
        except KeyError:
            continue
        set_style(st, HEI, size, bold=True)
        st.font.color.rgb = RGBColor(0, 0, 0)
        spf = st.paragraph_format
        spf.first_line_indent = Pt(0)
        spf.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
        spf.space_before = Pt(12 if name == "Heading 1" else 6)
        spf.space_after = Pt(6)

    for name in ("List Bullet", "List Number", "List Paragraph"):
        if name in [s.name for s in styles]:
            set_style(styles[name], SONG, SIZE_BODY)

    # docDefaults 字号对齐 21 半磅
    dd = styles.element.find(qn("w:docDefaults"))
    if dd is not None:
        rpr = dd.find(qn("w:rPrDefault"))
        if rpr is not None:
            inner = rpr.find(qn("w:rPr"))
            if inner is None:
                inner = OxmlElement("w:rPr")
                rpr.append(inner)
            for tag in ("w:sz", "w:szCs"):
                el = inner.find(qn(tag))
                if el is None:
                    el = OxmlElement(tag)
                    inner.append(el)
                el.set(qn("w:val"), "21")


# ------------------------------------------------------------------ 表格
def _add_borders(tbl):
    """文档里没有 Table Grid 样式时，直接写死一套 0.5pt 单线边框。"""
    tblPr = tbl._tbl.tblPr
    old = tblPr.find(qn("w:tblBorders"))
    if old is not None:
        tblPr.remove(old)
    borders = OxmlElement("w:tblBorders")
    for side in ("top", "left", "bottom", "right", "insideH", "insideV"):
        el = OxmlElement(f"w:{side}")
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), "4")
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), "auto")
        borders.append(el)
    tblPr.append(borders)


def fix_table(tbl):
    rows, cols = len(tbl.rows), len(tbl.columns)
    if rows < 2 or cols < 2:
        return False  # 代码块/图容器不动
    styled = False
    try:
        tbl.style = "Table Grid"
        styled = tbl._tbl.tblPr.find(qn("w:tblStyle")) is not None
    except Exception:
        styled = False
    if not styled:
        _add_borders(tbl)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER

    # 表头：底色 + 加粗居中 + 跨页重复
    header = tbl.rows[0]
    trpr = header._tr.get_or_add_trPr()
    if trpr.find(qn("w:tblHeader")) is None:
        trpr.append(OxmlElement("w:tblHeader"))
    for cell in header.cells:
        tcpr = cell._tc.get_or_add_tcPr()
        shd = tcpr.find(qn("w:shd"))
        if shd is None:
            shd = OxmlElement("w:shd")
            tcpr.append(shd)
        shd.set(qn("w:val"), "clear")
        shd.set(qn("w:color"), "auto")
        shd.set(qn("w:fill"), HEADER_FILL)
        for p in cell.paragraphs:
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            for r in p.runs:
                if r.text.strip():
                    r.bold = True
    return True


# ------------------------------------------------------------------ 正文
def fix_text(doc):
    normal = doc.styles["Normal"]
    for p in doc.paragraphs:
        if not p.text.strip():
            continue
        # 去项目符号
        if p.style.name in BULLET_STYLES:
            p.style = normal
            ppr = p._p.pPr
            if ppr is not None:
                for tag in ("w:numPr", "w:ind"):
                    el = ppr.find(qn(tag))
                    if el is not None:
                        ppr.remove(el)

        style_name = p.style.name or ""
        runs = [r for r in p.runs if r.text.strip()]
        if not runs:
            continue

        if all(is_mono(r) for r in runs):
            for r in runs:
                set_run(r, SONG, SIZE_CODE, mono=True)
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE
            continue

        if style_name.startswith("Heading"):
            size = SIZES_H.get(style_name, 12)
            for r in runs:
                set_run(r, HEI, size, bold=True)
            p.paragraph_format.first_line_indent = Pt(0)
            continue

        max_size = max((r.font.size.pt for r in runs if r.font.size), default=0)
        if p.alignment == WD_ALIGN_PARAGRAPH.CENTER or max_size >= 18:
            size = 22 if max_size >= 18 else 14
            for r in runs:
                set_run(r, HEI, size)
            p.paragraph_format.first_line_indent = Pt(0)
            continue

        for r in runs:
            if is_mono(r):
                set_run(r, SONG, SIZE_CODE, mono=True)
            else:
                set_run(r, SONG, SIZE_BODY)
        p.paragraph_format.first_line_indent = Pt(SIZE_BODY * 2)
        p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE

    for table in doc.tables:
        single = len(table.rows) == 1 and len(table.columns) == 1
        for row in table.rows:
            for cell in row.cells:
                for p in cell.paragraphs:
                    runs = [r for r in p.runs if r.text.strip()]
                    if not runs:
                        continue
                    if single or all(is_mono(r) for r in runs):
                        for r in runs:
                            set_run(r, SONG, SIZE_CODE, mono=True)
                        p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE
                        continue
                    for r in runs:
                        set_run(r, SONG, SIZE_TABLE)
                    p.paragraph_format.first_line_indent = Pt(0)
                    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE


def apply(path, dry_run=False):
    doc = Document(path)
    fix_page(doc)
    fix_styles(doc)
    n_tbl = sum(1 for t in doc.tables if fix_table(t))
    fix_text(doc)
    fix_footer(doc)
    if not dry_run:
        doc.save(path)
    return n_tbl


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", action="append", default=[])
    ap.add_argument("--file", nargs="*", default=[])
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    targets = list(args.file)
    for d in args.dir:
        targets += sorted(glob.glob(os.path.join(d, "*.docx")))
    targets = [t for t in targets if not os.path.basename(t).startswith("~$")]
    if not targets:
        print("没有目标文档")
        return 2

    for path in targets:
        try:
            n = apply(path, dry_run=args.dry_run)
            print(f"  ✓ {os.path.basename(path):<40} 规范表格 {n:>3} 张")
        except Exception as e:  # noqa: BLE001
            print(f"  ✗ {os.path.basename(path)}: {type(e).__name__}: {e}")
    print(f"\n处理 {len(targets)} 个文档{'（dry-run）' if args.dry_run else ''}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
