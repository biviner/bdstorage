# -*- coding: utf-8 -*-
"""诊断：output/sample.pdf 是否真的为空 —— 逐页统计文字并渲染成 PNG 亲眼看。"""
import os
from pathlib import Path

OUT = Path(r"D:\研一\aikaifa\文件格式转换助手\tests\samples\output")
SAMPLES = Path(r"D:\研一\aikaifa\文件格式转换助手\tests\samples")

print("---- 文件清单（名称 / 大小 / 修改时间）----")
for d in (SAMPLES, OUT):
    for f in sorted(d.glob("*")):
        if f.is_file():
            print(f"  {f.name:20s} {f.stat().st_size:>8d} B  {f.stat().st_mtime:.0f}")

import pymupdf

for tag, pdf in (("Word转PDF输出", OUT / "sample.pdf"), ("样例输入PDF", SAMPLES / "sample.pdf")):
    doc = pymupdf.open(str(pdf))
    print(f"\n---- [{tag}] {pdf.parent.name}\\{pdf.name}: 共 {doc.page_count} 页 ----")
    for i, page in enumerate(doc):
        text = page.get_text().strip()
        print(f"  第 {i+1} 页：文字 {len(text)} 字符，内容前 60 字：{text[:60]!r}")
        pix = page.get_pixmap(dpi=110)
        png = OUT / f"diag_{tag}_p{i + 1}.png"
        pix.save(str(png))
        print(f"  已渲染 → {png.name}（{png.stat().st_size} B）")
    doc.close()
