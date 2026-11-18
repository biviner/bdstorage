# -*- coding: utf-8 -*-
"""程序入口（设计文档 §二）：创建 QApplication 与主窗口。"""
import sys

from PySide6.QtWidgets import QApplication

from ui.main_window import MainWindow


def main() -> int:
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
