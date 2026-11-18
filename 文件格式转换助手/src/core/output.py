# -*- coding: utf-8 -*-
"""输出路径生成与重名避让规则（设计文档 §4.5，需求 FR-CORE-05）。"""
import os


def make_output_path(src: str, new_ext: str) -> str:
    """输出到源文件同目录，扩展名替换；同名冲突自动追加序号" (n)"，不覆盖已有文件。

    例：report.docx → report.pdf；若已存在则依次尝试 report (1).pdf、(2)…
    """
    base = os.path.splitext(src)[0]
    dst = base + new_ext
    if not os.path.exists(dst):
        return dst
    i = 1
    while os.path.exists(f"{base} ({i}){new_ext}"):
        i += 1
    return f"{base} ({i}){new_ext}"
