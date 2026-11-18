# -*- coding: utf-8 -*-
"""逐页定位实验：第 N 页单独 pdf2docx 转换 + 归一化 + Word 导出，
找出哪些页的内容导致 Word"无法准备用于导出的文档"。
"""
import sys
import time
from pathlib import Path

import pythoncom
import win32com.client
from docx import Document

from m0_scale_docx import scale_all  # 复用归一化逻辑

sys.path.insert(0, str(Path(__file__).parent))

SRC = Path(r"D:\研一\aikaifa\文件格式转换助手\第五章机器人运动学基础v3.pdf")
OUT_DIR = Path(r"D:\研一\aikaifa\文件格式转换助手\tests\samples\output")
TMP_DOCX = OUT_DIR / "_page_test.docx"
TMP_PDF = OUT_DIR / "_page_test.pdf"
EMU_PER_INCH = 914400
TARGET_WIDTH_IN = 13.333


def build_one_page(page_no: int, normalize: bool) -> None:
    from pdf2docx import Converter

    if TMP_DOCX.exists():
        TMP_DOCX.unlink()
    cv = Converter(str(SRC))
    cv.convert(str(TMP_DOCX), start=page_no, end=page_no + 1)
    cv.close()
    if normalize:
        doc = Document(str(TMP_DOCX))
        w = doc.sections[0].page_width / EMU_PER_INCH
        factor = TARGET_WIDTH_IN / w if w > 21.9 else 1.0
        scale_all(doc, factor)
        doc.save(str(TMP_DOCX))


def main() -> None:
    pythoncom.CoInitialize()
    app = win32com.client.DispatchEx("Word.Application")
    app.Visible = False
    try:
        app.DisplayAlerts = 0
    except Exception:
        pass

    failed = []
    t0 = time.time()
    try:
        for p in range(53):
            try:
                build_one_page(p, normalize=True)
            except Exception as e:
                failed.append((p + 1, f"pdf2docx转换失败: {type(e).__name__}"))
                continue
            doc = None
            ok = False
            try:
                doc = app.Documents.Open(str(TMP_DOCX), ReadOnly=True)
                doc.ExportAsFixedFormat(OutputFileName=str(TMP_PDF), ExportFormat=17)
                ok = TMP_PDF.exists() and TMP_PDF.stat().st_size > 0
            except Exception:
                ok = False
            finally:
                if doc is not None:
                    try:
                        doc.Close(False)
                    except Exception:
                        pass
            if not ok:
                failed.append((p + 1, "Word导出失败"))
            if TMP_PDF.exists():
                TMP_PDF.unlink()
    finally:
        app.Quit()
        pythoncom.CoUninitialize()
        if TMP_DOCX.exists():
            TMP_DOCX.unlink()

    print(f"逐页测试完成，耗时 {time.time() - t0:.0f}s")
    if failed:
        print(f"问题页（{len(failed)} 页）：")
        for p, why in failed:
            print(f"  第 {p} 页：{why}")
    else:
        print("53 页全部通过 Word 导出")


if __name__ == "__main__":
    main()
