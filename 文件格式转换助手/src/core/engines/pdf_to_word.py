# -*- coding: utf-8 -*-
"""PDF→Word 引擎（设计文档 §4.3、附录 D10）：pdf2docx 反向重建 + 页面尺寸归一化。

归一化后处理：pdf2docx 把 Word 页面设成与 PDF 页面同尺寸，宽页（如 26.66 英寸的
幻灯片）会超过 Word 的 22 英寸上限，导致 Word 无法正常打开/导出。这里把页面、边距、
图片、表格、缩进、字号整体等比缩回合法范围（缩放系数 = 目标宽度 ÷ 实际页宽）。
"""
import os

from docx import Document
from docx.oxml.ns import qn
from docx.shared import Emu, Pt

WORD_MAX_PAGE_IN = 21.9   # Word 页面尺寸上限 22 英寸，留安全余量
TARGET_WIDTH_IN = 13.333  # 归一化目标：标准 16:9 幻灯片宽度
EMU_PER_INCH = 914400

# 图形几何：EMU 单位，直接缩放属性值
_EMU_ATTR_TAGS = {
    "wp:extent": ("cx", "cy"),  # 内嵌与浮动图形尺寸
    "a:off": ("x", "y"),        # 浮动图形位置
    "a:ext": ("cx", "cy"),      # 浮动图形尺寸
}
# 表格/段落几何：dxa（twips）单位；type="pct" 表示百分比，不按长度缩放
_DXA_ATTR_TAGS = {
    "w:tblW": ("w:w",),
    "w:gridCol": ("w:w",),
    "w:tcW": ("w:w",),
    "w:trHeight": ("w:val",),
    "w:ind": ("w:left", "w:right", "w:start", "w:end", "w:firstLine", "w:hanging"),
    "w:spacing": ("w:before", "w:after"),
}


class _ScaleVisitor:
    """把等比缩放应用到 docx 的页面、图形、表格与字号上。"""

    def __init__(self, factor: float):
        self.factor = factor

    def _scale_attr(self, el, attr: str) -> None:
        raw = el.get(attr)
        if raw is None:
            return
        try:
            el.set(attr, str(int(int(raw) * self.factor)))
        except ValueError:
            pass

    def apply(self, doc: Document) -> None:
        # 1) 页面尺寸与边距
        for sec in doc.sections:
            for attr in ("page_width", "page_height", "left_margin", "right_margin",
                         "top_margin", "bottom_margin", "header_distance",
                         "footer_distance", "gutter"):
                value = getattr(sec, attr)
                if value:
                    setattr(sec, attr, Emu(int(value * self.factor)))

        # 2) XML 层面的图形与表格几何
        body = doc.element.body
        for tag, attrs in _EMU_ATTR_TAGS.items():
            for el in body.iter(qn(tag)):
                for a in attrs:
                    self._scale_attr(el, a)
        for el in body.iter(qn("wp:posOffset")):  # 浮动图形偏移是文本内容而非属性
            if el.text and el.text.lstrip("-").isdigit():
                el.text = str(int(int(el.text) * self.factor))
        for tag, attrs in _DXA_ATTR_TAGS.items():
            for el in body.iter(qn(tag)):
                if el.get("w:type") not in (None, "dxa"):
                    continue
                if tag == "w:trHeight" and el.get("w:hRule") == "auto":
                    continue
                for a in attrs:
                    self._scale_attr(el, a)

        # 3) 字号（正文段落 + 表格内段落，含嵌套表格）
        self._scale_runs(doc.paragraphs)
        for table in doc.tables:
            self._scale_table(table)

    def _scale_runs(self, paragraphs) -> None:
        for p in paragraphs:
            for run in p.runs:
                if run.font.size:
                    run.font.size = Pt(run.font.size.pt * self.factor)

    def _scale_table(self, table) -> None:
        for row in table.rows:
            for cell in row.cells:
                self._scale_runs(cell.paragraphs)
                for nested in cell.tables:
                    self._scale_table(nested)


def _normalize_page_size(path: str) -> None:
    """页宽超过 Word 上限时整体等比缩小；失败不影响主结果。"""
    doc = Document(path)
    width_in = doc.sections[0].page_width / EMU_PER_INCH
    if width_in <= WORD_MAX_PAGE_IN:
        return
    factor = TARGET_WIDTH_IN / width_in
    _ScaleVisitor(factor).apply(doc)
    doc.save(path)


def convert_pdf_to_docx(src: str, dst: str) -> None:
    """PDF → docx；输出异常（空文件）抛 RuntimeError，其余异常原样上抛。"""
    from pdf2docx import Converter  # 延迟导入，加快界面启动

    cv = Converter(src)
    cv.convert(dst)
    cv.close()

    if not (os.path.exists(dst) and os.path.getsize(dst) > 0):
        raise RuntimeError("PDF 转换出错：输出为空")
    try:
        _normalize_page_size(dst)
    except Exception:
        pass  # 归一化属于优化步骤，不阻断主流程
