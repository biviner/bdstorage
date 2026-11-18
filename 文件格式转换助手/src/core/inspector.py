# -*- coding: utf-8 -*-
"""预检器（设计文档 §4.4）：扩展名外的深度检查——加密、扫描件、Office 可用性。

原则（附录 D7）：Office 检测用注册表查询而非 Dispatch 试启——毫秒级、不拉起进程。
预检只负责"跳过"类判定（FR-ENV-01/02/03）；文件损坏等交给转换阶段报"失败"。
"""
import winreg
import zipfile

import pymupdf

SCAN_TEXT_THRESHOLD = 20  # 全文档文字字符数低于该值且含图片 → 判定为扫描件

# OLE 复合文档魔数：加密的 docx 与老版 .doc 都是 OLE 容器，统一按不支持处理
_OLE_MAGIC = bytes.fromhex("D0CF11E0A1B11AE1")

_MSG_ENCRYPTED = "加密文件暂不支持"
_MSG_SCANNED = "扫描版 PDF，无法转为可编辑文字"
_MSG_NO_OFFICE = "需本机安装 Office/WPS"


def office_available() -> dict[str, bool]:
    """检测本机 Word / WPS 的 COM 注册情况（FR-ENV-01）。"""
    result = {}
    for name, key in (("word", r"Word.Application"), ("wps", r"Kwps.Application")):
        try:
            with winreg.OpenKey(winreg.HKEY_CLASSES_ROOT, key):
                result[name] = True
        except OSError:
            result[name] = False
    return result


def check_pdf(path: str) -> str | None:
    """PDF 预检：返回跳过原因；None 表示可转换。"""
    with pymupdf.open(path) as doc:
        if doc.needs_pass:
            return _MSG_ENCRYPTED
        total_chars = 0
        has_image = False
        for page in doc:
            total_chars += len(page.get_text().strip())
            if not has_image and page.get_images():
                has_image = True
            if total_chars >= SCAN_TEXT_THRESHOLD:
                break  # 文字量已达标，不可能是扫描件，提前结束
        if total_chars < SCAN_TEXT_THRESHOLD and has_image:
            return _MSG_SCANNED
    return None


def check_docx(path: str) -> str | None:
    """docx 预检：加密容器（OLE）跳过；普通 zip 损坏交给转换阶段报"失败"（AC-07）。"""
    try:
        with open(path, "rb") as f:
            head = f.read(8)
        if head.startswith(_OLE_MAGIC):
            return _MSG_ENCRYPTED
        with zipfile.ZipFile(path):
            pass
    except (OSError, zipfile.BadZipFile):
        return None
    return None
