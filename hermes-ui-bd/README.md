# HermesUI-BD定制

Hermes 桌面端单文件主题插件：深蓝配色 + 底栏状态条 + 聊天区头像与上下文计量。

## 功能

| 模块 | 说明 |
|---|---|
| 深蓝主题 V3.4 | 近黑蓝底 + 亮蓝主色 + 青绿点缀，注册为可选主题 |
| 底栏状态条 | 左侧：HERMES 标识 / 网关状态 / 模型 / 活跃会话 / 消息数；右侧：时钟 / TOKEN 用量 / 版本 |
| 审批模式 chip | 直接切换 `approvals.mode`，与官方设置同源 |
| 聊天区增强 | 头像圆角、气泡间距、上下文用量条样式 |
| 上下文计量 | 读取 `focusedUsage`，会话级 token 用量显示在底栏 |

## 安装

1. 确认 Hermes 桌面端已启用磁盘插件（`desktop-plugins/` 目录）。
2. 把整个 `hermes-ui-bd` 目录复制到：
   - Windows: `%HERMES_HOME%\desktop-plugins\hermes-ui-bd\`
   - 例如 `D:\ai\hermes\desktop-plugins\hermes-ui-bd\`
3. 目录名必须是 `hermes-ui-bd`（插件 `id` 必须与目录名一致，见 `plugin.js` 里的 `XY_PLUGIN_ID`）。
4. 重启 Hermes 桌面端，在「插件」里看到 **HermesUI-BD定制** 并确认开关为开启。
5. 在「外观 / 主题」里选择 **深蓝主题 V3.4（BD）**。

## 文件

| 文件 | 说明 |
|---|---|
| `plugin.js` | 单文件 ESM 插件，约 190KB，无外部依赖（除 Hermes 提供的 `react` / `@hermes/plugin-sdk`） |

## 说明

- 插件只做**外观与状态展示**，不改动 Hermes 核心逻辑，不发起任何外部网络请求。
- 读取的数据全部来自桌面端已有的 SDK 状态（`state.gateway` / `state.model` / `state.focusedUsage` 等）。
- 不含任何账号、密钥、个人路径信息。
- 主题基于深蓝配色方案实现；如需自定义颜色，改 `plugin.js` 中 `XY_THEME.colors` 段。

## 卸载

删除 `desktop-plugins/hermes-ui-bd/` 目录并重启桌面端即可；已选主题会自动回落到默认主题。
