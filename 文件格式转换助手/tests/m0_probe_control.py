# -*- coding: utf-8 -*-
"""对照实验：不做归一化的原始 pdf2docx 单页输出，Word 导出是否也失败。"""
import sys
from pathlib import Path

import pythoncom
import win32com.client

sys.path.insert(0, str(Path(__file__).parent))
from m0_page_probe import build_one_page, OUT_DIR, TMP_DOCX, TMP_PDF  # noqa: E402

PAGES = [1, 3, 4, 13]  # 含已知的"归一化后失败"页(1,4,13)和"归一化后通过"页(3)


def main() -> None:
    pythoncom.CoInitialize()
    app = win32com.client.DispatchEx("Word.Application")
    app.Visible = False
    try:
        app.DisplayAlerts = 0
    except Exception:
        pass
    results = []
    try:
        for p in PAGES:
            build_one_page(p - 1, normalize=False)
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
            results.append((p, ok))
            if TMP_PDF.exists():
                TMP_PDF.unlink()
    finally:
        app.Quit()
        pythoncom.CoUninitialize()
        if TMP_DOCX.exists():
            TMP_DOCX.unlink()

    print("对照结果（不归一化，原始 pdf2docx 输出）：")
    for p, ok in results:
        print(f"  第 {p} 页：{'通过' if ok else '失败'}")


if __name__ == "__main__":
    main()
