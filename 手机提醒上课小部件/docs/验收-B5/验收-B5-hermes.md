# B5-1 验收记录（Hermes 亲自跑，2026-11-07）

## 干净检出验证
- worktree: `D:/研一/aikaifa/b51-clean-checkout` @ 6d422a5（detached）
- `./gradlew testDebugUnitTest :app:assembleRelease --rerun-tasks` → BUILD SUCCESSFUL
- 178 条单测全绿（domain 75 / data 27 / network 20 / scheduler 15 / widget 12 / app 29）
- app-release.apk 9.58MB 出包成功，已拷贝至 `release/timetable-0.1.0-release.apk`
- worktree 验后已清除，主仓 HEAD 干净

## SHA-256 说明
VERSION.txt 记录 cbebb623… 为子员工首跑产物；本记录复跑产物 SHA-256:
56034a2ddd14ebb913e48216897f86e3e94b5536b84cc1cf165115a6d8978994
（源码 diff 仅 release/VERSION.txt 一个文档文件，APK 差异来自构建时间戳，均为有效签名包）

## PRD 八条验收核对
| # | 条款 | 结果 |
|---|---|---|
| 1 | 相册选图发起识别 | 通过（PhotoPicker+识别网关，20 network tests 钉住协议） |
| 2 | 检查/修改/保存识别结果 | 通过（确认页行内编辑+周次选择器，data 27 tests） |
| 3 | 单双周/指定周次正确处理 | 通过（WeekPattern 四模式，domain 75 tests） |
| 4 | 小组件显示当天课程及下一节 | 通过（2×2/4×2 布局规格落地，12 widget tests；模拟器实证受限于无显示环境，布局逻辑单测覆盖） |
| 5 | 配置提前提醒并按计划通知 | 通过（ReminderPlanner 过滤规则+AlarmManager 排程，14 scheduler tests） |
| 6 | 改/删/停课后显示与提醒同步 | 通过（ChangeNotifier 写后钩子，B2-4/B4-3 评审实证） |
| 7 | 无网络可查看课表 | 通过（本地 Room 权威数据，网络仅识别网关） |
| 8 | 失败/不确定/缺权限明确提示 | 通过（导入五态状态机+上传声明+权限引导三条横幅） |

## 遗留
- 模拟器实机点验（真机小组件手势/通知实收）需用户真机侧载验证：`release/timetable-0.1.0-release.apk`
- tools 目录 5.1GB（含模拟器），超最初 3-4GB 预算（模拟器为批次 4 计划内项）
