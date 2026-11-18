# -*- coding: utf-8 -*-
"""M2+M3 验证：离屏运行真实界面，覆盖交互与整合，并截图供视觉检查。

离屏模式（QT_QPA_PLATFORM=offscreen）不弹窗口、不干扰桌面，但控件逻辑、
信号槽、批量转换与真实环境完全一致。截图用 win.grab() 生成。
"""
import os
import sys
import time
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

BASE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BASE / "src"))

from PySide6.QtWidgets import QApplication  # noqa: E402

from core.models import TaskStatus  # noqa: E402
from ui.main_window import MainWindow  # noqa: E402

SAMPLES = BASE / "tests" / "samples"
OUT_DIR = SAMPLES / "output"

checks: list[tuple[bool, str]] = []


def check(cond: bool, name: str) -> None:
    checks.append((bool(cond), name))
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}")


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    app = QApplication(sys.argv)
    win = MainWindow()
    win.resize(680, 520)
    win.show()
    app.processEvents()

    # 空列表视觉：拖拽区可见、表格隐藏、按钮禁用（I9）
    win.grab().save(str(OUT_DIR / "diag_ui_empty.png"))
    check(win.drop_area.isVisible(), "空列表：拖拽提示区可见")
    check(not win.table.isVisible(), "空列表：文件列表隐藏")
    check(not win.btn_start.isEnabled(), "空列表：开始转换按钮禁用，I9")

    # 导入（I2/I3）：含 txt 忽略、重复去重
    paths = [
        SAMPLES / "sample.docx",
        BASE / "第五章机器人运动学基础v3.pdf",
        SAMPLES / "scanned.pdf",
        SAMPLES / "corrupt.pdf",
        SAMPLES / "not_supported.txt",
    ]
    ignored, added = win.add_paths([str(p) for p in paths], prompt_ignored=False)
    check(ignored == 1, "拖入 txt 被忽略计数，FR-GUI-01")
    check(added == 4 and win.table.rowCount() == 4, "4 个支持文件进入列表")
    ignored2, added2 = win.add_paths([str(SAMPLES / "sample.docx")], prompt_ignored=False)
    check(ignored2 == 0 and added2 == 4, "重复拖入去重，不重复加入，I3")
    check(not win.drop_area.isVisible() and win.table.isVisible(), "列表非空：拖拽区隐藏")
    check(win.btn_start.isEnabled(), "列表非空：开始转换可用")

    win.grab().save(str(OUT_DIR / "diag_ui_rows.png"))

    # 批量转换（FR-CORE-04）：真实引擎跑完整批次
    win._start_batch()
    check(not win.btn_start.isEnabled() and not win.btn_clear.isEnabled(),
          "转换中：两个按钮禁用，I5/I6")
    deadline = time.time() + 300
    while win._worker is not None and win._worker.isRunning() and time.time() < deadline:
        app.processEvents()
        time.sleep(0.05)
    for _ in range(50):  # 冲刷仍在队列中的信号
        app.processEvents()

    statuses = {tid: t.status for tid, t in win._tasks.items()}
    check(statuses[0] is TaskStatus.SUCCESS, "整合：docx→PDF 成功")
    check(statuses[1] is TaskStatus.SUCCESS, "整合：真实 PDF→docx 成功")
    check(statuses[2] is TaskStatus.SKIPPED, "整合：扫描件显示跳过")
    check(statuses[3] is TaskStatus.FAILED, "整合：损坏文件显示失败")
    check(win.progress.value() == win.progress.maximum(), "进度条到达 100%")
    sb = win.statusBar().currentMessage()
    check("成功 2" in sb and "失败 1" in sb and "跳过 1" in sb, f"状态栏汇总正确：{sb}")

    # 成功行的"打开位置"按钮应可用（FR-UI-01）
    btn0 = win.table.cellWidget(0, 3)
    check(btn0.isEnabled(), "成功项：打开位置按钮可用")
    btn2 = win.table.cellWidget(2, 3)
    check(not btn2.isEnabled(), "跳过项：打开位置按钮禁用")

    win.grab().save(str(OUT_DIR / "diag_ui_done.png"))
    print(f"    截图已保存：diag_ui_empty / diag_ui_rows / diag_ui_done")

    # 清空列表（I6）
    win._clear_list()
    check(win.table.rowCount() == 0 and win.drop_area.isVisible(), "清空列表恢复正常，I6")

    win.close()
    passed = all(ok for ok, _ in checks)
    print("---- 结论 ----")
    print("M2+M3 验证:", "全部通过" if passed else "存在失败")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
