#!/usr/bin/env bash
# 幂等建板脚本：智能课表（timetable 板）。重复跑返回同一批卡 id，不会重复建卡。
# 改批次/卡面后重跑本脚本即可；卡面源文件在 .board/（已 gitignore）。
set -u
BOARD=timetable
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BDY="$ROOT/.board"
mkdir -p "$BDY"

declare -A IDS TITLES WHO PRI DEPS

card() {
  local key="$1" title="$2" who="$3" pri="$4" deps="$5"
  TITLES[$key]="$title"; WHO[$key]="$who"; PRI[$key]="$pri"; DEPS[$key]="$deps"
  local bf="$BDY/$key.md"
  cat > "$bf"
  local -a cmd=(kanban --board "$BOARD" create "$title" --assignee "$who" --priority "$pri"
                --workspace "dir:$ROOT" --body-file "$bf" --idempotency-key "$BOARD-$key" --json)
  local d
  for d in ${deps//,/ }; do
    [ -n "${IDS[$d]:-}" ] && cmd+=(--parent "${IDS[$d]}")
  done
  local out id
  out=$(hermes "${cmd[@]}" 2>&1)
  id=$(printf '%s' "$out" | python -c 'import sys,json
try: print(json.load(sys.stdin)["id"])
except Exception: pass' 2>/dev/null)
  [ -z "$id" ] && id=$(printf '%s' "$out" | grep -o 't_[0-9a-f]\{8\}' | head -1)
  if [ -z "$id" ]; then printf '!! %s 失败: %s\n' "$key" "$out" >&2; return 1; fi
  IDS[$key]="$id"
  printf '%-8s %-11s %-14s %s\n' "$key" "$id" "$who" "$title"
}

# ════════ 批次 0：环境（总控+执行者协作，1 张卡）════════
card B0-1 "B0-1 环境:JDK17+Android SDK 装入项目 tools/ 并建 env.sh" cc-tars 98 "-" <<'BODY'
## 依据
AGENTS.md §5；详细设计 §1.1、§2
## 文件域
tools/**、.gitignore、docs/环境验收.md（新建）
## 产出
- tools/jdk-17/（Temurin JDK 17）与 tools/android-sdk/（cmdline-tools、platform-tools、platforms;android-35、build-tools;35.x）
- tools/env.sh：source 后 JAVA_HOME/ANDROID_HOME/PATH 就绪（全部指向 tools 内路径，正斜杠 D:/ 形式）
- docs/环境验收.md：记录 java -version、sdkmanager --list_installed 真实输出
## 验收（Hermes 亲自跑）
- source tools/env.sh && java -version 输出 17.x
- source tools/env.sh && sdkmanager --list_installed 含 platform-tools / android-35 / build-tools
## 禁止
- 装到系统盘或写全局环境变量；用 scoop/choco 全局包管理器；跳过 env.sh 直接改系统 PATH
## 环境约束
- shell 是 git-bash；sdkmanager 是 bat，调用用 sdkmanager.bat 或 cmd //c；下载慢属正常，别重复触发
BODY

# ════════ 批次 1：骨架 + 领域层（3 张，2 张可并行）════════
card B1-1 "B1-1 骨架:Gradle工程+Compose空壳可出APK" cc-tars 96 "B0-1" <<'BODY'
## 依据
详细设计 §1.1、§2、§8.1；概要设计 §2.2
## 文件域
settings.gradle.kts、build.gradle.kts(根/app/core*)、gradle/**、gradle.properties、app/src/**(仅壳：MainActivity/主题/导航空宿主)、core/domain/build.gradle.kts、.gitignore 补充
## 产出
- 可 assembleDebug 的空 App（Compose 空首页）；libs.versions.toml 锁定全部版本
- gradle wrapper + 国内镜像配置；gradlew.bat 可用
## 验收（Hermes 亲自跑）
- source tools/env.sh && ./gradlew assembleDebug 在 app/build/outputs/apk/debug/ 真实产出 APK
## 禁止
- 引入 Hilt/Koin/RxJava 等卡面未列依赖；改 minSdk/targetSdk；用 /d/ 路径传给 gradle.bat
## 环境约束
- 首次构建下载 distribution 约150MB，慢属正常；一切构建经 tools/env.sh
BODY

card B1-2 "B1-2 领域模型:纯Kotlin数据类型与枚举" cc-tars 95 "B1-1" <<'BODY'
## 依据
详细设计 §3.1（实体语义）、§4.2；概要设计 §4.1
## 文件域
core/domain/src/main/kotlin/com/zjh/timetable/domain/model/**
## 产出
Course/SemesterSetting/PeriodTime/WeekPattern/CourseStatus/PlannedReminder/RecognizedCourse 数据类与枚举（不可变，无任何 android 依赖）
## 验收（Hermes 亲自跑）
- ./gradlew :core:domain:compileKotlin 编译通过；grep -r "android" core/domain/src 无 import
## 禁止
- 在 model 里写 Room 注解（那是 data 层的事，domain 保持纯净）；引入可 var 字段
BODY

card B1-3 "B1-3 领域算法:周次/状态/提醒计划+全量单测" cc-tars 94 "B1-2" <<'BODY'
## 依据
详细设计 §4 全部（含边界用例表）；概要设计 §4.1
## 文件域
core/domain/src/main/kotlin/com/zjh/timetable/domain/calc/**、core/domain/src/main/kotlin/com/zjh/timetable/domain/text/WidgetText.kt、core/domain/src/test/**
## 产出
WeekCalculator/CourseStatusResolver/ReminderPlanner/PeriodMapper/ConflictDetector/WidgetText + JUnit5 单测覆盖详细设计 §4 各边界用例表（含 4.2 customWeeks 编解码 §3.2）
## 并行纪律
本卡只动 domain 模块；与 B1-4 无共享文件，可并行
## 验收（Hermes 亲自跑）
- ./gradlew :core:domain:testDebugUnitTest（或 test）全绿，测试数 ≥ 25
- 反例：WeekCalculator 对 today<firstWeekStart 返回 null；ReminderPlanner 对过期时刻不产出
## 禁止
- import android.*；用 java.util.Date/Calendar（统一 java.time）；跳过 §4 任何一个边界用例
BODY

card B1-4 "B1-4 走查:领域层契约符合性评审" cc-kotlin-reviewer 93 "B1-3" <<'BODY'
## 依据
详细设计 §4、§9；概要设计 §4.1 契约草案
## 文件域
只读评审 core/domain/**，交付物走 stdout 由主控落盘 docs/评审-B1.md
## 产出
评审报告：契约签名与详细设计 §4 一致性、边界用例覆盖缺口、纯函数纯度核查
## 验收（Hermes 亲自跑）
- logs/workers 日志含「结论：可进入批次2」或具体阻塞项清单
## 禁止
- 修改任何文件
BODY

# ════════ 批次 2：数据层 + 管理 UI（4 张，2 车道并行）════════
card B2-1 "B2-1 数据层:Room实体/DAO/Repository+ChangeNotifier" cc-tars 92 "B1-4" <<'BODY'
## 依据
详细设计 §3 全部、§8.6；概要设计 §7、§10
## 文件域
core/data/**
## 产出
Room 实体+DAO+Database(schema v1 导出 JSON)+DataStore keys+TimetableRepository（写后触发 ChangeNotifier）+ customWeeks 编解码测试 + Robolectric DAO 测试
## 并行纪律
只动 core/data；不碰 feature/ui（那是 B2-2 车道）
## 验收（Hermes 亲自跑）
- ./gradlew :core:data:test 全绿（CRUD+编码解码+钩子触发断言）
- 反例：插入 day_of_week=0 被 schema 约束拒绝
## 禁止
- 在 core/data import scheduler/widget 模块；绕过 Repository 直接暴露 DAO 给 UI
BODY

card B2-2 "B2-2 UI车道:周课表+课程编辑+设置页" cc-tars 91 "B1-4" <<'BODY'
## 依据
详细设计 §8.1-8.6；概要设计 §5.2
## 文件域
app/src/main/kotlin/**/ui/**、app/src/main/kotlin/**/nav/**、feature/（新建 ui 相关仅限 app 模块 ui 包）
## 产出
Navigation 骨架 + 周课表网格/周切换、课程编辑页（字段校验/周次选择器/冲突弹窗/删除）、设置页（学期/作息/提醒，识别服务配置留空位）、ViewModel+StateFlow 挂接假 Repository 接口（B2-1 完成后由总控换真实现，如先于 B2-1 完成则用接口+Fake 实现先行）
## 并行纪律
只动 app 模块 ui/nav 包与自己的测试；不改 core/data（B2-1 车道）
## 验收（Hermes 亲自跑）
- ./gradlew :app:assembleDebug 通过；手动走查周课表空态/编辑校验截图（模拟器）
## 禁止
- 改 core/data 任何文件；在 Composable 里直连 DAO；引入 Coil 等无关依赖
BODY

card B2-3 "B2-3 接线:Repository真实现接入UI+首页" cc-tars 90 "B2-1,B2-2" <<'BODY'
## 依据
详细设计 §8.2、§9.2；概要设计 §6.4
## 文件域
app/src/main/kotlin/**（装配/首页/ui）、core/data（仅接口缝隙修补）
## 产出
首页（下一节大卡片+今日时间线+空态+权限提示条占位）；Fake 替换为真 Repository；周课表/编辑/设置全链路可真实读写
## 验收（Hermes 亲自跑）
- 模拟器安装：建课→周课表可见→编辑保存→数据持久（重开 App 还在）
## 禁止
- 动 scheduler/widget（B4 车道）；引 DI 框架
BODY

card B2-4 "B2-4 走查:批次2评审" cc-kotlin-reviewer 89 "B2-3" <<'BODY'
## 依据
详细设计 §3、§8、§9；概要设计 §7、§10
## 文件域
只读；报告由主控落盘 docs/评审-B2.md
## 产出
数据层/接线评审报告：写后钩子完整性、文件域越界核查、Room schema 与详细设计一致性
## 禁止
- 修改任何文件
BODY

# ════════ 批次 3：识别导入（3 张串行）════════
card B3-1 "B3-3 识别网关:RecognitionGateway+校验规则+单测" cc-tars 88 "B2-4" <<'BODY'
## 依据
详细设计 §5.1-5.3、§9.6；概要设计 §6.1
## 文件域
core/network/**（含 assets/prompt.md）
## 产出
ScheduleRecognizer 接口+OpenAiVisionRecognizer（response_format json_object、temperature 0、超时60s、网络错误重试1次、Authorization redact）+ §5.2 四条校验规则实现 + MockWebServer 单测 + prompt.md 定稿
## 并行纪律
只动 core/network；确认页（B3-2）不动
## 验收（Hermes 亲自跑）
- ./gradlew :core:network:test 全绿（正常/坏JSON/低置信度/4xx/超时五路径）
- 反例：响应含 confidence=0.2 且 name 缺失 → RecognizedCourse 被丢弃并计数
## 禁止
- 任何明文密钥进代码/日志/测试；在 domain import okhttp
BODY

card B3-2 "B3-2 导入UI:选图/进度/失败/确认页" cc-tars 87 "B3-1" <<'BODY'
## 依据
详细设计 §5.4-5.5、§8.5；概要设计 §5.2、§6.1
## 文件域
app/src/main/kotlin/**/ui/import/**、app/src/main/kotlin/**/ui/confirm/**、相关 ViewModel
## 产出
图片导入页（PhotoPicker、上传声明确认、状态机五态、未配置置灰）+ 导入确认页（行内编辑/周次选择器/标黄/冲突⚠/二次确认/全丢不写库）
## 并行纪律
只动 ui 的 import/confirm 包
## 验收（Hermes 亲自跑）
- 模拟器：配置测试服务地址→选图→（Mock 网关或真实 key）→确认页标黄正确→确认入库→周课表可见
## 禁止
- 未过上传声明就发网络请求；识别失败写半成品进 Room
BODY

card B3-3 "B3-3 走查:批次3评审" cc-kotlin-reviewer 86 "B3-2" <<'BODY'
## 依据
详细设计 §5、§9.5-9.6
## 文件域
只读；报告落盘 docs/评审-B3.md
## 产出
识别链路评审：协议一致、校验规则完整、隐私合规（key redact、上传声明前置）
## 禁止
- 修改任何文件
BODY

# ════════ 批次 4：小组件 + 提醒（2 车道并行）════════
card B4-1 "B4-1 提醒车道:ReminderScheduler+权限引导" cc-tars 85 "B3-3" <<'BODY'
## 依据
详细设计 §6 全部；概要设计 §6.2
## 文件域
core/scheduler/**、app 清单合并权限、设置页提醒可靠性入口
## 产出
ReminderScheduler（幂等全量重排）+AlarmReceiver（陈旧闹钟防陈旧重查）+BootReceiver+ScheduleGuardWorker+通知渠道+POST_NOTIFICATIONS/SCHEDULE_EXACT_ALARM 引导+Robolectric 单测
## 并行纪律
只动 core/scheduler 与清单中提醒相关段；不碰 feature/widget（B4-2 车道）；清单改动只追加不重排
## 验收（Hermes 亲自跑）
- ./gradlew :core:scheduler:test 全绿；模拟器：设一门 5 分钟后的课→收到通知→改课重排生效
## 禁止
- 绕过 ChangeNotifier 自行监听；在 Receiver 里写业务规则（规则全在 domain）
BODY

card B4-2 "B4-2 小组件车道:Glance 2x2+4x2+刷新机制" cc-tars 84 "B3-3" <<'BODY'
## 依据
详细设计 §7 全部；概要设计 §6.3
## 文件域
feature/widget/**、app 清单 receiver/provider 追加段
## 产出
NextClassWidget(2×2)+TodayWidget(4×2)（按 §7.1/7.2 布局规格与状态规则）+WidgetUpdater+15min Worker+午夜 Alarm+时间广播接收
## 并行纪律
只动 feature/widget 与清单小组件段；不碰 core/scheduler（B4-1 车道）
## 验收（Hermes 亲自跑）
- 模拟器：添加两枚小组件→数据正确；建课/删课小组件即时变化
## 禁止
- 缓存课程数据；做秒级刷新；点 2×2 跳错页面
BODY

card B4-3 "B4-3 走查:批次4评审+集成自测" cc-kotlin-reviewer 83 "B4-1,B4-2" <<'BODY'
## 依据
详细设计 §6-§7、§9
## 文件域
只读；报告落盘 docs/评审-B4.md
## 产出
提醒+小组件联合评审：重排幂等、投影现查现算、权限引导完整、并行期清单合并无冲突
## 禁止
- 修改任何文件
BODY

# ════════ 批次 5：集成验收（2 张）════════
card B5-1 "B5-1 集成:PRD八条验收逐条+release APK" cc-tars 82 "B4-3" <<'BODY'
## 依据
需求文档 §八；详细设计 §10
## 文件域
docs/验收-B5/**、release/**（apk 不进 git）、app 构建配置 release 签名
## 产出
PRD 八条验收逐条执行记录（截图+说明）+ release APK 产物在项目根 release/（gitignore）+ VERSION.txt
## 验收（Hermes 亲自跑）
- 干净 worktree 检出 → testDebugUnitTest 全绿 → assembleRelease 真实出包 → 八条逐条核对表
## 禁止
- 测试没跑就写「通过」；把 apk 提交进 git
BODY

card B5-2 "B5-2 收尾:全量评审+交付说明" cc-kotlin-reviewer 81 "B5-1" <<'BODY'
## 依据
全部设计文档
## 文件域
只读；报告落盘 docs/评审-B5.md
## 产出
最终走查报告（全局约束 §9 八条逐条核对）+ README 交付说明核对（安装/配置识别服务/添加小组件/权限引导）
## 禁止
- 修改任何文件
BODY

# ── 生成人读文档 ────────────────────────────────────────────────
mkdir -p "$ROOT/docs"
{
  echo "# 看板任务分解（timetable 板，与看板同源生成）"; echo
  echo "| 卡 | 内容 | 角色 | 优先级 | 依赖 |"; echo "| --- | --- | --- | --- | --- |"
  for k in $(printf '%s\n' "${!TITLES[@]}" | sort -t- -k1,1 -k2,2n); do
    printf '| %s | %s | %s | %s | %s |\n' "$k" "${TITLES[$k]}" "${WHO[$k]}" "${PRI[$k]}" "${DEPS[$k]:--}"
  done
  echo
  echo "## 批次闸门"
  echo "- B0→B1：tools/env.sh 真实可用（java -version / sdkmanager 输出）"
  echo "- B1→B2：domain 单测全绿 + 评审卡通过"
  echo "- B2→B3：模拟器手动建课-改课-持久验证 + 评审卡通过"
  echo "- B3→B4：识别→确认→入库端到端 + 评审卡通过"
  echo "- B4→B5：小组件/提醒模拟器实测 + 评审卡通过"
  echo "- B5：PRD 八条 + 干净检出 release 包"
} > "$ROOT/docs/看板任务分解.md"

echo "── 收工自检 ──"
hermes kanban --board "$BOARD" stats
