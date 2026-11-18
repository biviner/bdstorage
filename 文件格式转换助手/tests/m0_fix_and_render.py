# -*- coding: utf-8 -*-
"""修复实验 + 视觉还原检查：
1. 渲染 WPS 导出的 PDF 前 4 页（看 docx 实际长相）
2. 把 docx 中超宽图片按比例压回页面宽度，另存 *_fixed.docx
3. 用 Word 导出修复后的 docx，验证是否成功并渲染首页对比
"""
from pathlib import Path

import pymupdf
import pythoncom
import win32com.client

OUT_DIR = Path(r"D:\研一\aikaifa\文件格式转换助手\tests\samples\output")
DOCX = OUT_DIR / "第五章机器人运动学基础v3.docx"
DOCX_FIXED = OUT_DIR / "第五章机器人运动学基础v3_fixed.docx"
WPS_PDF = OUT_DIR / "check_WPS_export.pdf"
WORD_PDF = OUT_DIR / "check_fixed_word_export.pdf"
EMU_PER_INCH = 914400


def render(pdf: Path, prefix: str, pages: int) -> None:
    d = pymupdf.open(str(pdf))
    print(f"渲染 {pdf.name}（{d.page_count} 页）前 {min(pages, d.page_count)} 页")
    for i in range(min(pages, d.page_count)):
        pix = d[i].get_pixmap(dpi=90)
        png = OUT_DIR / f"{prefix}_p{i + 1}.png"
        pix.save(str(png))
        print(f"  → {png.name}")
    d.close()


def fix_oversize_images() -> None:
    import docx as docxlib

    d = docxlib.Document(str(DOCX))
    page_w = d.sections[0].page_width / EMU_PER_INCH
    max_w = page_w * 0.98  # 页面宽度的 98%
    fixed = 0
    for s in d.inline_shapes:
        w, h = int(s.width), int(s.height)
        if w > max_w and w > 0:
            scale = max_w / w
            s.width = int(w * scale)
            s.height = int(h * scale)
            fixed += 1
    d.save(str(DOCX_FIXED))
    print(f"页面宽 {page_w:.2f} 英寸；压回超宽图片 {fixed} 张 → {DOCX_FIXED.name}")


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
            n_pages = doc.ComputeStatistics(2)
            print(f"[Word] 页数统计: {n_pages}")
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


if __name__ == "__main__":
    render(WPS_PDF, "diag_wps", 4)
    print()
    fix_oversize_images()
    print()
    if word_export(DOCX_FIXED, WORD_PDF):
        render(WORD_PDF, "diag_fixed", 4)
