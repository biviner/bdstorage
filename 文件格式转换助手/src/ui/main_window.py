# -*- coding: utf-8 -*-
"""主窗口（设计文档 §三）：拖拽导入、任务列表、批量转换控制、结果展示。"""
import os
import subprocess

from PySide6.QtCore import Qt
from PySide6.QtGui import QColor
from PySide6.QtWidgets import (
    QAbstractItemView,
    QFileDialog,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QMainWindow,
    QMessageBox,
    QProgressBar,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from core import inspector
from core.models import Direction, FileTask, TaskStatus
from core.scheduler import ConverterWorker

SUPPORTED_EXTS = {".docx", ".pdf"}

# 状态列展示文字与颜色（设计文档 §3.3）
STATUS_DISPLAY = {
    TaskStatus.WAITING: ("等待中", "#757575"),
    TaskStatus.CONVERTING: ("⟳ 转换中…", "#1565C0"),
    TaskStatus.SUCCESS: ("✔ 成功", "#2E7D32"),
    TaskStatus.FAILED: ("✖ 失败", "#C62828"),
    TaskStatus.SKIPPED: ("⚠ 跳过", "#EF6C00"),
}


def _direction_for(path: str) -> Direction:
    ext = os.path.splitext(path)[1].lower()
    return Direction.DOCX_TO_PDF if ext == ".docx" else Direction.PDF_TO_DOCX


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("文件格式转换助手")
        self.setMinimumSize(640, 480)
        self.setAcceptDrops(True)

        self._tasks: dict[int, FileTask] = {}   # task_id → 任务
        self._rows: dict[int, int] = {}         # task_id → 表格行号
        self._next_id = 0
        self._worker: ConverterWorker | None = None

        self._build_ui()
        self._refresh_buttons()
        self._show_engine_status()

    # ---- UI 构建 ----

    def _build_ui(self) -> None:
        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        layout.setSpacing(8)

        self.drop_area = QLabel("⬇  将 .docx / .pdf 文件拖到这里\n或点击下方“选择文件”")
        self.drop_area.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.drop_area.setMinimumHeight(110)
        self.drop_area.setObjectName("dropArea")
        self.drop_area.setStyleSheet(
            "#dropArea { border: 2px dashed #B0B0B0; border-radius: 8px;"
            " color: #616161; font-size: 14px; background: #FAFAFA; }"
        )
        layout.addWidget(self.drop_area)

        self.table = QTableWidget(0, 4)
        self.table.setHorizontalHeaderLabels(["文件名", "方向", "状态", "操作"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.verticalHeader().setVisible(False)
        layout.addWidget(self.table)

        bottom = QHBoxLayout()
        self.btn_pick = QPushButton("选择文件")
        self.btn_start = QPushButton("开始转换")
        self.btn_clear = QPushButton("清空列表")
        self.progress = QProgressBar()
        self.progress.setVisible(False)
        bottom.addWidget(self.btn_pick)
        bottom.addWidget(self.btn_start)
        bottom.addWidget(self.btn_clear)
        bottom.addStretch(1)
        bottom.addWidget(self.progress)
        layout.addLayout(bottom)

        self.btn_pick.clicked.connect(self._pick_files)
        self.btn_start.clicked.connect(self._start_batch)
        self.btn_clear.clicked.connect(self._clear_list)

    def _show_engine_status(self) -> None:
        avail = inspector.office_available()
        found = [name for name, ok in (("Word", avail.get("word")), ("WPS", avail.get("wps"))) if ok]
        if found:
            self.statusBar().showMessage(f"就绪　|　转换引擎：{'、'.join(found)}")
        else:
            self.statusBar().showMessage("就绪　|　⚠ 未检测到 Word/WPS，Word→PDF 功能不可用")

    # ---- 导入：拖拽（I1~I3）与点击选择（I4） ----

    def dragEnterEvent(self, event) -> None:  # noqa: N802 (Qt 命名)
        if event.mimeData().hasUrls():
            for url in event.mimeData().urls():
                if url.toLocalFile().lower().endswith(tuple(SUPPORTED_EXTS)):
                    event.acceptProposedAction()
                    return
        event.ignore()

    def dropEvent(self, event) -> None:  # noqa: N802 (Qt 命名)
        paths = [u.toLocalFile() for u in event.mimeData().urls() if u.isLocalFile()]
        self.add_paths(paths)

    def add_paths(self, paths: list[str], prompt_ignored: bool = True) -> tuple[int, int]:
        """导入文件：过滤扩展名、去重、加入列表。返回 (忽略数, 新增数)。"""
        ignored = 0
        duplicated = 0
        for path in paths:
            ext = os.path.splitext(path)[1].lower()
            if ext not in SUPPORTED_EXTS:
                ignored += 1
                continue
            key = os.path.normcase(os.path.abspath(path))
            if any(os.path.normcase(t.src_path) == key for t in self._tasks.values()):
                duplicated += 1
                continue
            task = FileTask(self._next_id, os.path.abspath(path),
                            os.path.basename(path), _direction_for(path))
            self._tasks[task.task_id] = task
            self._append_row(task)
            self._next_id += 1
        if ignored and prompt_ignored:
            QMessageBox.information(self, "提示",
                                    f"已忽略 {ignored} 个不支持的文件（仅支持 .docx / .pdf）")
        if duplicated:
            self.statusBar().showMessage(f"{duplicated} 个文件已在列表中", 3000)
        self._refresh_buttons()
        return ignored, len(self._tasks)

    def _append_row(self, task: FileTask) -> None:
        row = self.table.rowCount()
        self.table.insertRow(row)
        self._rows[task.task_id] = row

        name_item = QTableWidgetItem(task.filename)
        name_item.setData(Qt.ItemDataRole.UserRole, task.task_id)
        name_item.setToolTip(task.src_path)
        self.table.setItem(row, 0, name_item)
        self.table.setItem(row, 1, QTableWidgetItem(task.direction.label))
        self.table.setItem(row, 2, QTableWidgetItem(""))

        btn = QPushButton("打开位置")
        btn.setEnabled(False)
        btn.clicked.connect(lambda _checked=False, tid=task.task_id: self._open_output(tid))
        self.table.setCellWidget(row, 3, btn)

        self._update_row(row, task)

    def _update_row(self, row: int, task: FileTask) -> None:
        text, color = STATUS_DISPLAY[task.status]
        if task.status is TaskStatus.FAILED and task.message:
            text = f"✖ 失败：{task.message}"
        elif task.status is TaskStatus.SKIPPED and task.message:
            text = f"⚠ 跳过：{task.message}"
        status_item = self.table.item(row, 2)
        status_item.setText(text)
        status_item.setForeground(QColor(color))

        btn = self.table.cellWidget(row, 3)
        if isinstance(btn, QPushButton):
            btn.setEnabled(task.status is TaskStatus.SUCCESS and bool(task.output_path))

    def _open_output(self, task_id: int) -> None:
        task = self._tasks.get(task_id)
        if task and task.output_path and os.path.exists(task.output_path):
            # 资源管理器打开目录并选中文件（I7）
            subprocess.Popen(f'explorer /select,"{task.output_path}"')

    # ---- 批量转换（I5/I9） ----

    def _pick_files(self) -> None:
        paths, _filter = QFileDialog.getOpenFileNames(
            self, "选择要转换的文件", "",
            "Word 文档 (*.docx);;PDF 文件 (*.pdf);;全部支持格式 (*.docx *.pdf)",
        )
        if paths:
            self.add_paths(paths)

    def _start_batch(self) -> None:
        if self._worker is not None or not self._tasks:
            return
        self._worker = ConverterWorker(list(self._tasks.values()), parent=self)
        self._worker.task_updated.connect(self._on_task_updated)
        self._worker.batch_progress.connect(self._on_progress)
        self._worker.batch_finished.connect(self._on_batch_finished)
        self._worker.finished.connect(self._on_worker_finished)

        self.progress.setRange(0, len(self._tasks))
        self.progress.setValue(0)
        self.progress.setVisible(True)
        self.btn_start.setEnabled(False)
        self.btn_clear.setEnabled(False)
        self.statusBar().showMessage(f"转换中 (0/{len(self._tasks)})")
        self._worker.start()

    def _on_task_updated(self, task_id: int, status_value: str, message: str, out_path: str) -> None:
        task = self._tasks.get(task_id)
        if task is None:
            return
        task.status = TaskStatus(status_value)
        task.message = message
        task.output_path = out_path or None
        row = self._rows.get(task_id)
        if row is not None:
            self._update_row(row, task)

    def _on_progress(self, done: int, total: int) -> None:
        self.progress.setValue(done)
        self.statusBar().showMessage(f"转换中 ({done}/{total})")

    def _on_batch_finished(self, success: int, failed: int, skipped: int) -> None:
        self.statusBar().showMessage(
            f"全部完成 — 成功 {success} · 失败 {failed} · 跳过 {skipped}")

    def _on_worker_finished(self) -> None:
        self._worker = None
        self.btn_start.setEnabled(bool(self._tasks))
        self.btn_clear.setEnabled(bool(self._tasks))

    # ---- 列表管理（I6） ----

    def _clear_list(self) -> None:
        if self._worker is not None:
            return
        self.table.setRowCount(0)
        self._tasks.clear()
        self._rows.clear()
        self.progress.setVisible(False)
        self.progress.setValue(0)
        self.statusBar().showMessage("就绪")
        self._refresh_buttons()

    def _refresh_buttons(self) -> None:
        has_tasks = bool(self._tasks)
        running = self._worker is not None
        self.btn_start.setEnabled(has_tasks and not running)
        self.btn_clear.setEnabled(has_tasks and not running)
        self.drop_area.setVisible(not has_tasks)
        self.table.setVisible(has_tasks)

    # ---- 退出（I8） ----

    def closeEvent(self, event) -> None:  # noqa: N802 (Qt 命名)
        if self._worker is None or not self._worker.isRunning():
            event.accept()
            return
        ret = QMessageBox.question(
            self, "确认退出", "正在转换，退出将中断剩余任务。确定退出？",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            QMessageBox.StandardButton.No,
        )
        if ret != QMessageBox.StandardButton.Yes:
            event.ignore()
            return
        self._worker.request_cancel()
        self._worker.wait(15000)  # 等当前文件转换收尾（单个文件通常数秒）
        if self._worker.isRunning():
            self._worker.terminate()  # 兜底，避免程序无法退出
            self._worker.wait(1000)
        event.accept()
