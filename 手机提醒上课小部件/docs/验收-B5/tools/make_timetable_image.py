# B5-1 验收辅助：生成测试用课表截图（推入模拟器相册，供 PRD 第 1 条「从相册选图」）
# 用法：python make_timetable_image.py [输出路径]
import sys

from PIL import Image, ImageDraw, ImageFont

COLS = ["节次", "周一", "周二", "周三", "周四", "周五"]
ROWS = [
    ("1-2节 08:00", "高等数学\nA101", "", "大学英语\nB202", "", ""),
    ("3-4节 10:00", "", "", "", "", ""),
    ("5节 12:30", "", "", "", "", ""),
    ("6-7节 14:00", "", "", "", "数据结构\nC303", ""),
    ("8-9节 16:00", "", "", "体育\n操场", "", ""),
]
W, H, ROW_H = 720, 640, 106


def load_font(size):
    for name in ("msyh.ttc", "simhei.ttf", "simsun.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "timetable.png"
    img = Image.new("RGB", (W, H), "#FFFFFF")
    d = ImageDraw.Draw(img)
    head = load_font(30)
    cell = load_font(24)
    col_w = W // len(COLS)
    for c, label in enumerate(COLS):
        x = c * col_w
        d.rectangle([x, 0, x + col_w, 60], fill="#4A6FA5")
        d.text((x + 12, 14), label, fill="#FFFFFF", font=head)
        d.rectangle([x, 0, x + col_w, 60], outline="#333333")
    for r, row in enumerate(ROWS):
        y = 60 + r * ROW_H
        d.rectangle([0, y, W, y + ROW_H], outline="#999999")
        for c, text in enumerate(row):
            x = c * col_w
            d.rectangle([x, y, x + col_w, y + ROW_H], outline="#999999")
            if text:
                fill = "#2F6F3F" if c else "#333333"
                for i, line in enumerate(text.split("\n")):
                    d.text((x + 8, y + 14 + i * 32), line, fill=fill, font=cell)
    d.text((12, H - 34), "2026-2027学年第1学期", fill="#555555", font=cell)
    img.save(out)
    print("saved", out)


if __name__ == "__main__":
    main()
