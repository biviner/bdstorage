# -*- coding: utf-8 -*-
"""真实文档测试：用 pdf2docx 转换《第五章机器人运动学基础v3.pdf》并评估输出。

用法：
  .venv\\Scripts\\python.exe tests\\m0_real_pdf_test.py        # 全本转换
  .venv\\Scripts\\python.exe tests\\m0_real_pdf_test.py --end 6 # 只转前 6 页（冒烟测试）

流程：检查源文件 → pdf2docx 转换 → 读输出 docx 统计 → 用 Word 把 docx 再转成
PDF → 渲染前几页 PNG，供肉眼对比还原效果。
"""
import sys
import time
from pathlib import Path

import pymupdf

SRC = Path(r"D:\研一\aikaifa\文件格式转换助手\第五章机器人运动学基础v3.pdf")
OUT_DIR = Path(r"D:\研一\aikaifa\文件格式转换助手\tests\samples\output")
OUT_DOCX = OUT_DIR / "第五章机器人运动学基础v3.docx"
CHECK_PDF = OUT_DIR / "第五章机器人运动学基础v3_check.pdf"

END_PAGE = None  # None = 全部页
if "--end" in sys.argv:
    END_PAGE = int(sys.argv[sys.argv.index("--end") + 1])


def inspect_source() -> None:
    """检查源 PDF：页数、文字量分布（顺带验证扫描件检测思路）。"""
    doc = pymupdf.open(str(SRC))
    n_pages = doc.page_count
    total_text = 0
    pages_sparse = []
    for i, page in enumerate(doc):
        t = page.get_text().strip()
        total_text += len(t)
        if len(t) < 30:
            pages_sparse.append(i + 1)
    doc.close()
    print(f"源文件：{n_pages} 页，总文字 {total_text} 字符")
    print(f"文字极少(<30字符)的页：{pages_sparse if pages_sparse else '无'}")


def convert() -> None:
    from pdf2docx import Converter

    if OUT_DOCX.exists():
        OUT_DOCX.unlink()
    cv = Converter(str(SRC))
    if END_PAGE is None:
        cv.convert(str(OUT_DOCX))
    else:
        cv.convert(str(OUT_DOCX), start=0, end=END_PAGE)
    cv.close()


def inspect_output() -> bool:
    import docx as docxlib

    d = docxlib.Document(str(OUT_DOCX))
    texts = [p.text.strip() for p in d.paragraphs if p.text.strip()]
    print(f"输出 docx：段落数 {len(d.paragraphs)}，非空文字段落 {len(texts)}，"
          f"表格数 {len(d.tables)}，内嵌图片数 {len(d.inline_shapes)}")
    print("文字样例（前 5 段）：")
    for t in texts[:5]:
        print("  -", t[:40])
    return len(texts) > 0


def visual_check() -> None:
    """docx → PDF（Word COM）→ 渲染前 4 页 PNG，肉眼对比还原效果。"""
    import pythoncom
    import win32com.client

    pythoncom.CoInitialize()
    app = None
    try:
        app = win32com.client.DispatchEx("Word.Application")
        app.Visible = False
        try:
            app.DisplayAlerts = 0
        except Exception:
            pass
        doc = app.Documents.Open(str(OUT_DOCX), ReadOnly=True)
        try:
            try:
                doc.ExportAsFixedFormat(OutputFileName=str(CHECK_PDF), ExportFormat=17)
            except Exception:
                doc.SaveAs2(str(CHECK_PDF), FileFormat=17)
        finally:
            doc.Close(False)
    finally:
        if app is not None:
            app.Quit()
        pythoncom.CoUninitialize()

    d = pymupdf.open(str(CHECK_PDF))
    n_src = f"（源 {'全部' if END_PAGE is None else END_PAGE} 页）"
    print(f"视觉检查 PDF：{d.page_count} 页 {n_src}")
    for i in range(min(4, d.page_count)):
        pix = d[i].get_pixmap(dpi=90)
        png = OUT_DIR / f"diag_real_p{i + 1}.png"
        pix.save(str(png))
        print(f"  已渲染 → {png.name}")
    d.close()


def main() -> int:
    if not SRC.exists():
        print(f"源文件不存在：{SRC}")
        return 1
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    inspect_source()

    print(f"\n开始 pdf2docx 转换（{'全部页' if END_PAGE is None else f'前 {END_PAGE} 页'}）…")
    t0 = time.time()
    convert()
    print(f"转换完成，耗时 {time.time() - t0:.1f}s，输出 {OUT_DOCX.stat().st_size} B")

    ok = inspect_output()

    print("\n视觉检查（docx → PDF → PNG）…")
    try:
        visual_check()
    except Exception as e:
        print(f"视觉检查失败: {type(e).__name__}: {e}")

    print("\n结论：", "docx 可打开且含文字" if ok else "输出为空/失败")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
