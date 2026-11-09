# AGENTS.md — 智能课表 Android App（手机提醒上课小部件）

> 本文件是所有子员工（claude --agent）与协调者的共同工作约定。开工前必读。改流程先改这里。

## 1. 项目与文档体系

- 项目：智能课表（Android 原生 Kotlin + Compose + Glance 小组件），仓库根 = 本目录。
- 文档链（权威性从高到低，冲突以此顺序为准）：
  1. `需求文档.txt`（PRD V1.0）
  2. `docs/基础架构落地设计.md`（架构蓝图，分批计划 B0-B5）
  3. `docs/概要设计.md`（模块边界/数据流/契约草案）
  4. `docs/详细设计.md`（**实现直接依据**：DDL/算法签名/边界用例/prompt/页面规格）
  5. `docs/看板任务分解.md`（机器卡 ↔ 人读文档，与看板同源生成）
- 每张卡 `## 依据` 必须具体到上述文档 §章节。

## 2. 技术栈与框架（锁死，不自由发挥）

| 项 | 值 |
| --- | --- |
| 语言 | Kotlin 2.x，JVM target 17 |
| UI | Jetpack Compose + Material 3，单 Activity + Navigation Compose |
| 小组件 | Glance（2×2「下一节课」+ 4×2「今日课程」），大尺寸留扩展 |
| 数据 | Room（schema v1，见详细设计 §3）+ DataStore Preferences |
| 网络 | Retrofit/OkHttp，OpenAI 兼容 chat.completions，仅 `core/network` 可出网 |
| 调度 | AlarmManager（setExactAndAllowWhileIdle）+ WorkManager 兜底 |
| DI | 手工依赖装配（Application 内），**不引 Hilt/Koin** |
| minSdk 26 / targetSdk 35 | 不改 |

## 3. 数据流（一段话背下来）

Room 是唯一权威数据源；课程/学期/作息的任何写操作必经 `TimetableRepository`，写后必须依序触发 `ReminderScheduler.rescheduleAll()` 与 `WidgetUpdater.refreshAll()`（经 `ChangeNotifier` 接口，依赖倒置，`core/data` 不 import scheduler/widget 模块）。小组件与提醒是数据库的只读投影，**一律现查现算，禁止缓存课程数据**。「今天的课/下一节/第几周/倒计时」全部实时由 `core/domain` 纯函数计算，不落库。网络只存在于 `core/network` 的识别网关内；API key 不进日志（OkHttp 日志拦截器 redact Authorization）。

包依赖方向（违反即编译失败）：`domain ← data/network/scheduler/widget/ui ← app`；`core/domain` 禁止 import 任何 `android.*`/`androidx.*`（独立 Gradle 模块强制）。

## 4. 开发规范

- **全局约束 8 条**：详细设计 §9 原样有效（domain 纯净、写路径必经 Repository、重排幂等、过期提醒不补发、识别失败不写半成品、密钥不进日志、周一=1/分钟数 0..1439、Room schema JSON 入 git）。
- 每张卡只动卡面 `## 文件域` 列出的路径；并行卡严禁改其他车道的文件。
- 提交纪律：**精确路径 `git add <本卡文件>`，禁止 `git add -A`**；提交信息含卡号（如 `D1-1: xxx`）；在当前分支（master）直接提交，**不要自建分支**。
- 提交前跑 `codegraph sync`（本仓库首次先 `codegraph init`）。
- 测试文件用非敏感字面量（`demo-key`/`tok-test`），不要写出形似真实密钥的常量。
- 卡面 `## 验收（Hermes 亲自跑）` 的命令必须真实执行并把输出摘要写进完成报告；禁止只报「构建成功」。
- 完成报告写进 PR 描述/卡评论，包含：改动文件清单、执行的命令与结果摘要、与设计文档的差异点（有则必须注明）。

## 5. 环境约束（本机实况，子员工必读）

- 本机初始**无 JDK、无 Android SDK**。统一安装到 **`D:\研一\aikaifa\手机提醒上课小部件\tools\`** 下（用户指定），不装系统盘、不写全局环境变量：
  - `tools/jdk-17/`（Temurin JDK 17）
  - `tools/android-sdk/`（cmdline-tools + platform-tools + platforms;android-35 + build-tools）
  - 构建/测试命令统一走项目根 `tools/env.sh`（source 后 JAVA_HOME/ANDROID_HOME/PATH 就绪），详见该文件。
- shell 是 git-bash：给原生程序（gradle.bat/java.exe）传路径用 `D:/...` 正斜杠形式，别用 `/d/...`。
- 依赖下载走国内镜像（详细设计 §1.1；gradle distribution 与 maven 镜像在 `settings.gradle.kts`/`gradle.properties` 配好，别删）。
- 首次 Gradle 构建会下载 distribution（约 150MB）+ 依赖，慢属正常，别超时重试导致重复下载。
- 无真机；批次 4 起用模拟器（`tools/android-sdk/emulator`，AVD 数据也在 tools 目录下）。
- 磁盘预算：tools 目录合计约 3-4GB，已获用户同意。

## 6. 派单与验收约定

- 执行者：`cc-tars`（写卡）；架构/走查：`cc-architect`、`cc-kotlin-reviewer`（只读）。
- 并发规则：同批并行卡 = 不同文件域 + 卡面写明「并行纪律」；同一时间同一车道只跑一张。
- 验收铁律：Hermes 亲自跑 `gradlew testDebugUnitTest` / `assembleDebug`（经 tools/env.sh），不采信子员工自报；产出核对 `git log`/`git show --name-only` 逐文件查文件域越界。
- 批次闸门：每批末尾一张独立验收卡（只读角色），不过不派下一批。
