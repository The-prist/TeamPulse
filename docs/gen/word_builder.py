"""
Word 文档构建助手。

之所以自己封一层而不是每处直接调 python-docx：
  1. 统一字体（正文宋体 + 代码 Consolas）、行距、段前段后
  2. 统一标题层级与目录锚点
  3. 表格统一「首行加粗 + 斑马纹 + 边框」
这样几十页文档的排版不会因为某处漏设而花掉。
"""
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

# ---------- 配色 ----------
COLOR_PRIMARY = RGBColor(0x1D, 0x4E, 0xD8)   # 主蓝
COLOR_TEXT = RGBColor(0x1F, 0x29, 0x37)      # 正文深灰
COLOR_MUTED = RGBColor(0x64, 0x74, 0x8B)     # 次要灰
COLOR_CODE_BG = "F1F5F9"                     # 代码块底色
COLOR_TABLE_HEAD = "E8EEF9"                  # 表头底色
COLOR_TABLE_ALT = "F7F9FC"                   # 斑马纹


def _set_cn_font(run, name="宋体"):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)


def _shade(cell, hex_color):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    tc_pr.append(shd)


def _set_cell_margins(cell, top=60, bottom=60, left=90, right=90):
    tc_pr = cell._tc.get_or_add_tcPr()
    mar = OxmlElement("w:tcMar")
    for tag, value in (("top", top), ("bottom", bottom), ("start", left), ("end", right)):
        node = OxmlElement(f"w:{tag}")
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")
        mar.append(node)
    tc_pr.append(mar)


