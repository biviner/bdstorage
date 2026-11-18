# 文件格式转换助手

面向 Windows 用户的纯本地文件格式转换工具：把 `.docx` 与 `.pdf` 双向批量转换，
拖拽即用，文件不出电脑。

![界面](tests/samples/output/diag_ui_done.png)

## 功能

- **Word → PDF**：调用本机 Word（优先）或 WPS，保真度与手动"另存为 PDF"一致
- **PDF → Word**：内置 pdf2docx 引擎反向重建文档，段落/表格/图片尽力还原，转出可编辑
- **拖拽批量转换**：一次拖入多个文件，逐个显示状态（等待/转换中/成功/失败/跳过）
- **智能预检**：扫描版 PDF、加密文件自动识别并跳过并说明原因；损坏文件标记失败且不影响其他任务
- **重名保护**：输出文件重名自动追加序号（`报告 (1).pdf`），绝不覆盖已有文件
- **一键定位**：转换成功后可打开文件所在目录并选中该文件

## 环境要求

| 场景 | 要求 |
|---|---|
| 使用打包版（`dist/` 目录） | Windows 10/11 x64，无需安装 Python |
| Word → PDF 转换 | 本机安装 Microsoft Word 或 WPS |
| PDF → Word 转换 | 无额外要求（内置引擎） |

## 使用方法

1. 运行 `dist/文件格式转换助手/文件格式转换助手.exe`
2. 把 `.docx` / `.pdf` 文件拖进窗口（或点击"选择文件"）
3. 点击"开始转换"，等待逐个完成
4. 转换结果保存在**源文件所在目录**，与源文件同名、扩展名替换

## 已知限制

- **扫描版 PDF**（整页是图片）无法转为可编辑文字，程序会检测并跳过（不做 OCR）
- **加密文件**不支持转换
- 复杂幻灯片式 PDF 转出后可能出现少量空白溢出页；源 PDF 中的公式/截图若本身是图片，转出后仍是图片
- pdf2docx 生成的复杂 docx 在 **Word 自带的"导出 PDF"** 中可能失败——如需把这类 docx 再转回 PDF，请继续使用本工具（会自动回退 WPS 引擎）

## 开发

```bash
# 1) 创建虚拟环境并安装依赖（Python 3.10+）
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt -r requirements-dev.txt

# 2) 运行
.venv\Scripts\python src\main.py

# 3) 自动化验证（无界面跑全部验收用例）
.venv\Scripts\python tests\m1_core_test.py     # 核心服务层：正常/重名/扫描件/加密/损坏
.venv\Scripts\python tests\m2_ui_test.py       # 界面整合：离屏运行真实窗口并断言交互

# 4) 打包
.venv\Scripts\pyinstaller --noconfirm --clean --onedir --windowed ^
    --name 文件格式转换助手 --distpath dist --workpath build ^
    --specpath . src\main.py
```

## 项目结构

```
├── src/
│   ├── main.py              # 程序入口
│   ├── ui/main_window.py    # 主窗口：拖拽、列表、按钮、信号槽
│   └── core/
│       ├── models.py        # 任务数据模型与状态机
│       ├── inspector.py     # 预检：Office 注册表检测、扫描件/加密识别
│       ├── output.py        # 输出路径与重名避让
│       ├── scheduler.py     # 后台转换线程（只发 Qt 信号，不碰 UI）
│       └── engines/
│           ├── word_to_pdf.py  # COM 调用 Word/WPS，失败自动回退
│           └── pdf_to_word.py  # pdf2docx + 页面尺寸归一化
├── tests/                   # 引擎验证与自动化验收脚本
├── 需求文档.md / 设计文档.md  # 项目宪法与实现蓝图
├── requirements.txt         # 运行时依赖（版本经实测锁定）
└── dist/                    # 打包产物（exe 在此）
```

## 技术方案要点

两个转换方向用了**不同的引擎**，这是本项目最核心的设计决策：

- `.docx` 只存"内容 + 样式描述"，分页排版需要一个**排版引擎**计算——Word 的分页算法闭源且
  积累几十年，所以 Word→PDF 直接通过 COM 自动化**借用 Word/WPS 原生引擎**，保真度最高；
- PDF 存的是精确坐标，PDF→Word 是"反向重建"问题，pdf2docx（基于 PyMuPDF）解析元素坐标、
  字体与表格线重建 docx，纯 Python、离线、轻量；
- Word 的 PDF 导出器对部分复杂 docx 会拒绝导出（详见设计文档附录 D10 的实测记录），
  因此 docx→PDF 在 Word 失败时**自动回退 WPS 引擎**，任何环境都能出结果。

完整的决策推理、实测数据与里程碑记录见 `需求文档.md` 与 `设计文档.md`。
