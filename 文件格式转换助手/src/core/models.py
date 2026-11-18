# -*- coding: utf-8 -*-
"""数据模型：转换任务的状态与方向（设计文档 §七）。无持久化，仅存内存。"""
from dataclasses import dataclass
from enum import Enum


class Direction(Enum):
    """转换方向；value 为目标文件扩展名。"""

    DOCX_TO_PDF = ".pdf"
    PDF_TO_DOCX = ".docx"

    @property
    def label(self) -> str:
        return "docx → PDF" if self is Direction.DOCX_TO_PDF else "PDF → docx"


class TaskStatus(Enum):
    """单个文件项状态机（设计文档 §3.3）。"""

    WAITING = "等待中"
    CONVERTING = "转换中…"
    SUCCESS = "成功"
    FAILED = "失败"
    SKIPPED = "跳过"


@dataclass
class FileTask:
    """一条转换任务；task_id 是 UI 与后台线程的关联键。"""

    task_id: int
    src_path: str
    filename: str
    direction: Direction
    status: TaskStatus = TaskStatus.WAITING
    message: str = ""  # 失败/跳过原因
    output_path: str | None = None
