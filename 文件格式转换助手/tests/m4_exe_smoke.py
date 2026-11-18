# -*- coding: utf-8 -*-
"""M4 冒烟验证：启动打包后的 exe（离屏模式，不弹窗口），确认 12 秒后仍存活即通过。"""
import os
import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
EXE = BASE / "dist" / "文件格式转换助手" / "文件格式转换助手.exe"


def dir_size(path: Path) -> str:
    total = sum(f.stat().st_size for f in path.rglob("*") if f.is_file())
    return f"{total / 1024 / 1024:.1f} MB"


def main() -> int:
    if not EXE.exists():
        print(f"未找到 exe：{EXE}")
        return 1
    print(f"exe 路径：{EXE}")
    print(f"交付目录大小：{dir_size(EXE.parent)}")

    env = dict(os.environ, QT_QPA_PLATFORM="offscreen")
    proc = subprocess.Popen([str(EXE)], env=env,
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(12)
    alive = proc.poll() is None
    if alive:
        proc.terminate()
        try:
            proc.wait(5)
        except subprocess.TimeoutExpired:
            proc.kill()
    print(f"12 秒后进程状态：{'存活（启动正常）' if alive else f'已退出，返回码 {proc.returncode}'}")
    print("结论：", "PASS" if alive else "FAIL")
    return 0 if alive else 1


if __name__ == "__main__":
    sys.exit(main())
