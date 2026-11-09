# 复查要点

**主审两项维持通过：**
- **协议一致** — 请求结构（json_object / temperature 0 / data URI / 60s 超时 / IOException 重试 1 次）与 §5.1 一致并被 MockWebServer 测试钉住；prompt.md 与 §5.3 逐条对应
- **隐私合规** — `redactHeader("Authorization")` 有 HEADERS 级别实测（key 不落日志）；上传声明前置门禁成立，「不发网」由 `recognizer.calls == 0` 断言钉住

**阻塞项状态（3 条）：**

| # | 级别 | 问题 | 状态 |
|---|---|---|---|
| 1 | HIGH | baseUrl 无格式校验 → 无 scheme 地址在协程内构造 Retrofit 抛异常崩溃（`ImportViewModel.kt:151`） | **未修复** |
| 2 | HIGH | `ImportViewModel.kt:136` `catch (Exception)` 吞 `CancellationException` | **未修复** |
| 3 | MEDIUM | `RecognitionConfig` 等 DataStore 符号未提交，HEAD 单独检出编译不过 | 修复已写好，**待提交** |

工作区仅有的改动就是阻塞项 3 的修复（`TimetableDataStore.kt`），阻塞项 1、2 对应文件无任何变化，故仍判 **BLOCK**。报告中附了两条具体修法建议（`toHttpUrlOrNull()` 校验 / 先 rethrow CancellationException），并保留了首轮的 LOW/INFO 项（5xx 重试歧义、`String.format` 默认 Locale、§8.5「发送测试请求」缺失等）。

例行提醒：遵守卡片只读约束，本轮未运行 Gradle 测试，测试执行需在 B3 验收环节确认。
[07:08:45]