# -*- coding: utf-8 -*-
"""Word→PDF 引擎（设计文档 §4.2 + 附录 D10）。

核心策略：
- Word（Word.Application）优先，WPS（Kwps.Application）后备
- DispatchEx 创建独立实例，不复用用户已打开的窗口
- Word "能打开但导出失败"（无法准备用于导出的文档）→ 必须换 WPS 整篇重试（D10 实测）
- try/finally 保证 doc.Close 与 app.Quit 必然执行，不留残留进程
- 导出后校验输出非空，防止"成功但为空"（用户遇到的空 PDF 问题）
"""
import os

import pythoncom
import win32com.client

WORD_PROGID = "Word.Application"
WPS_PROGID = "Kwps.Application"
WD_EXPORT_PDF = 17  # wdExportFormatPDF / wdFormatPDF

ENGINES = ((WORD_PROGID, "Word"), (WPS_PROGID, "WPS"))

MSG_CORRUPT = "文件损坏或无法读取"
MSG_ENGINE = "Word/WPS 转换出错"


class ConversionError(Exception):
    """带用户可读信息的转换异常，scheduler 直接把 user_message 展示到状态列。"""

    def __init__(self, message: str):
        super().__init__(message)
        self.user_message = message


class _OpenFailed(Exception):
    """内部信号：引擎实例创建成功但 Documents.Open 失败（多为文件损坏）。"""


def _convert_with(progid: str, src: str, dst: str) -> None:
    """用单个引擎完成转换；失败抛异常，由上层决定是否换引擎重试。"""
    pythoncom.CoInitialize()
    app = None
    try:
        app = win32com.client.DispatchEx(progid)
        app.Visible = False
        try:
            app.DisplayAlerts = 0
        except Exception:
            pass
        try:
            doc = app.Documents.Open(src, ReadOnly=True)
        except Exception as e:
            raise _OpenFailed(str(e)) from e
        try:
            try:
                doc.ExportAsFixedFormat(OutputFileName=dst, ExportFormat=WD_EXPORT_PDF)
            except Exception:
                doc.SaveAs2(dst, FileFormat=WD_EXPORT_PDF)  # 个别 WPS 版本的兼容路径
        finally:
            doc.Close(False)
    finally:
        if app is not None:
            try:
                app.Quit()
            except Exception:
                pass
        pythoncom.CoUninitialize()

    if not (os.path.exists(dst) and os.path.getsize(dst) > 0):
        raise RuntimeError("导出结果为空")


def convert_docx_to_pdf(src: str, dst: str) -> str:
    """docx → PDF，返回实际使用的引擎名（"Word"/"WPS"）。

    全部引擎失败时抛 ConversionError，信息按"打开失败"与"导出失败"区分，
    对应设计文档 §六 的错误提示表。
    """
    open_failed_seen = False
    engine_error_seen = False
    for progid, _name in ENGINES:
        try:
            _convert_with(progid, src, dst)
            return "Word" if progid == WORD_PROGID else "WPS"
        except _OpenFailed:
            open_failed_seen = True  # 该引擎打不开文档，换下一个引擎
        except Exception:
            engine_error_seen = True  # 未安装 / 导出失败 / 输出为空，换下一个引擎
    if open_failed_seen and not engine_error_seen:
        raise ConversionError(MSG_CORRUPT)
    raise ConversionError(MSG_ENGINE)
