# -*- coding: utf-8 -*-
"""归一化实验：把 pdf2docx 产出的超宽 docx 整体等比缩小到 Word 合法范围。

缩放对象：页面尺寸/边距、全部图片（wp:extent/a:ext）、浮动图形位置（wp:posOffset/a:off）、
表格宽高（tblW/gridCol/tcW/trHeight）、缩进与段距（ind/spacing before/after）、全部字号。
缩放后用 Word 导出 PDF 并渲染，验证是否消除"导出失败"与空白溢出页。
"""
from pathlib import Path

import pymupdf
import pythoncom
import win32com.client
from docx import Document
from docx.oxml.ns import qn
from docx.shared import Emu, Pt

OUT_DIR = Path(r"D:\研一\aikaifa\文件格式转换助手\tests\samples\output")
SRC_DOCX = OUT_DIR / "第五章机器人运动学基础v3.docx"
FIXED_DOCX = OUT_DIR / "第五章机器人运动学基础v3_normalized.docx"
WORD_PDF = OUT_DIR / "check_normalized_word.pdf"

EMU_PER_INCH = 914400
TARGET_WIDTH_IN = 13.333  # 标准 16:9 幻灯片宽度


def scale_all(doc, factor: float) -> None:
    # 1) 页面尺寸与边距
    for sec in doc.sections:
        sec.page_width = Emu(int(sec.page_width * factor))
        sec.page_height = Emu(int(sec.page_height * factor))
        for attr in ("left_margin", "right_margin", "top_margin", "bottom_margin",
                     "header_distance", "footer_distance", "gutter"):
            v = getattr(sec, attr)
            if v:
                setattr(sec, attr, Emu(int(v * factor)))

    # 2) XML 层面的几何量（EMU：图形；dxa/twips：表格与段落）
    EMU_TAGS = {"wp:extent": ("cx", "cy"), "wp:posOffset": ("x", "y"),
                "a:off": ("x", "y"), "a:ext": ("cx", "cy")}
    DXA_TAGS = {"w:tblW": ("w:w",), "w:gridCol": ("w:w",), "w:tcW": ("w:w",),
                "w:trHeight": ("w:val",), "w:ind": ("w:left", "w:right",
                "w:firstLine", "w:hanging", "w:start", "w:end"),
                "w:spacing": ("w:before", "w:after")}
    body = doc.element.body
    for tag, attrs in EMU_TAGS.items():
        for el in body.iter(qn(tag)):
            for a in attrs:
                if el.get(a) is not None:
                    try:
                        el.set(a, str(int(int(el.get(a)) * factor)))
                    except ValueError:
                        pass
    for tag, attrs in DXA_TAGS.items():
        for el in body.iter(qn(tag)):
            for a in attrs:
                if el.get(a) is not None:
                    try:
                        el.set(a, str(int(int(el.get(a)) * factor)))
                    except ValueError:
                        pass

    # 3) 字号（正文段落 + 表格内段落，含嵌套表格）
    def scale_paragraphs(paragraphs):
        for p in paragraphs:
            for r in p.runs:
                if r.font.size:
                    r.font.size = Pt(r.font.size.pt * factor)

    scale_paragraphs(doc.paragraphs)

    def scale_table(tbl):
        for row in tbl.rows:
            for cell in row.cells:
                scale_paragraphs(cell.paragraphs)
                for nested in cell.tables:
                    scale_table(nested)

    for t in doc.tables:
        scale_table(t)


def word_export(docx: Path, pdf: Path) -> bool:
    pythoncom.CoInitialize()
    app = None
    doc = None
    ok = False
    try:
        app = win32com.client.DispatchEx("Word.Application")
        app.Visible = False
        try:
            app.DisplayAlerts = 0
        except Exception:
            pass
        doc = app.Documents.Open(str(docx), ReadOnly=True)
        try:
            print(f"[Word] 页数统计: {doc.ComputeStatistics(2)}")
        except Exception:
            pass
        try:
            doc.ExportAsFixedFormat(OutputFileName=str(pdf), ExportFormat=17)
            ok = pdf.exists() and pdf.stat().st_size > 0
            print(f"[Word] 导出{'成功' if ok else '失败'}：{pdf.stat().st_size if pdf.exists() else 0} B")
        except Exception as e:
            print(f"[Word] 导出失败: {str(e)[:150]}")
        finally:
            doc.Close(False)
    finally:
        if app is not None:
            app.Quit()
        pythoncom.CoUninitialize()
    return ok


def render(pdf: Path, prefix: str, pages: int) -> None:
    d = pymupdf.open(str(pdf))
    print(f"渲染 {pdf.name}（{d.page_count} 页）前 {min(pages, d.page_count)} 页")
    for i in range(min(pages, d.page_count)):
        pix = d[i].get_pixmap(dpi=90)
        png = OUT_DIR / f"{prefix}_p{i + 1}.png"
        pix.save(str(png))
        print(f"  → {png.name}")
    d.close()


if __name__ == "__main__":
    doc = Document(str(SRC_DOCX))
    page_w_in = doc.sections[0].page_width / EMU_PER_INCH
    factor = TARGET_WIDTH_IN / page_w_in if page_w_in > 21.9 else 1.0
    print(f"原页面宽 {page_w_in:.2f} 英寸，缩放系数 {factor:.4f}")
    scale_all(doc, factor)
    doc.save(str(FIXED_DOCX))
    print(f"已保存 → {FIXED_DOCX.name}")

    print()
    if word_export(FIXED_DOCX, WORD_PDF):
        render(WORD_PDF, "diag_norm", 6)
