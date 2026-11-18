# -*- coding: utf-8 -*-
"""转换调度器（设计文档 §五）：后台线程逐文件执行预检与转换。

铁律：worker 线程绝不直接触碰任何 UI 控件，只发 Qt 信号；UI 只在信号槽里更新。
一批任务对应一个 worker 实例，结束后自然退出，下一批新建。
"""
from PySide6.QtCore import QThread, Signal

from core import inspector, output
from core.engines import pdf_to_word, word_to_pdf
from core.models import Direction, FileTask, TaskStatus


class ConverterWorker(QThread):
    # task_id, 状态枚举值, 提示信息, 输出路径（"" 表示无）
    task_updated = Signal(int, str, str, str)
    batch_progress = Signal(int, int)   # 已处理数, 总数
    batch_finished = Signal(int, int, int)  # 成功, 失败, 跳过

    def __init__(self, tasks: list[FileTask], parent=None):
        super().__init__(parent)
        self._tasks = tasks
        self._cancelled = False  # 关闭窗口时置位，在下一个文件开始前退出（I8）

    def request_cancel(self) -> None:
        self._cancelled = True

    def run(self) -> None:
        import pythoncom  # COM 线程初始化必须在 worker 线程内完成

        pythoncom.CoInitialize()
        try:
            office = inspector.office_available()
            total = len(self._tasks)
            success = fail = skip = 0
            done = 0
            for task in self._tasks:
                if self._cancelled:
                    break
                try:
                    reason = self._precheck(task, office)
                    if reason:
                        skip += 1
                        self._emit(task, TaskStatus.SKIPPED, reason, "")
                    else:
                        self._emit(task, TaskStatus.CONVERTING, "", "")
                        message, out_path = self._convert(task)
                        if out_path:
                            success += 1
                            self._emit(task, TaskStatus.SUCCESS, "", out_path)
                        else:
                            fail += 1
                            self._emit(task, TaskStatus.FAILED, message, "")
                except Exception:
                    # 预检阶段的意外异常（如 PyMuPDF 打不开损坏 PDF）按损坏处理（AC-07）
                    fail += 1
                    self._emit(task, TaskStatus.FAILED, word_to_pdf.MSG_CORRUPT, "")
                done += 1
                self.batch_progress.emit(done, total)
            self.batch_finished.emit(success, fail, skip)
        finally:
            pythoncom.CoUninitialize()

    # ---- 内部流程 ----

    def _precheck(self, task: FileTask, office: dict[str, bool]) -> str:
        """返回跳过原因；空字符串表示可转换。"""
        if task.direction is Direction.DOCX_TO_PDF:
            if not (office.get("word") or office.get("wps")):
                return inspector._MSG_NO_OFFICE
            return inspector.check_docx(task.src_path) or ""
        return inspector.check_pdf(task.src_path) or ""

    def _convert(self, task: FileTask) -> tuple[str, str | None]:
        """执行转换；返回 (失败提示, 输出路径)。成功时提示为空。"""
        dst = output.make_output_path(task.src_path, task.direction.value)
        try:
            if task.direction is Direction.DOCX_TO_PDF:
                word_to_pdf.convert_docx_to_pdf(task.src_path, dst)
            else:
                pdf_to_word.convert_pdf_to_docx(task.src_path, dst)
            return "", dst
        except word_to_pdf.ConversionError as e:
            return e.user_message, None
        except PermissionError:
            return "输出文件被占用，请关闭后重试", None
        except Exception:
            msg = ("Word/WPS 转换出错" if task.direction is Direction.DOCX_TO_PDF
                   else "PDF 转换出错")
            return msg, None

    def _emit(self, task: FileTask, status: TaskStatus, message: str, out_path: str) -> None:
        self.task_updated.emit(task.task_id, status.value, message, out_path)