class WordDoc:
    """面向本项目的 Word 构建器。"""

    def __init__(self, title, subtitle=""):
        self.doc = Document()
        self._setup_page()
        self._setup_styles()
        if title:
            self.cover(title, subtitle)

    # ---------------- 基础设置 ----------------
    def _setup_page(self):
        section = self.doc.sections[0]
        section.page_width = Cm(21.0)
        section.page_height = Cm(29.7)
        section.top_margin = Cm(2.2)
        section.bottom_margin = Cm(2.2)
        section.left_margin = Cm(2.4)
        section.right_margin = Cm(2.4)

    def _setup_styles(self):
        normal = self.doc.styles["Normal"]
        normal.font.size = Pt(10.5)
        normal.font.color.rgb = COLOR_TEXT
        _set_cn_font(normal, "宋体")
        normal.paragraph_format.line_spacing = 1.5
        normal.paragraph_format.space_after = Pt(6)

        # 标题样式：中文标题用黑体更醒目
        for name, size, color in (
            ("Heading 1", 18, COLOR_PRIMARY),
            ("Heading 2", 14, COLOR_PRIMARY),
            ("Heading 3", 12, COLOR_TEXT),
        ):
            style = self.doc.styles[name]
            style.font.size = Pt(size)
            style.font.bold = True
            style.font.color.rgb = color
            _set_cn_font(style, "黑体")
            style.paragraph_format.space_before = Pt(14 if name == "Heading 1" else 10)
            style.paragraph_format.space_after = Pt(8)

    # ---------------- 封面 ----------------
    def cover(self, title, subtitle=""):
        for _ in range(5):
            self.doc.add_paragraph()
        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p.add_run(title)
        run.font.size = Pt(30)
        run.font.bold = True
        run.font.color.rgb = COLOR_PRIMARY
        _set_cn_font(run, "黑体")

        if subtitle:
            p2 = self.doc.add_paragraph()
            p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
            r2 = p2.add_run(subtitle)
            r2.font.size = Pt(13)
            r2.font.color.rgb = COLOR_MUTED
            _set_cn_font(r2, "宋体")

        for _ in range(3):
            self.doc.add_paragraph()

        info = self.doc.add_paragraph()
        info.alignment = WD_ALIGN_PARAGRAPH.CENTER
        ri = info.add_run("技术手册 · 项目梳理 · 面试准备")
        ri.font.size = Pt(11)
        ri.font.color.rgb = COLOR_MUTED
        _set_cn_font(ri, "宋体")

        self.page_break()

    # ---------------- 结构元素 ----------------
    def page_break(self):
        self.doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)

    def h1(self, text):
        return self.doc.add_heading(text, level=1)

    def h2(self, text):
        return self.doc.add_heading(text, level=2)

    def h3(self, text):
        return self.doc.add_heading(text, level=3)

    def p(self, text, bold=False, color=None, size=None, indent=None):
        para = self.doc.add_paragraph()
        if indent:
            para.paragraph_format.left_indent = Cm(indent)
        # 支持 **加粗** 行内标记
        parts = text.split("**")
        for idx, part in enumerate(parts):
            if not part:
                continue
            run = para.add_run(part)
            run.bold = bold or (idx % 2 == 1)
            if color:
                run.font.color.rgb = color
            if size:
                run.font.size = Pt(size)
            _set_cn_font(run, "宋体")
        return para

    def muted(self, text):
        return self.p(text, color=COLOR_MUTED, size=9.5)

    def bullets(self, items, indent=0.5):
        for item in items:
            para = self.doc.add_paragraph(style="List Bullet")
            para.paragraph_format.left_indent = Cm(indent)
            para.paragraph_format.space_after = Pt(2)
            parts = item.split("**")
            for idx, part in enumerate(parts):
                if not part:
                    continue
                run = para.add_run(part)
                run.bold = idx % 2 == 1
                run.font.size = Pt(10.5)
                _set_cn_font(run, "宋体")

    def numbered(self, items):
        for item in items:
            para = self.doc.add_paragraph(style="List Number")
            para.paragraph_format.space_after = Pt(2)
            run = para.add_run(item)
            run.font.size = Pt(10.5)
            _set_cn_font(run, "宋体")

    def table(self, headers, rows, widths=None, font_size=9.5):
        table = self.doc.add_table(rows=1, cols=len(headers))
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        table.style = "Table Grid"

        hdr = table.rows[0].cells
        for i, text in enumerate(headers):
            hdr[i].text = ""
            para = hdr[i].paragraphs[0]
            run = para.add_run(str(text))
            run.bold = True
            run.font.size = Pt(font_size)
            run.font.color.rgb = RGBColor(0x0F, 0x17, 0x2A)
            _set_cn_font(run, "黑体")
            _shade(hdr[i], COLOR_TABLE_HEAD)
            _set_cell_margins(hdr[i])

        for r_idx, row in enumerate(rows):
            cells = table.add_row().cells
            for c_idx, value in enumerate(row):
                cells[c_idx].text = ""
                para = cells[c_idx].paragraphs[0]
                run = para.add_run(str(value) if value is not None else "")
                run.font.size = Pt(font_size)
                _set_cn_font(run, "宋体")
                _set_cell_margins(cells[c_idx])
                if r_idx % 2 == 1:
                    _shade(cells[c_idx], COLOR_TABLE_ALT)

        if widths:
            for row in table.rows:
                for idx, width in enumerate(widths):
                    if idx < len(row.cells):
                        row.cells[idx].width = Cm(width)
        self.doc.add_paragraph().paragraph_format.space_after = Pt(2)
        return table

    def code(self, text, caption=None):
        """代码/命令块：等宽字体 + 浅底色，逐行写入同一个段落以保持紧凑。"""
        if caption:
            cap = self.doc.add_paragraph()
            run = cap.add_run(caption)
            run.font.size = Pt(9)
            run.font.color.rgb = COLOR_MUTED
            _set_cn_font(run, "宋体")

        table = self.doc.add_table(rows=1, cols=1)
        table.style = "Table Grid"
        cell = table.rows[0].cells[0]
        _shade(cell, COLOR_CODE_BG)
        _set_cell_margins(cell, top=90, bottom=90, left=140, right=140)
        cell.text = ""
        for line_no, line in enumerate(text.split("\n")):
            para = cell.paragraphs[0] if line_no == 0 else cell.add_paragraph()
            para.paragraph_format.space_after = Pt(0)
            para.paragraph_format.line_spacing = 1.15
            run = para.add_run(line if line else " ")
            run.font.name = "Consolas"
            run.font.size = Pt(8.8)
            run._element.rPr.rFonts.set(qn("w:eastAsia"), "Consolas")
        self.doc.add_paragraph().paragraph_format.space_after = Pt(2)

    def callout(self, title, text, fill="FFF7E6"):
        """提示框：用单列表格模拟，带底色。"""
        table = self.doc.add_table(rows=1, cols=1)
        table.style = "Table Grid"
        cell = table.rows[0].cells[0]
        _shade(cell, fill)
        _set_cell_margins(cell, top=100, bottom=100, left=140, right=140)
        cell.text = ""
        para = cell.paragraphs[0]
        run = para.add_run(title + "  ")
        run.bold = True
        run.font.size = Pt(10)
        _set_cn_font(run, "黑体")
        run2 = para.add_run(text)
        run2.font.size = Pt(10)
        _set_cn_font(run2, "宋体")
        self.doc.add_paragraph().paragraph_format.space_after = Pt(2)

    def toc_hint(self):
        self.callout(
            "阅读提示",
            "本文档按「先跑通、再吃透、后背诵」三段组织。第 1~3 章建立全局认知；第 4~8 章逐个技术点拆解；"
            "第 9~11 章用于面试问答与复盘。建议配合代码一起看，每个技术点都标注了对应源文件。",
            fill="E8F1FF",
        )

    def save(self, path):
        self.doc.save(path)
        return path
