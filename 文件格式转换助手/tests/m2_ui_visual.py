# -*- coding: utf-8 -*-
"""界面视觉检查：离屏渲染 + 指定 Windows 字体目录，生成真实文字效果的截图。

状态为合成数据（内容取自真实批次结果），不跑转换引擎，仅用于看界面长相。
"""
import os
import sys
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
os.environ.setdefault("QT_QPA_FONTDIR", r"C:\Windows\Fonts")

BASE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BASE / "src"))

from PySide6.QtWidgets import QApplication  # noqa: E402

from core.models import Direction, FileTask, TaskStatus  # noqa: E402
from ui.main_window import MainWindow  # noqa: E402

OUT_DIR = BASE / "tests" / "samples" / "output"


def add_synthetic(win, tid, path, status, message="", out=None):
    task = FileTask(tid, str(path), Path(path).name, _dir(path))
    task.status = status
    task.message = message
    task.output_path = out
    win._tasks[tid] = task
    win._append_row(task)
    return task


def _dir(path):
    ext = Path(path).suffix.lower()
    return Direction.DOCX_TO_PDF if ext == ".docx" else Direction.PDF_TO_DOCX


def main() -> int:
    app = QApplication(sys.argv)
    win = MainWindow()
    win.resize(680, 520)
    win.show()
    app.processEvents()
    win.grab().save(str(OUT_DIR / "diag_ui_empty.png"))

    add_synthetic(win, 0, BASE / "tests/samples/sample.docx",
                  TaskStatus.SUCCESS, "", BASE / "tests/samples/sample.pdf")
    add_synthetic(win, 1, BASE / "第五章机器人运动学基础v3.pdf",
                  TaskStatus.SUCCESS, "", BASE / "第五章机器人运动学基础v3.docx")
    add_synthetic(win, 2, BASE / "tests/samples/scanned.pdf",
                  TaskStatus.SKIPPED, "扫描版 PDF，无法转为可编辑文字")
    add_synthetic(win, 3, BASE / "tests/samples/corrupt.pdf",
                  TaskStatus.FAILED, "文件损坏或无法读取")
    win._refresh_buttons()
    win.statusBar().showMessage("全部完成 — 成功 2 · 失败 1 · 跳过 1")
    win.progress.setRange(0, 4)
    win.progress.setValue(4)
    win.progress.setVisible(True)
    app.processEvents()
    win.grab().save(str(OUT_DIR / "diag_ui_done.png"))
    print("截图已更新（含系统字体）：diag_ui_empty.png / diag_ui_done.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
