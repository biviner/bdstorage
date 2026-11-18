# -*- coding: utf-8 -*-
"""M1 验证：无界面跑一个覆盖全部状态分支的真实批次（AC-01/04/05/07 子集）。

用例：
  1. sample.docx → PDF          （Word 引擎，SUCCESS）
  2. sample.docx 再转一次        （重名规则，输出 sample (1).pdf，AC-04）
  3. 第五章机器人运动学基础v3.pdf → docx（pdf2docx + 归一化，SUCCESS）
  4. scanned.pdf（纯图片页）      （SKIPPED：扫描版，AC-05）
  5. encrypted.pdf（加密）       （SKIPPED：加密）
  6. corrupt.pdf（截断损坏）     （FAILED：文件损坏或无法读取，AC-07）

运行：.venv\\Scripts\\python.exe tests\\m1_core_test.py
"""
import os
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BASE / "src"))

import pymupdf
from PySide6.QtCore import QCoreApplication

from core.models import Direction, FileTask, TaskStatus
from core.scheduler import ConverterWorker

SAMPLES = BASE / "tests" / "samples"
SAMPLE_DOCX = SAMPLES / "sample.docx"
ROBOTICS_PDF = BASE / "第五章机器人运动学基础v3.pdf"
SCANNED_PDF = SAMPLES / "scanned.pdf"
ENCRYPTED_PDF = SAMPLES / "encrypted.pdf"
CORRUPT_PDF = SAMPLES / "corrupt.pdf"

checks: list[tuple[bool, str]] = []


def check(cond: bool, name: str) -> None:
    checks.append((bool(cond), name))
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}")


def prepare_files() -> None:
    SAMPLES.mkdir(parents=True, exist_ok=True)
    # 清理历史输出，保证重名规则从零开始
    for pattern_dir, names in ((SAMPLES, ("sample.pdf",)), (BASE, None)):
        for f in Path(pattern_dir).glob("sample*.pdf"):
            f.unlink()
    for f in BASE.glob("第五章机器人运动学基础v3*.docx"):
        f.unlink()

    # 1) 扫描件：整页只有图片、无文字层
    pix = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, 400, 300))
    try:
        pix.set_rect(pix.irect, (185, 185, 185))
        doc = pymupdf.open()
        page = doc.new_page()
        page.insert_image(pymupdf.Rect(40, 40, 560, 420), pixmap=pix)
        doc.save(str(SCANNED_PDF))
        doc.close()
    except Exception:
        # Pixmap API 兼容性兜底：用现成渲染图当图片素材
        src_png = SAMPLES / "output" / "diag_wps_p1.png"
        doc = pymupdf.open()
        page = doc.new_page()
        page.insert_image(pymupdf.Rect(40, 40, 560, 420), filename=str(src_png))
        doc.save(str(SCANNED_PDF))
        doc.close()

    # 2) 加密 PDF
    doc = pymupdf.open()
    doc.new_page().insert_text((72, 72), "encrypted content 12345")
    doc.save(str(ENCRYPTED_PDF), encryption=pymupdf.PDF_ENCRYPT_AES_256,
             owner_pw="owner", user_pw="user")
    doc.close()

    # 3) 损坏 PDF：截断真实文件
    CORRUPT_PDF.write_bytes(ROBOTICS_PDF.read_bytes()[:3000])


def make_task(tid: int, path: Path) -> FileTask:
    ext = path.suffix.lower()
    direction = Direction.DOCX_TO_PDF if ext == ".docx" else Direction.PDF_TO_DOCX
    return FileTask(tid, str(path), path.name, direction)


def main() -> int:
    prepare_files()

    tasks = [
        make_task(0, SAMPLE_DOCX),
        make_task(1, SAMPLE_DOCX),  # 同一文件第二次 → 重名规则
        make_task(2, ROBOTICS_PDF),
        make_task(3, SCANNED_PDF),
        make_task(4, ENCRYPTED_PDF),
        make_task(5, CORRUPT_PDF),
    ]

    app = QCoreApplication.instance() or QCoreApplication(sys.argv)
    results: dict[int, tuple[str, str, str]] = {}
    summary: dict = {}

    worker = ConverterWorker(tasks)

    def on_task(tid, status, msg, out):
        results[tid] = (status, msg, out)

    def on_done(s, f, k):
        summary.update(success=s, fail=f, skip=k)
        app.quit()

    worker.task_updated.connect(on_task)
    worker.batch_finished.connect(on_done)
    worker.start()
    app.exec()
    worker.wait()

    print("---- 批次结果 ----")
    for tid, (status, msg, out) in sorted(results.items()):
        print(f"  任务{tid}: {status} | {msg} | {out}")

    # ---- 断言 ----
    print("---- 断言 ----")
    check(results[0][0] == TaskStatus.SUCCESS.value, "用例1 docx→PDF 成功")
    out1 = Path(results[0][2]) if results[0][2] else None
    check(bool(out1 and out1.exists() and out1.stat().st_size > 0), "用例1 输出 PDF 存在且非空")

    check(results[1][0] == TaskStatus.SUCCESS.value, "用例2 同文件第二次转换成功")
    out2 = Path(results[1][2]) if results[1][2] else None
    check(bool(out2 and out2.name == "sample (1).pdf" and out2.exists()),
          "用例2 重名规则生效（sample (1).pdf），AC-04")

    check(results[2][0] == TaskStatus.SUCCESS.value, "用例3 真实 53 页 PDF→docx 成功")
    out3 = Path(results[2][2]) if results[2][2] else None
    ok_width = False
    if out3 and out3.exists():
        import docx as docxlib
        d = docxlib.Document(str(out3))
        width_in = d.sections[0].page_width / 914400
        ok_width = 13.0 < width_in < 13.7
        print(f"    输出页宽 {width_in:.2f} 英寸（归一化目标 13.33）")
    check(ok_width, "用例3 归一化后页宽回到 Word 合法范围（D10）")

    check(results[3][0] == TaskStatus.SKIPPED.value and "扫描版" in results[3][1],
          "用例4 扫描件跳过并提示，AC-05")
    check(results[4][0] == TaskStatus.SKIPPED.value and "加密" in results[4][1],
          "用例5 加密文件跳过并提示")
    check(results[5][0] == TaskStatus.FAILED.value and "损坏或无法读取" in results[5][1],
          "用例6 损坏文件标失败且不拖累其他任务，AC-07")

    s, f, k = summary.get("success", 0), summary.get("fail", 0), summary.get("skip", 0)
    check((s, f, k) == (3, 1, 2), f"批次汇总正确（成功{s} 失败{f} 跳过{k}）")

    passed = all(ok for ok, _ in checks)
    print("---- 结论 ----")
    print("M1 验证:", "全部通过" if passed else "存在失败")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
