# B5-1 验收辅助：本地 OpenAI 兼容 Mock 识别服务器
# 用途：PRD 八条验收无法依赖真实云端识别（无 API key），以本地 Mock 服务
# 走通「选图 → 上传 → 识别返回 → 草稿确认 → 保存」的完整应用链路。
# 用法：python mock_recognizer.py [端口]（默认 8765）；模拟器内 baseUrl 填
#       http://10.0.2.2:8765/v1/（10.0.2.2 = 宿主机回环）。
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

# 固定识别结果：覆盖普通周/单周/双周/指定周次 + 低置信度标黄 + 丢弃阈值
COURSES = [
    {"name": "高等数学", "dayOfWeek": 1, "periodStart": 1, "periodEnd": 2,
     "startTime": "08:00", "endTime": "09:40", "weeksRaw": "1-16周",
     "weeksParsed": list(range(1, 17)), "location": "教学楼A101",
     "teacher": "张老师", "confidence": 0.95},
    {"name": "大学英语", "dayOfWeek": 3, "periodStart": 3, "periodEnd": 4,
     "startTime": "10:00", "endTime": "11:40", "weeksRaw": "单周",
     "weeksParsed": [1, 3, 5, 7, 9, 11, 13, 15], "location": "外语楼B202",
     "teacher": "李老师", "confidence": 0.9},
    {"name": "数据结构", "dayOfWeek": 5, "periodStart": 6, "periodEnd": 7,
     "startTime": "14:00", "endTime": "15:40", "weeksRaw": "双周",
     "weeksParsed": [2, 4, 6, 8, 10, 12, 14, 16], "location": "机房C303",
     "teacher": "王老师", "confidence": 0.9},
    {"name": "体育", "dayOfWeek": 4, "periodStart": 8, "periodEnd": 9,
     "startTime": "16:00", "endTime": "17:40", "weeksRaw": "1-8周",
     "weeksParsed": list(range(1, 9)), "location": "操场",
     "teacher": None, "confidence": 0.45},
    {"name": None, "dayOfWeek": 6, "periodStart": None, "periodEnd": None,
     "startTime": None, "endTime": None, "weeksRaw": None,
     "weeksParsed": [], "location": None, "teacher": None,
     "confidence": 0.2},
]


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        self.rfile.read(length)
        body = {
            "id": "mock-1",
            "object": "chat.completion",
            "choices": [{
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": json.dumps({"courses": COURSES}, ensure_ascii=False),
                },
                "finish_reason": "stop",
            }],
        }
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        print("[mock]", fmt % args, flush=True)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    print(f"mock recognizer on 0.0.0.0:{port}", flush=True)
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
