# -*- coding: utf-8 -*-
"""诊断：pdf2docx 产出的 docx，Word / WPS 各自能否打开、能否导出 PDF。

定位"命令失败"发生在哪个环节：打开失败（文件结构问题）还是导出失败（内容渲染问题）。
顺带用 python-docx 扫描内嵌图片的几何尺寸，排查异常图形。
"""
from pathlib import Path

import pythoncom
import win32com.client
import pymupdf

DOCX = Path(r"D:\研一\aikaifa\文件格式转换助手\tests\samples\output\第五章机器人运动学基础v3.docx")
OUT_DIR = DOCX.parent


def scan_image_geometry() -> None:
    import docx as docxlib
    from docx.shared import Emu

    d = docxlib.Document(str(DOCX))
    print(f"---- 图片几何扫描（共 {len(d.inline_shapes)} 张内嵌图）----")
    bad = 0
    for i, s in enumerate(d.inline_shapes):
        w, h = int(s.width), int(s.height)
        # 1 英寸 = 914400 EMU；幻灯片宽约 13.3 英寸。超 20 英寸或 <=0 视为异常
        if w <= 0 or h <= 0 or w > 20 * 914400 or h > 20 * 914400:
            print(f"  异常图 #{i}: {w} x {h} EMU = {w/914400:.2f} x {h/914400:.2f} 英寸")
            bad += 1
    print(f"  异常图片数：{bad}\n")


def try_engine(progid: str, tag: str) -> None:
    pythoncom.CoInitialize()
    app = None
    doc = None
    try:
        try:
            app = win32com.client.DispatchEx(progid)
        except Exception as e:
            print(f"[{tag}] 创建实例失败: {e}")
            return
        app.Visible = False
        try:
            app.DisplayAlerts = 0
        except Exception:
            pass
        try:
            doc = app.Documents.Open(str(DOCX), ReadOnly=True)
            print(f"[{tag}] 打开成功")
            try:
                print(f"[{tag}] Word/WPS 认为的页数: {doc.ComputeStatistics(2)}")
            except Exception as e:
                print(f"[{tag}] 统计页数失败: {type(e).__name__}")

            for name, pdf in (("ExportAsFixedFormat", OUT_DIR / f"check_{tag}_export.pdf"),
                              ("SaveAs2", OUT_DIR / f"check_{tag}_saveas.pdf")):
                try:
                    if name == "ExportAsFixedFormat":
                        doc.ExportAsFixedFormat(OutputFileName=str(pdf), ExportFormat=17)
                    else:
                        doc.SaveAs2(str(pdf), FileFormat=17)
                    size = pdf.stat().st_size if pdf.exists() else 0
                    info = ""
                    if size:
                        try:
                            d2 = pymupdf.open(str(pdf))
                            t0 = d2[0].get_text().strip()
                            info = f"，{d2.page_count} 页，首页文字 {len(t0)} 字符"
                            d2.close()
                        except Exception as e:
                            info = f"，但 pymupdf 读取失败: {type(e).__name__}"
                    print(f"[{tag}] {name}: {'成功' if size else '未生成文件'}，{size} B{info}")
                except Exception as e:
                    print(f"[{tag}] {name}: 失败 {type(e).__name__}: {str(e)[:150]}")
        except Exception as e:
            print(f"[{tag}] 打开失败: {type(e).__name__}: {str(e)[:200]}")
        finally:
            if doc is not None:
                try:
                    doc.Close(False)
                except Exception:
                    pass
    finally:
        if app is not None:
            try:
                app.Quit()
            except Exception:
                pass
        pythoncom.CoUninitialize()


if __name__ == "__main__":
    scan_image_geometry()
    try_engine("Word.Application", "Word")
    print()
    try_engine("Kwps.Application", "WPS")
