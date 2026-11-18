# -*- coding: utf-8 -*-
"""
M0 引擎验证脚本（设计文档 §九 M0）

目的：在写任何正式代码之前，用最小脚本证明两条转换链路在本机可跑通：
  链路 A：docx --(COM: Word 优先 / WPS 后备)--> PDF
  链路 B：PDF --(pdf2docx)--> docx

运行方式（项目根目录）：
  .venv\\Scripts\\python.exe tests\\m0_verify_engines.py

退出码：0 = 全部通过；1 = 存在失败。
"""
import os
import sys
import time
from importlib.metadata import version as pkg_version
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent  # 项目根目录
SAMPLES = BASE / "tests" / "samples"
OUT = SAMPLES / "output"

SAMPLE_DOCX = SAMPLES / "sample.docx"
SAMPLE_PDF = SAMPLES / "sample.pdf"
OUT_PDF = OUT / "sample.pdf"
OUT_DOCX = OUT / "sample.docx"

WORD_PROGID = "Word.Application"
WPS_PROGID = "Kwps.Application"

# Windows 常见中文字体，按优先级尝试
CJK_FONTS = [
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\simsun.ttc",
]


def make_sample_docx(path: Path) -> None:
    """用 python-docx 生成样例 docx（链路 A 的输入）。"""
    import docx

    doc = docx.Document()
    doc.add_heading("文件格式转换助手 M0 验证", level=1)
    doc.add_paragraph("这是 Word 转 PDF 的样例文档。")
    doc.add_paragraph("第二段：验证中文与 English 混排。")
    doc.add_paragraph("第三段：数字 12345 与标点测试。")
    table = doc.add_table(rows=2, cols=2)
    table.cell(0, 0).text = "项目"
    table.cell(0, 1).text = "数值"
    table.cell(1, 0).text = "进度"
    table.cell(1, 1).text = "80%"
    doc.save(str(path))


def make_sample_pdf(path: Path) -> None:
    """用 PyMuPDF 生成带中文文字层的样例 PDF（链路 B 的输入）。"""
    import pymupdf

    doc = pymupdf.open()
    page = doc.new_page()

    cjk_ok = False
    fontfile = next((f for f in CJK_FONTS if os.path.exists(f)), None)
    if fontfile:
        try:
            page.insert_font(fontname="cjk", fontfile=fontfile)
            cjk_ok = True
        except Exception:
            cjk_ok = False

    if cjk_ok:
        lines = [
            "PDF 转 Word 测试文档（M0）",
            "第二行：中文与 English 混排。",
            "第三行：数字 12345 与标点，。",
        ]
        fontname, fontsize = "cjk", 14
    else:  # 中文字体不可用时回退英文样例
        lines = ["PDF to Word test (M0)", "Line 2: mixed with 12345."]
        fontname, fontsize = "helv", 14

    y = 72
    for line in lines:
        page.insert_text((72, y), line, fontname=fontname, fontsize=fontsize)
        y += 24
    doc.save(str(path))
    doc.close()


def docx_to_pdf(src: Path, dst: Path) -> str:
    """链路 A：COM 调用 Word（优先）或 WPS（后备），返回实际使用的引擎名。"""
    import pythoncom
    import win32com.client

    pythoncom.CoInitialize()
    app = None
    try:
        try:
            app = win32com.client.DispatchEx(WORD_PROGID)
            engine = "Word"
        except Exception:
            app = win32com.client.DispatchEx(WPS_PROGID)
            engine = "WPS"
        app.Visible = False
        try:
            app.DisplayAlerts = 0
        except Exception:
            pass
        doc = app.Documents.Open(str(src), ReadOnly=True)
        try:
            try:
                # 17 = wdExportFormatPDF
                doc.ExportAsFixedFormat(OutputFileName=str(dst), ExportFormat=17)
            except Exception:
                doc.SaveAs2(str(dst), FileFormat=17)  # 个别 WPS 版本的兼容路径
        finally:
            doc.Close(False)
        return engine
    finally:
        if app is not None:
            app.Quit()
        pythoncom.CoUninitialize()


def pdf_to_docx(src: Path, dst: Path) -> None:
    """链路 B：pdf2docx。"""
    from pdf2docx import Converter

    cv = Converter(str(src))
    cv.convert(str(dst))
    cv.close()


def check_pdf_text(path: Path) -> str:
    import pymupdf

    with pymupdf.open(str(path)) as doc:
        return "".join(page.get_text() for page in doc)


def check_docx_text(path: Path) -> str:
    import docx

    d = docx.Document(str(path))
    parts = [p.text for p in d.paragraphs]
    for t in d.tables:
        for row in t.rows:
            for cell in row.cells:
                parts.append(cell.text)
    return "\n".join(parts)


def main() -> int:
    print("=" * 60)
    print("M0 引擎验证")
    print("=" * 60)
    for name in ("PySide6", "pdf2docx", "PyMuPDF", "pywin32", "python-docx"):
        try:
            print(f"依赖版本: {name} == {pkg_version(name)}")
        except Exception:
            print(f"依赖版本: {name} == 未知")

    SAMPLES.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    for f in (OUT_PDF, OUT_DOCX):
        if f.exists():
            f.unlink()

    make_sample_docx(SAMPLE_DOCX)
    make_sample_pdf(SAMPLE_PDF)
    print(f"样例已生成: {SAMPLE_DOCX.name}, {SAMPLE_PDF.name}")

    results = []

    # ---- 链路 A ----
    print("-" * 60)
    print("链路 A: docx → PDF (COM)")
    try:
        t0 = time.time()
        engine = docx_to_pdf(SAMPLE_DOCX, OUT_PDF)
        dt = time.time() - t0
        text = check_pdf_text(OUT_PDF)
        ok = ("12345" in text) and len(text.strip()) > 10
        print(f"  引擎: {engine} | 耗时 {dt:.1f}s | 输出 {OUT_PDF.stat().st_size} B")
        print(f"  抽验文字: {'通过' if ok else '未通过'}")
        results.append(("A docx→PDF", ok))
    except Exception as e:
        print(f"  失败: {type(e).__name__}: {e}")
        results.append(("A docx→PDF", False))

    # ---- 链路 B ----
    print("-" * 60)
    print("链路 B: PDF → docx (pdf2docx)")
    try:
        t0 = time.time()
        pdf_to_docx(SAMPLE_PDF, OUT_DOCX)
        dt = time.time() - t0
        text = check_docx_text(OUT_DOCX)
        ok = ("12345" in text) and len(text.strip()) > 10
        print(f"  耗时 {dt:.1f}s | 输出 {OUT_DOCX.stat().st_size} B")
        print(f"  抽验文字: {'通过' if ok else '未通过'}")
        results.append(("B PDF→docx", ok))
    except Exception as e:
        print(f"  失败: {type(e).__name__}: {e}")
        results.append(("B PDF→docx", False))

    print("=" * 60)
    for name, ok in results:
        print(f"  [{'PASS' if ok else 'FAIL'}] {name}")
    all_ok = all(ok for _, ok in results)
    print("M0 结论:", "通过，可进入 M1" if all_ok else "未通过，先解决再继续")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
