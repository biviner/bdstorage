// ═══════════════════════════════════════════════════════════════════════════
//  HermesUI-BD定制   —   Hermes 桌面插件（单文件 ESM）
//  单文件 ESM 桌面插件：直接放入 Hermes 的 desktop-plugins/ 目录即可加载。
// ═══════════════════════════════════════════════════════════════════════════
// ═══ 装配入口（本文件为单文件分发，下列分片标记保留以对应内部结构） ═══
import * as React from 'react'
import { jsx, jsxs } from 'react/jsx-runtime'
import {
  host,
  haptic,
  useValue,
  useTheme,
  requestTheme,
  THEMES_AREA,
  TITLEBAR_AREAS,
  STATUSBAR_AREAS,
} from '@hermes/plugin-sdk'

// ─────────────────────── f2-theme.js ───────────────────────
// ─────────────────────────────────────────────────────────────────────────────
// 分片 2/3：深蓝主题 V3 主题对象 + 皮肤 CSS（BD定制 · 官方桌面插件移植）
//
// 约束：纯 ESM 声明片段 —— 无 import / export / JSX / 相对路径。
//       顶层标识符全部以 XY_THEME / XY_SKIN_ 前缀命名，避免与其它分片冲突。
//       不使用 setInterval（也未使用任何定时器）。
//
// 源材料（只读，未修改）：
//   上游 UI worktree（custom-ui-deploy 分支，本地路径已隐去）
//     · apps/desktop/src/themes/presets.ts:398-431  cyberTechTheme
//     · git diff -- apps/desktop/src/styles.css     +153 行新增块
// 官方基线  Hermes 官方桌面源码 apps/desktop/src/
//     · themes/types.ts:88-105       DesktopTheme / customCSS 语义
//     · themes/context.tsx:332-352   customCSS 注入（单个 <style id="hermes-desktop-custom-css">）
//     · themes/user-themes.ts:127-158 themes 注册区（contributedThemes/resolveTheme）
//
// 交付说明与逐条映射表见分片报告（父 agent 复核用）。
// ─────────────────────────────────────────────────────────────────────────────

// 主题 name：包内建名与已装 qipei-* 插件都避不开冲突，用 xianyi- 命名空间。
// 官方 contributedThemes() 只拒绝「内建名」重复（themes/user-themes.ts:141），
// 因此这个 name 在官方 v0.21.5 上可注册。
const XY_THEME_NAME = 'xianyi-cybertech'

// worktree 把 DEFAULT_SKIN_NAME 改成了 'cybertech'（presets.ts:451）。
// 注册主题 ≠ 选中主题（SDK 行 384），插件侧要真正设成当前主题得由 main 分片调用
// SDK 的 requestTheme(XY_THEME_NAME)（SDK 行 405-416）。此常量把「母版默认皮肤」
// 这一意图交给 main 分片决定是否执行；本分片自身不做任何副作用。
const XY_THEME_IS_DEFAULT_SKIN = true

// 皮肤 CSS —— 由 XY_THEME.customCSS 承载。官方 themes/context.tsx:332-352 在主题
// 应用时把 customCSS 注入单个 <style id="hermes-desktop-custom-css">，切主题/无
// customCSS 时移除，所以下面所有规则**只在「深蓝主题 V3（BD）」为当前主题时生效**，
// 且不会在热重载里堆叠（宿主自己管这一条 style 标签，不是插件 append 的）。
// 注入点在 <head> 末尾 ⇒ 同权重规则按文档顺序压过包内样式表（见下方 :root 覆盖段）。
const XY_SKIN_CSS = `
/* ═════════════════════════════════════════════════════════════════════════════
   BD定制 · 深蓝主题 V3 皮肤 —— worktree apps/desktop/src/styles.css 新增块移植
   ═════════════════════════════════════════════════════════════════════════════ */

/* ── ① 左侧会话行的「当前选中」高亮（母版 V3 组件表）─────────────────────────
   V3 激活行：g-active 135° 渐变 #063255→#12477D + #08A5FF 描边（0.65px，用
   inset 阴影实现、零布局位移）+ 左侧 3×18 #088AFF 亮条（rx1.5）+ #F2F5FF 文字。

   worktree 锚点是它自己加的自造属性 [data-session-selected]（session-row.tsx:395），
   官方没有。官方等价锚点（读源码核对，两套并用）：
   a) 选中行的唯一官方标记 = 行根 div 上由 session-row.tsx:380 条件挂上的 Tailwind
      类 bg-(--ui-row-active-background)；官方集成测试 chat-sidebar.integration.test.tsx:77-81
      也是用 .group.row-hover + 这个类来筛「选中的会话行」。.group.row-hover 是会话行
      专属组合（row-geometry/其它页面的 ".group/row" 是带斜杠的命名 group，不匹配 .group）。
   b) 父 agent CDP 实测的 [data-tour="sessions-sidebar"] [data-active="true"] 确实存在，
      但在官方侧栏里它的携带者只有导航行 SidebarMenuButton（sidebar/index.tsx:1613，
      data-slot="sidebar-menu-button"）与 profile 轨（profile-switcher.tsx:630，
      data-slot="profile-rail-gateway"）—— 故对这两者做 :not() 排除，避免把「选中会话」
      的实心亮青底涂到导航行/profile 轨上。
   子元素（标题/时间/图标）自带文字色，所以用 * 一起改。 */
:is(
  [data-tour='sessions-sidebar'] .group.row-hover[class*='bg-(--ui-row-active-background)'],
  [data-tour='sessions-sidebar'] [data-active='true']:not([data-slot='sidebar-menu-button']):not([data-slot='profile-rail-gateway'])
) {
  position: relative;
  background: linear-gradient(135deg, #063255, #12477D);
  box-shadow: inset 0 0 0 1px #08A5FF;
}

:is(
  [data-tour='sessions-sidebar'] .group.row-hover[class*='bg-(--ui-row-active-background)'],
  [data-tour='sessions-sidebar'] [data-active='true']:not([data-slot='sidebar-menu-button']):not([data-slot='profile-rail-gateway'])
)::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 18px;
  border-radius: 1.5px;
  background: #0889FB;
  transform: translateY(-50%);
}

:is(
  [data-tour='sessions-sidebar'] .group.row-hover[class*='bg-(--ui-row-active-background)'],
  [data-tour='sessions-sidebar'] [data-active='true']:not([data-slot='sidebar-menu-button']):not([data-slot='profile-rail-gateway'])
) * {
  color: #F2F5FF !important;
}

/* 可选：worktree 给会话行根加了 rounded-lg（session-row.tsx:374，与上面的选中态
   同一批改动），官方行根是 rounded-md（6px）。启用即所有会话行 8-9px 圆角。
   非 styles.css 新增行、默认未启用 —— 需要几何完全对齐母版时删掉注释。 */
/*
[data-tour='sessions-sidebar'] .group.row-hover {
  border-radius: var(--radius-lg);
}
*/

/* ── ② 圆角基准（原样保留）──────────────────────────────────────────────────
   上游 apps/desktop/src/styles.css:501 在 @layer base 的 :root 里钉死
   --radius-scalar: 0.2（把 rounded-xl 压到 3.2px）；母版 V3 是 12px 基准
   ⇒ 0.75×1rem=12px（xl：卡片/气泡/Composer）、lg 9px≈行 rx8、md 7.5px≈chip r7）。
   本规则在 customCSS 里、无 @layer，且该 style 标签晚于包内样式表插入
   ⇒ 未分层规则胜过分层规则，不需要 !important。 */
:root {
  --radius-scalar: 0.75;
}

/* ── ③ Composer 壳底部内距（worktree 就地改了上游 styles.css:509 的值）───────
   @layer base :root 的 --composer-shell-pad-block-end: 0.625rem → 2.5rem。
   HUD 不受影响：[data-hud-shell] 挂在 HUD 内层 div 上（app/hud/hud-shell.tsx:331），
   它在元素自身把该变量设回 0px（styles.css:3433）——元素自身声明永远赢过继承值，
   与本段的 :root 覆盖不冲突。 */
:root {
  --composer-shell-pad-block-end: 2.5rem;
}

/* ── ④ 页面底 = 母版 g-bg-base（覆盖上游玻璃层写进 html/body 的行内底色）──── */
html,
body {
  background-color: #000D17 !important;
  background-image: linear-gradient(180deg, #020E19 0%, #000D17 55%, #010B14 100%) !important;
}

/* ── ⑤ 侧栏底 = 母版 g-sidebar（横向渐变）。锚点原样可用 ─────────────────────
   官方 app/chat/sidebar/index.tsx:1580-1593 以 collapsible="none" 渲染侧栏，
   可见根同时带 data-slot="sidebar"（components/ui/sidebar.tsx:186-190）与
   data-tour="sessions-sidebar"，故 worktree 的复合选择器在官方成立。 */
[data-slot='sidebar'][data-tour='sessions-sidebar'] {
  background-image: linear-gradient(90deg, #03131F 0%, #00101C 100%);
}

/* ── ⑥ Composer 表面 = 母版 g-composer（135° 卡渐变）+ #244661 细线 + r12；
       聚焦转亮蓝。锚点原样可用（官方 [data-slot="composer-surface"] 存在）。
   border-color 需 !important：包内 styles.css:1997-1999 有一条
   [data-slot='composer-surface']{border-color:var(--ui-stroke-secondary)!important}
   优先级更高，非 important 压不过。 */
[data-slot='composer-surface'] {
  background-image: linear-gradient(135deg, #091929 0%, #02162A 100%);
  border-color: #244661 !important;
  border-radius: 12px;
}

[data-slot='composer-surface']:focus-within {
  border-color: #0889FB !important;
}

/* ── ⑦ Composer 空态占位符 = 设计稿取值。锚点原样可用 ────────────────────────
   上游 styles.css:2010-2012 同选择器以 color: var(--ui-text-tertiary) !important
   压过一切；本规则同权重、文档顺序更晚 ⇒ 生效，不需要参与优先级战。 */
[data-slot='composer-rich-input']:is(:empty, [data-empty])::before {
  color: #7E98B6 !important;
}

/* ── ⑧ 看板根表面透明化 —— 让「整体背景图」在看板页可见（原样保留）───────────
   官方 plugins/kanban/board.tsx:1337 的根 div className 正是
   "relative flex h-full flex-col overflow-hidden bg-(--ui-surface-background)"，
   与该复合选择器逐字匹配；全库另一处 bg-(--ui-surface-background) 用法
   （components/find-bar.tsx:308）不含 relative/h-full/overflow-hidden，不会被误伤。 */
div.relative.flex.h-full.flex-col.overflow-hidden[class*='bg-(--ui-surface-background)'] {
  background-color: transparent;
}

/* ── ⑨ 侧栏文字色 = 设计稿取值（会话标题 #DCEAFF、时间/分组 #7E98B6）─────────
   行组件读 --ui-text-* 变量，按子树重映射即可整体对齐。锚点同 ⑤，原样可用。 */
[data-slot='sidebar'][data-tour='sessions-sidebar'] {
  --ui-text-primary: var(--xy-design-text-1);
  --ui-text-secondary: var(--xy-design-text-2);
  --ui-text-tertiary: #7E98B6;
  --ui-text-quaternary: #7E98B6;
}

/* ── ⑩ 选中行标题加粗到 600（设计稿 #F2F5FF 600；颜色已由 ① 的 * 规则统一）──
   .hover-marquee 是官方类名：session-row.tsx:544/609 给标题 span 挂它，
   styles.css:1518-1534 也以它作为滚动省略的开关，锚点成立。 */
:is(
  [data-tour='sessions-sidebar'] .group.row-hover[class*='bg-(--ui-row-active-background)'],
  [data-tour='sessions-sidebar'] [data-active='true']:not([data-slot='sidebar-menu-button']):not([data-slot='profile-rail-gateway'])
) .hover-marquee {
  font-weight: 600;
}

/* ── ⑪ 助手气泡：母版 V3 的卡片式气泡 ────────────────────────────────────────
   worktree 载荷 assistant-message.tsx.patch 给这个锚点加了
   rounded-xl border border-border bg-card/50 px-3.5 py-2.5 text-[#F2F5FF]。
   插件改不了 TSX，所以在同一锚点上补样式。取值全部来自 worktree 实例实测：
     圆角 12px = var(--radius-xl)（本表 ② 已把 --radius-scalar 钉到 0.75，
       与母版 rounded-xl 同值，故用变量不写字面值）；
     描边 1px #1A6B93 = 主题 border（实测 rgb(26,107,147)）；
     底色 = 母版实际像素 rgba(17,23,30,.5)。注意官方 bg-card 工具类解析到的底
       色不是主题 card #091929（实测 #11171E，其 oklab 展开 oklab(0.202555
       -0.00498397 -0.0166591 / 0.5) 与本值逐位一致）——要复刻的是母版像素，
       故写字面值；
     内距 10px 14px 10px 12px（py-2.5 / px-3.5，左内侧另被 12px 收紧）；
     文字色 #F2F5FF（官方此处是 text-foreground/95 ⇒ #DCEAFF 94%）。
   用户追加要求（比母版更严的一档）：气泡最大宽度不得越过左/右两个头像列 ——
   即收在内列 [内容区左+40px, 内容区右−40px]。母版只在头像那一侧扣一个槽
   （实测其助手气泡右缘 1582 = 内容区右缘，会压到用户头像列 1552..1582 下面），
   这里按用户口径两侧都扣：内容宽 1437 − 40 = 1397，右缘落在 1721。
   40px = 头像槽（f3 的 30px 头像列 + 10px 列间距），两处硬编码需与 f3 同步。 */
[data-slot='aui_assistant-message-content'] {
  border: 1px solid #1A6B93;
  border-radius: var(--radius-xl);
  background-color: rgba(17, 23, 30, 0.5);
  padding: 10px 14px 10px 12px;
  max-width: calc(100% - 40px);
  color: var(--xy-design-text-1);
}

/* ── ⑫ 用户气泡：右贴、随内容宽、82% 上限、蓝渐变底 ──────────────────────────
   worktree user-message.tsx.patch 两处：
     外层 [data-slot='aui_user-message-root'] 由
       -mx-4 w-[calc(100%+2rem)] px-4  改成  w-full pl-4；
     气泡 .composer-human-message 由
       w-full max-w-full bg-(--dt-user-bubble) text-foreground/95
       改成 ml-auto w-fit max-w-[82%] rounded-xl
            bg-[linear-gradient(45deg,#063255,#12477D)] px-3 py-2 pr-9 text-[#EEF4FF]。
   母版实测：width fit-content、maxWidth 82%、marginLeft auto（598.576px）、
   圆角 12px、内距 8px 36px 8px 12px、底 45° 线性渐变 rgb(6,50,85)→rgb(18,71,125)、
   文字 rgb(238,244,255)。描边沿用官方已有的 border-(--ui-stroke-tertiary)
   （两边该变量同值，不重复声明）。
   用户追加要求：气泡左缘不得压进左侧头像列，故 pl-4 由 16px 加到 40px
   （内容区左 + 头像槽），可用宽 1421 → 1397，与助手气泡右缘共用同一条内列边界。 */
[data-slot='aui_user-message-root'] {
  width: 100%;
  margin-left: 0;
  margin-right: 0;
  padding-left: 2.5rem;
  padding-right: 0;
}

[data-slot='aui_user-message-root'] .composer-human-message {
  width: fit-content;
  max-width: 82%;
  margin-left: auto;
  border-radius: var(--radius-xl);
  background-color: transparent;
  background-image: linear-gradient(45deg, #063255 0%, #12477D 100%);
  padding: 8px 36px 8px 12px;
  color: var(--xy-design-text-1);
}


/* ── ⑬ 字体族：按设计稿正本重排 ───────────────────────────────────────────────
   上游 --dt-font-sans 首项 "Segoe WPC" 本机未安装（canvas 度量 99.23 等于通用
   sans-serif），整条链不含任何 CJK 族 ⇒ 拉丁与中文各走一套隐式回退、基线不齐；
   mono 链前 8 项本机均未安装（实际落 Consolas）；kbd 链前 3 项是 macOS 专有
   （Windows 落 Segoe UI，与全局不同族）。按正本「Microsoft YaHei → Noto Sans
   CJK SC → Arial」收敛。 */
:root {
  /* 上游把这三个令牌写在 html 行内（实测覆盖无效）⇒ 必须 !important */
  --dt-font-sans: 'Microsoft YaHei', 'Noto Sans CJK SC', 'Segoe UI', Arial, system-ui, sans-serif !important;
  --dt-font-mono: Consolas, 'Cascadia Mono', 'JetBrains Mono', ui-monospace, monospace !important;
  --dt-font-kbd: 'Microsoft YaHei', 'Noto Sans CJK SC', 'Segoe UI', Arial, sans-serif !important;
}

/* 兜底：上游重置 --dt-font-* 时仍走同一字栈。 */
html[data-xy-chrome] {
  font-family: var(--dt-font-sans);
}

/* 数字等宽：底栏 TOKEN / 上下文 / 计数刷新时不再横向抖动（正本「数字一律等宽」）。 */
html[data-xy-chrome] * {
  font-variant-numeric: tabular-nums;
}

/* ── ⑭ 文字色收敛 + 字号刻度 ──────────────────────────────────────────────────
   上游把同一基色 #DCEAFF 拆成 8 档 alpha，其中 36% / 40.5% / 45.9% 三档不过 AA
   （36% = 2.89:1，还被用在可点的「新建会话」图标上）。正本只有三档不透明色。 */
:root {
  --ui-text-primary: var(--xy-design-text-1) !important;
  --ui-text-secondary: var(--xy-design-text-2) !important;
  --ui-text-tertiary: var(--xy-design-text-3) !important;
  --ui-text-quaternary: var(--xy-design-text-3) !important;
  --conversation-scaffold-text: var(--xy-design-text-2) !important;
}

/* 标题栏 6 个图标按钮：实测 text-muted-foreground/85 = 4.07:1 → text-2 */
button[class*='titlebar-icon-button'] {
  color: var(--xy-design-text-2);
}

/* 助手气泡右下角操作条（上游 slot aui_msg-actions：分支 / 复制 / 朗读 / 刷新）：
   按钮自带 text-3 最暗档 + opacity .5 ⇒ 常态实测 rgb(126,152,182)@0.5，用户登记「太暗淡」。
   与标题栏图标按钮同一口径提到 text-2，并把 opacity 显式归 1（否则仍被上游的 50% 压暗）。
   hover 必须自己写：本规则权重高于上游的 hover:opacity-100，不写就没有悬停反馈。 */
[data-slot='aui_assistant-message-root'] [data-slot='aui_msg-actions'] button {
  color: var(--xy-design-text-2);
  opacity: 1;
}
[data-slot='aui_assistant-message-root'] [data-slot='aui_msg-actions'] button:hover {
  color: var(--xy-design-text-1);
}
/* 不可用时仍要看得出来（如串流中的「刷新」），别让归 1 的 opacity 把禁用态抹平 */
[data-slot='aui_assistant-message-root'] [data-slot='aui_msg-actions'] button:disabled {
  opacity: .45;
}

/* 键帽 Ctrl / N：实测 4.2:1 → text-2 */
[class*='--dt-font-kbd'] {
  color: var(--xy-design-text-2);
}

/* 标题带会话名与 tab：实测 9px → 11 档（正本原生标题带文本为 11 档） */
[class*='text-[9px]'] {
  font-size: var(--xy-design-fs-small);
}

/* 底栏文本：实测 11.2px（0.7rem）→ 11 档；中性计数不再加粗（600 只给强调/警告段） */
.xyc-status,
.xyc-status * {
  font-size: var(--xy-design-fs-small);
}

.xyc-status-value {
  font-weight: 400;
}

.xyc-status-accent,
.xyc-status-warn {
  font-weight: 600;
}

/* 侧栏分区标题 / 时间桶标题：10.24px、字距 1.64px → 10.5px、字距 1px（正本刻度） */
[data-slot='sidebar-group'] [class*='group/section-label'] span,
[class*='text-[0.64rem]'][class*='font-semibold'] {
  font-size: var(--xy-design-fs-label);
  letter-spacing: 1px;
}

/* composer 输入：13 → 13.5（正本 placeholder 13.5） */
[data-slot='composer-rich-input'] {
  font-size: 13.5px;
}

/* ── ⑮ 按钮配色 ───────────────────────────────────────────────────────────────
   发送/语音主控原为「浅底深字」（#DCEAFF@94% + #030D14），与正本相反 —— 正本是
   g-cyan 青色渐变圆钮（#20ADFF → #0889FB）+ 字色 #010B14（对 #0889FB 5.65:1）。
   background-image 不在本次允许族，故取渐变终点 #0889FB 作底色。 */
[class*='rounded-full'][class*='bg-foreground'][class*='text-background'] {
  background-color: var(--xy-design-nav-accent);
  color: #010B14;
}

/* 宽提交胶囊：上游 --dt-primary-solid #066ec9 → 正本主色（全应用只留一个主色） */
[class*='bg-primary'][class*='text-primary-foreground'] {
  background-color: var(--xy-design-nav-accent);
  color: #010B14;
}

/* 激活态图标按钮：上游声明了 bg-primary/10 + text-primary，但实测文本未取到主色 */
[class*='bg-primary/10'][class*='text-primary'] {
  color: var(--xy-design-nav-accent);
}

/* 行内次要按钮（关闭 / Add context / 管理配置档案 / 模型芯片）：与侧栏三级文字统一 */
button[class*='text-(--ui-text-tertiary)'] {
  color: var(--xy-design-text-3);
}

/* 底栏胶囊与芯片：正本为纯文字 + 细描边，无填充块 */
.xyc-pill,
.xyc-chip {
  background-color: transparent;
}

/* ── ⑯ 状态色：三个绿收敛为一个，两个空令牌补值 ────────────────────────────────
   --ui-warning / --ui-danger 上游解析为空串（任何 var() 消费者声明无效），
   --ui-success 又是第三个绿；统一到正本 #00DBA9 / #FFBD4B / #EF4444。 */
:root {
  --ui-success: var(--xy-design-ok) !important;
  --ui-warning: var(--xy-design-warn) !important;
  --ui-danger: #EF4444 !important;
}


/* ── ⑰ 图片与后文间距 —— 含上游溢出的修正 ─────────────────────────────────
   上游 zoomable-image.tsx:39 的容器类是 group/image relative inline-block max-w-full
   align-top，但 markdown 路径额外传了 absolute left-0 top-0 block h-full w-fit：
   图片因此被绝对定位，包装盒高度 = 容器高度（实测 346 = 346）。上游又给图片设了
   **上下各 26px 的 margin** ⇒ 图片边框盒在包装内下移 26px、**底部溢出包装盒 26px**，
   而容器带 position:relative ⇒ 这 26px 画在紧随其后的正文之上。
   实测证据：文字行盒顶端比图片下沿高 14px；在重叠点调用 elementFromPoint 返回 IMG，
   栈序 [IMG, P, DIV] —— 命中测试证实图片盖住文字，不是错觉。
   修法：① 取消该包装内图片的上下 margin，让图片与为它预留的盒子对齐（消除溢出）；
        ② 给包装盒补 10px 下间距，保证与后文有稳定空隙。 */
[data-slot='aui_assistant-message-content'] [class*='group/image'] img {
  margin-top: 0 !important;
  margin-bottom: 0 !important;
  vertical-align: bottom;
}

[data-slot='aui_assistant-message-content'] [class*='group/image'] {
  margin-bottom: 10px;
}
/* ⑸ 会话行的「⋯」仅在悬停/聚焦时显示 ────────────────
   实测（修复前）：侧栏会话行右侧的 dropdown-menu-trigger 在无悬停时
   computed opacity 即为 1 ⇒ 常显。用户登记：应仅在 focuse 状态下显示。
   作用域限定在侧栏内（不动消息区、代码块里的同类按钮）；
   悬停该行、行内有焦点、按钮本身键盘聚焦、或菜单已展开时显示。
   注意：隐藏时必须同时 pointer-events:none，否则会留下看不见的点击热区。 */
[data-slot='sidebar'] [data-slot='context-menu-trigger'] [data-slot='dropdown-menu-trigger'] {
  opacity: 0;
  pointer-events: none;
  transition: opacity .12s ease;
}

[data-slot='sidebar'] [data-slot='context-menu-trigger']:hover [data-slot='dropdown-menu-trigger'],
[data-slot='sidebar'] [data-slot='context-menu-trigger']:focus-within [data-slot='dropdown-menu-trigger'],
[data-slot='sidebar'] [data-slot='dropdown-menu-trigger']:focus-visible,
[data-slot='sidebar'] [data-slot='dropdown-menu-trigger'][aria-expanded='true'],
[data-slot='sidebar'] [data-slot='dropdown-menu-trigger'][data-state='open'] {
  opacity: 1;
  pointer-events: auto;
}

`

// DesktopTheme（官方 themes/types.ts:88-105）：name / label / description / colors
// 必填；darkColors / typography / terminal / darkTerminal / customCSS 可选。
// worktree 只给 colors（无 darkColors ⇒ 官方按 types.ts:92 注释「colors 同时用于
// 亮/暗」，与本主题「近黑蓝底」的意图一致）。
// 颜色键 26 个 = DesktopThemeColors（types.ts:13-48）的 19 个必填 + 7 个可选，
// 与 worktree presets.ts:403-430 逐字段一致，无增删改。
const XY_THEME = {
  name: XY_THEME_NAME,
  label: '深蓝主题 V3.4（BD）',
  description: '近黑蓝底 + 亮蓝 + 青绿 — 深蓝主题 V3.4（BD定制）',
  colors: {
    background: '#000D17',
    foreground: '#DCEAFF',
    card: '#091929',
    cardForeground: '#DCEAFF',
    muted: '#02162A',
    mutedForeground: '#7E98B6',
    popover: '#091B2C',
    popoverForeground: '#DCEAFF',
    primary: '#0889FB',
    primaryForeground: '#F2F5FF',
    secondary: '#091929',
    secondaryForeground: '#DCEAFF',
    accent: '#9162FB',
    accentForeground: '#F2F5FF',
    border: '#1A6B93',
    input: '#244661',
    ring: '#0889FB',
    midground: '#0889FB',
    midgroundForeground: '#F2F5FF',
    composerRing: '#0889FB',
    destructive: '#EF4444',
    destructiveForeground: '#FFFFFF',
    sidebarBackground: '#03131F',
    sidebarBorder: '#1A6B93',
    userBubble: '#12477D',
    userBubbleBorder: '#2B506F'
  },
  customCSS: XY_SKIN_CSS
}

// themes 注册区的 area 常量值：THEMES_AREA = 'themes'
// （官方 apps/desktop/src/themes/user-themes.ts:132，SDK 行 235/378-382 的用法）。
// 贡献体只提供 data（= DesktopTheme）；注册只登记不选中（SDK 行 384）。
const XY_SKIN_THEME_CONTRIBUTION = {
  id: 'xianyi-cybertech',
  area: 'themes',
  data: XY_THEME
}
// ─────────────────────── f1-chrome.js ───────────────────────
/**
 * f1-chrome.js —— 分片 1/3：顶部品牌栏 + 全局背景层 + 底部状态条
 *
 * 对应上游 UI worktree（custom-ui-deploy 分支）新增的
 * apps/desktop/src/app/shell/global-chrome.tsx（AppTopNav / GlobalBackdrop / GlobalBottomBar）
 * 与 apps/desktop/src/app/chat/composer/context-meter.tsx（composer 第二行）。
 *
 * 交付约束（父 agent 拼装用）：
 *   - 纯 ESM 片：**无 import / export / JSX 语法**；顶层只有声明，无任何自动执行副作用。
 *   - 依赖父 agent 的 f0-main.js 提供的模块级标识符：React、host、haptic。
 *   - 对外产物：XyChromeInstall(ctx?) → 返回清理函数（f0-main 的
 *     `React.useEffect(() => XyChromeInstall(), [])` 直接把它当 effect cleanup 用）。
 *   - 无 setInterval / 无 react-dom / 无第三方 import；全部 DOM 用原生 API 构建。
 *
 * 为什么三块挂到 document.body，而不是官方 titlebar/statusbar 贡献区：
 *   1) SDK 文档 322-323 行：titleBar 区载荷是 TitlebarTool data（{ id,label,icon,active?,onSelect? }），
 *      只适合「工具按钮」，装不下品牌栏 + 8 项导航 + 全屏背景层这三块自绘 chrome。
 *   2) 官方 titlebar 控件簇自带 translate（titlebar.ts 的 titlebarToolClusterClass +
 *      titlebar-controls.tsx 的 translate-y-(--titlebar-controls-y-nudge)）。祖先一旦有
 *      transform，后代 position:fixed 就以该簇为包含块 —— 放进簇里渲染会定位错乱。
 *   3) 底部状态条要整行遮住官方 statusbar（本片 CSS 把它 display:none）；注册进
 *      STATUSBAR_AREAS 会被自己这条规则一起隐藏。
 *   4) 文档 325-330 行把 titleBar 区称作 permanent mount point，并认可在 useEffect 里注入
 *      <style> / html[data-*] —— 所以 3 个 React 包装组件仍保留，父级可以把它们注册到
 *      TITLEBAR_AREAS.left（获得生命周期），DOM 依旧由本片注入 body：两条路径等价且幂等。
 *   document.body / documentElement / #root 上官方无 transform（styles.css 只有 box-sizing 与
 *   滚动条规则），因此 body 下的 fixed 定位稳定。
 *
 * 清理契约：
 *   - XyChromeInstall() 返回清理函数：移除三块 DOM + 断开全部 atom 订阅/事件监听 +
 *     移除 <style id="xy-chrome-css"> + 移除 html[data-xy-chrome]。重复调用幂等。
 *   - 每次 install 带自增 installId：热重载时新实例接管，过期实例的清理函数自动空转
 *     （否则旧的 onDispose 会把新实例的 UI 拆掉）。
 */

/* ---------------------------------------------------------------- 常量 */

/** 注入 <style> 的 id（拼装契约指定）。 */
const XY_CHROME_STYLE_ID = 'xy-chrome-css'
/** 激活态开关：所有布局级规则都挂在这个属性下，卸载时移除（SDK 文档 328 行认可的 html[data-*]）。 */
const XY_CHROME_ATTR = 'data-xy-chrome'
const XY_CHROME_BACKDROP_ID = 'xy-chrome-backdrop'
const XY_CHROME_STATUS_ID = 'xy-chrome-status'
const XY_CHROME_SVG_NS = 'http://www.w3.org/2000/svg'

/* 顶带（.xyc-top）已整条撤下：导航按原版搬回侧栏左上纵向排列后带内无内容，
   插件不再自绘顶部条 —— 正文让位量即原生标题栏高度。 */
/** 底栏高度（worktree 为 h-9 = 36px）。 */
const XY_CHROME_BOTTOM_H = 36
/** 设计稿侧栏宽基准（global-chrome.tsx:412 底栏 `left: SIDEBAR_W` = 240）；
    运行时以侧栏实测右缘覆盖，见 XY_CHROME_trackLeft。 */
const XY_CHROME_SIDEBAR_W = 240

/**
 * 本片负责的样式串（纯模板字符串常量）。
 * 颜色 100% 走官方主题变量，每个变量都在官方源码核实存在
 * （apps/desktop/src/styles.css）：
 *   --ui-accent:240  --ui-accent-secondary:241  --ui-red:243/598  --ui-yellow:245  --ui-green:246/599
 *   --ui-bg-chrome:297  --ui-bg-sidebar:302  --ui-bg-editor:307
 *   --ui-row-active-background:345  --ui-control-hover-background:354
 *   --ui-text-primary:364  --ui-text-tertiary:366  --ui-text-quaternary:367
 *   --ui-stroke-primary:375  --ui-stroke-secondary:380  --dt-font-sans:476
 * 半透明一律用 color-mix() 从变量派生 —— 不出现任何字面色（SDK 文档 1544-1545 行硬规定）。
 * worktree 的 global-chrome.tsx 里有 31 处 #hex/rgba() 字面色，本片不复刻（见报告「已知差异」）。
 */
/* ════════════ 背景图资产（设计稿《整体应用背景图》v1.0 · bg-app.svg）════════════
   1920×1080 · 纯矢量 · 0 内嵌位图 · sha256 前缀 5777417fd72516ed · 6246 字节。
   工作台 / 看板 / 会话页共用同一张（设计稿：不得各自换图）。
   为什么内联成 data URI：插件是从 blob: 基址求值的 ESM，相对路径 './bg-app.svg'
   无法解析（loader 只放行 @hermes/plugin-sdk 与 react*，非 SDK 的裸/相对 specifier
   直接拒绝），故资产必须随 plugin.js 一同分发。 */
const XY_CHROME_BG_APP_URI =
  'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxOTIwIiBoZWlnaHQ9IjEwODAiIHZpZXdCb3g9IjAgMCAxOTIwIDEwODAiIHByZXNlcnZlQXNwZWN0UmF0aW89InhNaWRZTWlkIHNsaWNlIj4NCjx0aXRsZT5IZXJtZXMg5qGM6Z2iIMK3IOaVtOS9k+W6lOeUqOiDjOaZr+WbviDCtyAxOTIwIMOXIDEwODAgwrcg5bel5L2c5Y+w5LiO55yL5p2/5YWx55SoPC90aXRsZT4NCjxkZXNjPue6r+efoumHj+iDjOaZr+i1hOS6p++8jOWPr+ebtOaOpeS9nOS4uiBDU1MgYmFja2dyb3VuZCDkvb/nlKjjgILlsYLmrKHoh6rkuIvogIzkuIrvvJrln7rlupUgIzAwMEQxNyDihpIg57q15ZCR5b6u5riQ5Y+YICMwMjBFMTnihpIjMDAwRDE3KDU1JSnihpIjMDEwQjE0IOKGkiDlm5vmnprlpKfljYrlvoTlvoTlkJHlhYnmmZXvvIjpnZIgIzA4ODlGQiDlt6bkuIov5Y+z5LiK44CB57SrICM5MTYyRkIg5Y+z5LiL44CB6Z2S57u/ICMwMERCQTkg5bem5LiL77yM5YWJ5pmV5b+D6LS06KeS44CBMjAlIOWNiuW+hOWGheW/q+mAn+ihsOWHj++8ieKGkiAzOHB4IOe7huiFu+e9keagvO+8iOe6vyAjMDg4OUZCIM6xMC4wNu+8jOe6teWQkemBrue9qSAxLjAwLzAuODYvMC43NC8wLjYyIOS4iumDqOa4kOaYvuOAgeS4i+mDqOS/neeVmeWPr+ivu+e6ueeQhu+8m+aoquWQkemBrue9qeW3puWPs+WQhCAxMiUg5riQ6ZqQ77yJ4oaSIOW6lemDqOWOi+aalyAjMDAwIM6xMOKGkjAuMTbvvIh5OTQw4oCTMTA4MO+8ieKGkiDlt6blj7PovrnnvJjljovmmpcgIzAwMCDOsTAuMTLihpIw77yI5ZCEIDIwMHB477yJ44CC5Lu75oSP5Y2V54K56KOF6aWw5pyJ5pWI5Y+g5YqgIOKJpCAwLjEy77yb5Lit5b+DIDcwJSDljLrln5/vvIh4Mjg44oCTMTYzMiAvIHkxNjLigJM5MTjvvInlubPmu5HlupXkuq7luqYgTCA5LjTigJMxNS4444CBzpRMID0gNi40LzI1Ne+8iOWNoOa7oemHj+eoiyAyLjUl77yJ77yM572R5qC857q/55u45a+55bqV6ImyIM6UTCAzLjkg5Li6562J6Led57q555CG6ICM6Z2e54Ot54K577yb5YWo55S75biD5pyA5LquIEwgPSAyNi40LzI1Ne+8iCMwMTFGMzjvvIzkvY3kuo7lhYnmmZUgQSDkuI7nvZHmoLznmoTkuqTngrnvvInvvIzlhajnlLvluIPlnYflgLwgTCAxMi4x44CB5pyA5pqXIEwgNi4544CC5peg5YaF5bWM5L2N5Zu+44CB5peg57yW56CB5pWw5o2u44CB5peg5aSW6YOo6LWE5rqQ5byV55So44CB5peg5paH5a2X5a+56LGh44CCPC9kZXNjPg0KPGRlZnM+DQoNCiAgPGxpbmVhckdyYWRpZW50IGlkPSJiZy13YXNoIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjAiIHkxPSIwIiB4Mj0iMCIgeTI9IjEwODAiPg0KICAgIDxzdG9wIG9mZnNldD0iMCIgc3RvcC1jb2xvcj0iIzAyMEUxOSIvPg0KICAgIDxzdG9wIG9mZnNldD0iMC41NSIgc3RvcC1jb2xvcj0iIzAwMEQxNyIvPg0KICAgIDxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iIzAxMEIxNCIvPg0KICA8L2xpbmVhckdyYWRpZW50Pg0KDQogIDxyYWRpYWxHcmFkaWVudCBpZD0iYmctZ2xvdy1jeWFuLXRsIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgY3g9IjExMCIgY3k9IjcwIiByPSI5MDAiPg0KICAgIDxzdG9wIG9mZnNldD0iMCIgc3RvcC1jb2xvcj0iIzA4ODlGQiIgc3RvcC1vcGFjaXR5PSIwLjA5NSIvPg0KICAgIDxzdG9wIG9mZnNldD0iMC4yMCIgc3RvcC1jb2xvcj0iIzA4ODlGQiIgc3RvcC1vcGFjaXR5PSIwLjAyOCIvPg0KICAgIDxzdG9wIG9mZnNldD0iMC41NSIgc3RvcC1jb2xvcj0iIzA4ODlGQiIgc3RvcC1vcGFjaXR5PSIwLjAwOCIvPg0KICAgIDxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iIzA4ODlGQiIgc3RvcC1vcGFjaXR5PSIwIi8+DQogIDwvcmFkaWFsR3JhZGllbnQ+DQoNCiAgPHJhZGlhbEdyYWRpZW50IGlkPSJiZy1nbG93LXB1cnBsZS1iciIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiIGN4PSIxODMwIiBjeT0iMTAxMCIgcj0iODYwIj4NCiAgICA8c3RvcCBvZmZzZXQ9IjAiIHN0b3AtY29sb3I9IiM5MTYyRkIiIHN0b3Atb3BhY2l0eT0iMC4wOTIiLz4NCiAgICA8c3RvcCBvZmZzZXQ9IjAuMjAiIHN0b3AtY29sb3I9IiM5MTYyRkIiIHN0b3Atb3BhY2l0eT0iMC4wMjciLz4NCiAgICA8c3RvcCBvZmZzZXQ9IjAuNTUiIHN0b3AtY29sb3I9IiM5MTYyRkIiIHN0b3Atb3BhY2l0eT0iMC4wMDgiLz4NCiAgICA8c3RvcCBvZmZzZXQ9IjEiIHN0b3AtY29sb3I9IiM5MTYyRkIiIHN0b3Atb3BhY2l0eT0iMCIvPg0KICA8L3JhZGlhbEdyYWRpZW50Pg0KDQogIDxyYWRpYWxHcmFkaWVudCBpZD0iYmctZ2xvdy1jeWFuLXRyIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgY3g9IjE3MDAiIGN5PSIxMTAiIHI9IjcyMCI+DQogICAgPHN0b3Agb2Zmc2V0PSIwIiBzdG9wLWNvbG9yPSIjMDg4OUZCIiBzdG9wLW9wYWNpdHk9IjAuMDU1Ii8+DQogICAgPHN0b3Agb2Zmc2V0PSIwLjIwIiBzdG9wLWNvbG9yPSIjMDg4OUZCIiBzdG9wLW9wYWNpdHk9IjAuMDE2Ii8+DQogICAgPHN0b3Agb2Zmc2V0PSIwLjU1IiBzdG9wLWNvbG9yPSIjMDg4OUZCIiBzdG9wLW9wYWNpdHk9IjAuMDA1Ii8+DQogICAgPHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjMDg4OUZCIiBzdG9wLW9wYWNpdHk9IjAiLz4NCiAgPC9yYWRpYWxHcmFkaWVudD4NCg0KICA8cmFkaWFsR3JhZGllbnQgaWQ9ImJnLWdsb3ctdGVhbC1ibCIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiIGN4PSIyNDAiIGN5PSIxMDMwIiByPSI2NDAiPg0KICAgIDxzdG9wIG9mZnNldD0iMCIgc3RvcC1jb2xvcj0iIzAwREJBOSIgc3RvcC1vcGFjaXR5PSIwLjA0MiIvPg0KICAgIDxzdG9wIG9mZnNldD0iMC4yMCIgc3RvcC1jb2xvcj0iIzAwREJBOSIgc3RvcC1vcGFjaXR5PSIwLjAxMyIvPg0KICAgIDxzdG9wIG9mZnNldD0iMC41NSIgc3RvcC1jb2xvcj0iIzAwREJBOSIgc3RvcC1vcGFjaXR5PSIwLjAwNCIvPg0KICAgIDxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iIzAwREJBOSIgc3RvcC1vcGFjaXR5PSIwIi8+DQogIDwvcmFkaWFsR3JhZGllbnQ+DQoNCiAgPGxpbmVhckdyYWRpZW50IGlkPSJiZy1ncmlkLW1hc2stdiIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiIHgxPSIwIiB5MT0iMCIgeDI9IjAiIHkyPSIxMDgwIj4NCiAgICA8c3RvcCBvZmZzZXQ9IjAiIHN0b3AtY29sb3I9IiNGRkZGRkYiIHN0b3Atb3BhY2l0eT0iMS4wMCIvPg0KICAgIDxzdG9wIG9mZnNldD0iMC4zMCIgc3RvcC1jb2xvcj0iI0ZGRkZGRiIgc3RvcC1vcGFjaXR5PSIwLjg2Ii8+DQogICAgPHN0b3Agb2Zmc2V0PSIwLjYwIiBzdG9wLWNvbG9yPSIjRkZGRkZGIiBzdG9wLW9wYWNpdHk9IjAuNzQiLz4NCiAgICA8c3RvcCBvZmZzZXQ9IjEiIHN0b3AtY29sb3I9IiNGRkZGRkYiIHN0b3Atb3BhY2l0eT0iMC42MiIvPg0KICA8L2xpbmVhckdyYWRpZW50Pg0KDQogIDxsaW5lYXJHcmFkaWVudCBpZD0iYmctZ3JpZC1tYXNrLWgiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMCIgeTE9IjAiIHgyPSIxOTIwIiB5Mj0iMCI+DQogICAgPHN0b3Agb2Zmc2V0PSIwIiBzdG9wLWNvbG9yPSIjRkZGRkZGIiBzdG9wLW9wYWNpdHk9IjAiLz4NCiAgICA8c3RvcCBvZmZzZXQ9IjAuMTIiIHN0b3AtY29sb3I9IiNGRkZGRkYiIHN0b3Atb3BhY2l0eT0iMC44NSIvPg0KICAgIDxzdG9wIG9mZnNldD0iMC44OCIgc3RvcC1jb2xvcj0iI0ZGRkZGRiIgc3RvcC1vcGFjaXR5PSIwLjg1Ii8+DQogICAgPHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjRkZGRkZGIiBzdG9wLW9wYWNpdHk9IjAiLz4NCiAgPC9saW5lYXJHcmFkaWVudD4NCg0KICA8bGluZWFyR3JhZGllbnQgaWQ9ImJnLWJvdHRvbS1zaGFkZSIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiIHgxPSIwIiB5MT0iOTQwIiB4Mj0iMCIgeTI9IjEwODAiPg0KICAgIDxzdG9wIG9mZnNldD0iMCIgc3RvcC1jb2xvcj0iIzAwMDAwMCIgc3RvcC1vcGFjaXR5PSIwIi8+DQogICAgPHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjMDAwMDAwIiBzdG9wLW9wYWNpdHk9IjAuMTYiLz4NCiAgPC9saW5lYXJHcmFkaWVudD4NCg0KICA8bGluZWFyR3JhZGllbnQgaWQ9ImJnLXZpZ25ldHRlLWwiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMCIgeTE9IjAiIHgyPSIyMDAiIHkyPSIwIj4NCiAgICA8c3RvcCBvZmZzZXQ9IjAiIHN0b3AtY29sb3I9IiMwMDAwMDAiIHN0b3Atb3BhY2l0eT0iMC4xMiIvPg0KICAgIDxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iIzAwMDAwMCIgc3RvcC1vcGFjaXR5PSIwIi8+DQogIDwvbGluZWFyR3JhZGllbnQ+DQoNCiAgPGxpbmVhckdyYWRpZW50IGlkPSJiZy12aWduZXR0ZS1yIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjE3MjAiIHkxPSIwIiB4Mj0iMTkyMCIgeTI9IjAiPg0KICAgIDxzdG9wIG9mZnNldD0iMCIgc3RvcC1jb2xvcj0iIzAwMDAwMCIgc3RvcC1vcGFjaXR5PSIwIi8+DQogICAgPHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjMDAwMDAwIiBzdG9wLW9wYWNpdHk9IjAuMTIiLz4NCiAgPC9saW5lYXJHcmFkaWVudD4NCg0KICA8bWFzayBpZD0iYmctbWFzay12IiBtYXNrVW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4PSIwIiB5PSIwIiB3aWR0aD0iMTkyMCIgaGVpZ2h0PSIxMDgwIj4NCiAgICA8cmVjdCB4PSIwIiB5PSIwIiB3aWR0aD0iMTkyMCIgaGVpZ2h0PSIxMDgwIiBmaWxsPSJ1cmwoI2JnLWdyaWQtbWFzay12KSIvPg0KICA8L21hc2s+DQoNCiAgPG1hc2sgaWQ9ImJnLW1hc2staCIgbWFza1VuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeD0iMCIgeT0iMCIgd2lkdGg9IjE5MjAiIGhlaWdodD0iMTA4MCI+DQogICAgPHJlY3QgeD0iMCIgeT0iMCIgd2lkdGg9IjE5MjAiIGhlaWdodD0iMTA4MCIgZmlsbD0idXJsKCNiZy1ncmlkLW1hc2staCkiLz4NCiAgPC9tYXNrPg0KDQogIDxwYXR0ZXJuIGlkPSJiZy1ncmlkLTM4IiBwYXR0ZXJuVW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4PSIwIiB5PSIwIiB3aWR0aD0iMzgiIGhlaWdodD0iMzgiPg0KICAgIDxwYXRoIGQ9Ik0wIDAuNUgzOE0wLjUgMFYzOCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjMDg4OUZCIiBzdHJva2Utd2lkdGg9IjEiIHN0cm9rZS1vcGFjaXR5PSIwLjA2Ii8+DQogIDwvcGF0dGVybj4NCg0KPC9kZWZzPg0KDQo8ZyBpZD0iYmctYXBwLWFydCI+DQogIDxyZWN0IGlkPSJiZy1iYXNlIiB4PSIwIiB5PSIwIiB3aWR0aD0iMTkyMCIgaGVpZ2h0PSIxMDgwIiBmaWxsPSIjMDAwRDE3Ii8+DQogIDxyZWN0IGlkPSJiZy13YXNoLWxheWVyIiB4PSIwIiB5PSIwIiB3aWR0aD0iMTkyMCIgaGVpZ2h0PSIxMDgwIiBmaWxsPSJ1cmwoI2JnLXdhc2gpIi8+DQogIDxyZWN0IGlkPSJiZy1nbG93LWEtY3lhbi10bCIgeD0iMCIgeT0iMCIgd2lkdGg9IjE5MjAiIGhlaWdodD0iMTA4MCIgZmlsbD0idXJsKCNiZy1nbG93LWN5YW4tdGwpIi8+DQogIDxyZWN0IGlkPSJiZy1nbG93LWItcHVycGxlLWJyIiB4PSIwIiB5PSIwIiB3aWR0aD0iMTkyMCIgaGVpZ2h0PSIxMDgwIiBmaWxsPSJ1cmwoI2JnLWdsb3ctcHVycGxlLWJyKSIvPg0KICA8cmVjdCBpZD0iYmctZ2xvdy1jLWN5YW4tdHIiIHg9IjAiIHk9IjAiIHdpZHRoPSIxOTIwIiBoZWlnaHQ9IjEwODAiIGZpbGw9InVybCgjYmctZ2xvdy1jeWFuLXRyKSIvPg0KICA8cmVjdCBpZD0iYmctZ2xvdy1kLXRlYWwtYmwiIHg9IjAiIHk9IjAiIHdpZHRoPSIxOTIwIiBoZWlnaHQ9IjEwODAiIGZpbGw9InVybCgjYmctZ2xvdy10ZWFsLWJsKSIvPg0KICA8ZyBpZD0iYmctZ3JpZCIgbWFzaz0idXJsKCNiZy1tYXNrLXYpIj4NCiAgICA8cmVjdCB4PSIwIiB5PSIwIiB3aWR0aD0iMTkyMCIgaGVpZ2h0PSIxMDgwIiBmaWxsPSJ1cmwoI2JnLWdyaWQtMzgpIiBtYXNrPSJ1cmwoI2JnLW1hc2staCkiLz4NCiAgPC9nPg0KICA8cmVjdCBpZD0iYmctYm90dG9tLXNoYWRlLWxheWVyIiB4PSIwIiB5PSI5NDAiIHdpZHRoPSIxOTIwIiBoZWlnaHQ9IjE0MCIgZmlsbD0idXJsKCNiZy1ib3R0b20tc2hhZGUpIi8+DQogIDxyZWN0IGlkPSJiZy12aWduZXR0ZS1sZWZ0IiB4PSIwIiB5PSIwIiB3aWR0aD0iMjAwIiBoZWlnaHQ9IjEwODAiIGZpbGw9InVybCgjYmctdmlnbmV0dGUtbCkiLz4NCiAgPHJlY3QgaWQ9ImJnLXZpZ25ldHRlLXJpZ2h0IiB4PSIxNzIwIiB5PSIwIiB3aWR0aD0iMjAwIiBoZWlnaHQ9IjEwODAiIGZpbGw9InVybCgjYmctdmlnbmV0dGUtcikiLz4NCjwvZz4NCjwvc3ZnPg0K'

/* 会话页对话流背景装饰（设计稿 UI设计方案-会话页-v3.svg 的 chat-aurora 组：
   「背景装饰 · 青色粒子与波形（纯矢量，低调）」，400 粒子 + 3 波形 + 3 椭圆）。
   底稿层 clip-chat = 198,78,990,896，本资产已按该矩形归一到自身坐标系 990x896，
   故设计稿的 clip-path 由「挂在对话流容器上」等价实现 —— 只覆盖对话流，不越到侧栏/右栏。 */
const XY_CHROME_CHAT_AURORA_URI =
  'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbG5zOmlua3NjYXBlPSJodHRwOi8vd3d3Lmlua3NjYXBlLm9yZy9uYW1lc3BhY2VzL2lua3NjYXBlIiB3aWR0aD0iOTkwIiBoZWlnaHQ9Ijg5NiIgdmlld0JveD0iMCAwIDk5MCA4OTYiPg0KPGRlZnM+DQoNCjxjbGlwUGF0aCBpZD0iY2xpcC1jaGF0Ij48cmVjdCB4PSIxOTgiIHk9Ijc4IiB3aWR0aD0iOTkwIiBoZWlnaHQ9Ijg5NiIvPjwvY2xpcFBhdGg+DQo8cmFkaWFsR3JhZGllbnQgaWQ9ImctaGFsby1jeWFuIiBjeD0iMC41IiBjeT0iMC41IiByPSIwLjUiPg0KICAgIDxzdG9wIG9mZnNldD0iMCIgc3RvcC1jb2xvcj0iIzA4ODlGQiIgc3RvcC1vcGFjaXR5PSIwLjIwIi8+DQogICAgPHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjMDg4OUZCIiBzdG9wLW9wYWNpdHk9IjAiLz4NCiAgPC9yYWRpYWxHcmFkaWVudD4NCjxyYWRpYWxHcmFkaWVudCBpZD0iZy13YXZlLWdsb3ciIGN4PSIwLjUiIGN5PSIwLjUiIHI9IjAuNSI+DQogICAgPHN0b3Agb2Zmc2V0PSIwIiBzdG9wLWNvbG9yPSIjMjBBREZGIiBzdG9wLW9wYWNpdHk9IjAuMzAiLz4NCiAgICA8c3RvcCBvZmZzZXQ9IjAuNTUiIHN0b3AtY29sb3I9IiMwODg5RkIiIHN0b3Atb3BhY2l0eT0iMC4xMCIvPg0KICAgIDxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iIzIwQURGRiIgc3RvcC1vcGFjaXR5PSIwIi8+DQogIDwvcmFkaWFsR3JhZGllbnQ+DQoNCjwvZGVmcz4NCjxnIHRyYW5zZm9ybT0idHJhbnNsYXRlKC0xOTgsLTc4KSI+DQo8ZyBpZD0iY2hhdC1hdXJvcmEiIGlua3NjYXBlOmdyb3VwbW9kZT0ibGF5ZXIiIGlua3NjYXBlOmxhYmVsPSLog4zmma/oo4XppbAgwrcg6Z2S6Imy57KS5a2Q5LiO5rOi5b2i77yI57qv55+i6YeP77yM5L2O6LCD77yJIiBjbGlwLXBhdGg9InVybCgjY2xpcC1jaGF0KSIgZGF0YS1tb2Q9IjEiIGRhdGEteD0iMTk4IiBkYXRhLXk9IjkxIiBkYXRhLXc9Ijk4MiIgZGF0YS1oPSI4ODMiPg0KICAgICAgPGVsbGlwc2UgY3g9IjcwMCIgY3k9Ijc5MCIgcng9IjQ3MCIgcnk9IjE5MCIgZmlsbD0idXJsKCNnLXdhdmUtZ2xvdykiLz4NCiAgICAgIDxlbGxpcHNlIGN4PSI4ODAiIGN5PSI2MjAiIHJ4PSIzMDAiIHJ5PSIxMzAiIGZpbGw9InVybCgjZy1oYWxvLWN5YW4pIiBvcGFjaXR5PSIwLjgiLz4NCiAgICAgIDxlbGxpcHNlIGN4PSI0MjAiIGN5PSI3MDAiIHJ4PSIyNjAiIHJ5PSIxMTAiIGZpbGw9InVybCgjZy1oYWxvLWN5YW4pIiBvcGFjaXR5PSIwLjYiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjI4MS43IiBjeT0iMTA0LjAiIHI9IjAuOTIiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTciLz4NCiAgICAgIDxjaXJjbGUgY3g9IjM4OC43IiBjeT0iMTE5LjQiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjIiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjEwMjguMiIgY3k9IjE4OS41IiByPSIxLjQxIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIxIi8+DQogICAgICA8Y2lyY2xlIGN4PSI5MDAuMiIgY3k9IjYzNC4zIiByPSIxLjI4IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjExIi8+DQogICAgICA8Y2lyY2xlIGN4PSI3ODcuMSIgY3k9IjQzNC4wIiByPSIwLjg2IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjI0Ii8+DQogICAgICA8Y2lyY2xlIGN4PSIzMzEuMyIgY3k9IjMzNi45IiByPSIwLjk0IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE0Ii8+DQogICAgICA8Y2lyY2xlIGN4PSI2MTYuOCIgY3k9Ijc2MS45IiByPSIxLjU1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjI2Ii8+DQogICAgICA8Y2lyY2xlIGN4PSI0NTEuNyIgY3k9IjY1MC4zIiByPSIxLjQ1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjExIi8+DQogICAgICA8Y2lyY2xlIGN4PSI1NzIuMyIgY3k9IjQ1OC42IiByPSIxLjQzIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEwIi8+DQogICAgICA8Y2lyY2xlIGN4PSI4NjUuOCIgY3k9IjUwOC44IiByPSIxLjA4IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEwIi8+DQogICAgICA8Y2lyY2xlIGN4PSI5MDQuMyIgY3k9IjY4OC4wIiByPSIwLjc4IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE0Ii8+DQogICAgICA8Y2lyY2xlIGN4PSIyNTAuNyIgY3k9IjcxOC43IiByPSIxLjYzIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+DQogICAgICA8Y2lyY2xlIGN4PSIxMDU4LjIiIGN5PSIyOTcuNSIgcj0iMS40OSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPg0KICAgICAgPGNpcmNsZSBjeD0iMTA0MC4zIiBjeT0iMTIxLjYiIHI9IjAuODAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjYiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjkyOC45IiBjeT0iMTgxLjIiIHI9IjEuMjUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTkiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjQyMy44IiBjeT0iODQ2LjUiIHI9IjEuMzEiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjU5MC43IiBjeT0iNjY0LjUiIHI9IjAuODMiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMDgiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjM1NC4wIiBjeT0iNTc3LjgiIHI9IjEuMDkiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjciLz4NCiAgICAgIDxjaXJjbGUgY3g9Ijk1MC4zIiBjeT0iMzI4LjQiIHI9IjEuNjEiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTciLz4NCiAgICAgIDxjaXJjbGUgY3g9IjEwNzIuMCIgY3k9Ijc4My45IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEzIi8+DQogICAgICA8Y2lyY2xlIGN4PSIxMDk3LjIiIGN5PSIyMjAuMiIgcj0iMS4wOCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xOCIvPg0KICAgICAgPGNpcmNsZSBjeD0iOTkzLjEiIGN5PSI4NDcuNyIgcj0iMC45OCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNyIvPg0KICAgICAgPGNpcmNsZSBjeD0iMzg4LjMiIGN5PSI0NTcuMSIgcj0iMS4yOCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4wNyIvPg0KICAgICAgPGNpcmNsZSBjeD0iNjU1LjYiIGN5PSIyOTAuNiIgcj0iMS4yMSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNyIvPg0KICAgICAgPGNpcmNsZSBjeD0iNjg3LjQiIGN5PSI4MDIuNSIgcj0iMS4wNyIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMyIvPg0KICAgICAgPGNpcmNsZSBjeD0iNzcwLjYiIGN5PSIxNjkuNSIgcj0iMS40MyIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xOCIvPg0KICAgICAgPGNpcmNsZSBjeD0iODM3LjciIGN5PSIzNDUuMiIgcj0iMC43MiIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPg0KICAgICAgPGNpcmNsZSBjeD0iNTQ5LjIiIGN5PSI2OTQuNiIgcj0iMS4yOCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xOCIvPg0KICAgICAgPGNpcmNsZSBjeD0iNjc3LjciIGN5PSI4MTEuOSIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yNSIvPg0KICAgICAgPGNpcmNsZSBjeD0iNTU2LjEiIGN5PSI0ODMuMiIgcj0iMS42MSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yNSIvPg0KICAgICAgPGNpcmNsZSBjeD0iNTgxLjgiIGN5PSIzNjguMiIgcj0iMS4yNCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMiIvPg0KICAgICAgPGNpcmNsZSBjeD0iMTAzOC40IiBjeT0iNDMxLjMiIHI9IjAuODEiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjMiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjIyMC40IiBjeT0iNzIxLjAiIHI9IjEuNTYiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTkiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjM1Ni4xIiBjeT0iNzE5LjIiIHI9IjEuMDkiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjIiLz4NCiAgICAgIDxjaXJjbGUgY3g9Ijk1NC41IiBjeT0iOTIuNiIgcj0iMC43NSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMCIvPg0KICAgICAgPGNpcmNsZSBjeD0iMjc5LjYiIGN5PSIzNzYuOCIgcj0iMC45NyIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMCIvPg0KICAgICAgPGNpcmNsZSBjeD0iMzgxLjciIGN5PSI2MzcuNSIgcj0iMS4yOCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4wOSIvPg0KICAgICAgPGNpcmNsZSBjeD0iMjc3LjIiIGN5PSI1MTAuOSIgcj0iMS42NSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMiIvPg0KICAgICAgPGNpcmNsZSBjeD0iNTY1LjMiIGN5PSI1MjIuNSIgcj0iMS4yMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4wOSIvPg0KICAgICAgPGNpcmNsZSBjeD0iNTg1LjUiIGN5PSI2ODAuOSIgcj0iMS4yNCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yNiIvPg0KICAgICAgPGNpcmNsZSBjeD0iMTA3Ni41IiBjeT0iMzg4LjEiIHI9IjEuNTIiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz4NCiAgICAgIDxjaXJjbGUgY3g9Ijg1Ny43IiBjeT0iODM0LjgiIHI9IjAuODAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTkiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjIzNC4wIiBjeT0iNzY5LjQiIHI9IjEuMjciIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMDgiLz4NCiAgICAgIDxjaXJjbGUgY3g9Ijg5My4xIiBjeT0iNDkxLjQiIHI9IjEuNDQiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjIiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjY5MS40IiBjeT0iNTYzLjciIHI9IjEuMTQiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjMiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjM5MC44IiBjeT0iMTA0LjMiIHI9IjAuNzMiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTMiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjkzNy44IiBjeT0iMjQwLjUiIHI9IjEuMDkiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTAiLz4NCiAgICAgIDxjaXJjbGUgY3g9Ijc1Ni40IiBjeT0iNzk3LjIiIHI9IjAuNzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTMiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjgxMS4xIiBjeT0iNzM0LjciIHI9IjEuMjkiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMDkiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjEwOTcuMCIgY3k9IjY4MS4wIiByPSIxLjU3IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIzIi8+DQogICAgICA8Y2lyY2xlIGN4PSI3OTEuNiIgY3k9IjQ2Ni4yIiByPSIxLjA4IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIxIi8+DQogICAgICA8Y2lyY2xlIGN4PSI0NzguMCIgY3k9IjY2MC45IiByPSIwLjgwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEwIi8+DQogICAgICA8Y2lyY2xlIGN4PSIxMDk2LjYiIGN5PSI0ODIuNSIgcj0iMS4yOCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xOSIvPg0KICAgICAgPGNpcmNsZSBjeD0iNzE4LjAiIGN5PSI0OTQuMiIgcj0iMS4xNCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4wOCIvPg0KICAgICAgPGNpcmNsZSBjeD0iNzY2LjkiIGN5PSI4MjQuOCIgcj0iMS4wNyIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPg0KICAgICAgPGNpcmNsZSBjeD0iODU5LjgiIGN5PSIxMDEuNCIgcj0iMS40MyIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPg0KICAgICAgPGNpcmNsZSBjeD0iMTEzOC4yIiBjeT0iNDQwLjYiIHI9IjEuNTMiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTEiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjExMjAuMyIgY3k9IjI5Mi40IiByPSIxLjUxIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjI2Ii8+DQogICAgICA8Y2lyY2xlIGN4PSI0ODQuMyIgY3k9Ijc0OS44IiByPSIwLjg5IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIzIi8+DQogICAgICA8Y2lyY2xlIGN4PSIxMDU5LjciIGN5PSIyODMuMCIgcj0iMC45NyIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yNiIvPg0KICAgICAgPGNpcmNsZSBjeD0iOTU5LjAiIGN5PSI5Mi43IiByPSIxLjUyIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIzIi8+DQogICAgICA8Y2lyY2xlIGN4PSIxMTE5LjQiIGN5PSI3MDMuNyIgcj0iMC44MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPg0KICAgICAgPGNpcmNsZSBjeD0iODE5LjkiIGN5PSI2MTQuMCIgcj0iMC44MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMSIvPg0KICAgICAgPGNpcmNsZSBjeD0iMjYxLjQiIGN5PSI0NTkuNSIgcj0iMS41MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPg0KICAgICAgPGNpcmNsZSBjeD0iNzUyLjYiIGN5PSI4MTIuMyIgcj0iMS4zOCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPg0KICAgICAgPGNpcmNsZSBjeD0iODI2LjEiIGN5PSI2MTEuMiIgcj0iMC44MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yNCIvPg0KICAgICAgPGNpcmNsZSBjeD0iMTA4Ni4yIiBjeT0iMTA3LjEiIHI9IjEuNDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTUiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjEwNzUuOCIgY3k9IjcyNi42IiByPSIwLjg0IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjI0Ii8+DQogICAgICA8Y2lyY2xlIGN4PSIxMTE4LjIiIGN5PSI1NTcuNiIgcj0iMS4xOCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMyIvPg0KICAgICAgPGNpcmNsZSBjeD0iNjQzLjkiIGN5PSIzNDUuMSIgcj0iMC45NiIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yNiIvPg0KICAgICAgPGNpcmNsZSBjeD0iMTA4NC4yIiBjeT0iNDg0LjIiIHI9IjEuNTQiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTgiLz4NCiAgICAgIDxjaXJjbGUgY3g9Ijc2Ni40IiBjeT0iMjA3LjQiIHI9IjEuMDIiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjYiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjcwMy4yIiBjeT0iMzIwLjkiIHI9IjEuNDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTUiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjkzOS4yIiBjeT0iNDAwLjAiIHI9IjEuNjMiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjUiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjU5MC42IiBjeT0iMjgyLjIiIHI9IjEuMDQiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjUiLz4NCiAgICAgIDxjaXJjbGUgY3g9Ijc1OC44IiBjeT0iODQzLjUiIHI9IjEuNDkiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTMiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjQ3Ny45IiBjeT0iNzg3LjUiIHI9IjAuOTUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMDciLz4NCiAgICAgIDxjaXJjbGUgY3g9IjM0OS4wIiBjeT0iNDgyLjQiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTAiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjIxMi4wIiBjeT0iNzcxLjIiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSIyMjcuNyIgY3k9Ijc3MS43IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iMjQzLjQiIGN5PSI3NzIuMCIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjI1OS4xIiBjeT0iNzcxLjkiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSIyNzQuOCIgY3k9Ijc3MS42IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iMjkwLjUiIGN5PSI3NzAuOSIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjMwNi4yIiBjeT0iNzcwLjAiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSIzMjEuOSIgY3k9Ijc2OC44IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iMzM3LjYiIGN5PSI3NjcuMyIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjM1My4zIiBjeT0iNzY1LjYiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSIzNjkuMCIgY3k9Ijc2My42IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iMzg0LjciIGN5PSI3NjEuNSIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjQwMC40IiBjeT0iNzU5LjEiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI0MTYuMSIgY3k9Ijc1Ni42IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iNDMxLjgiIGN5PSI3NTMuOSIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjQ0Ny41IiBjeT0iNzUxLjEiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI0NjMuMiIgY3k9Ijc0OC4zIiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iNDc4LjkiIGN5PSI3NDUuMyIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjQ5NC42IiBjeT0iNzQyLjMiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI1MTAuMyIgY3k9IjczOS40IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iNTI2LjAiIGN5PSI3MzYuNCIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjU0MS43IiBjeT0iNzMzLjUiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI1NTcuNCIgY3k9IjczMC43IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iNTczLjEiIGN5PSI3MjguMCIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjU4OC44IiBjeT0iNzI1LjQiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI2MDQuNSIgY3k9IjcyMy4wIiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iNjIwLjIiIGN5PSI3MjAuOCIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjYzNS45IiBjeT0iNzE4LjgiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI2NTEuNiIgY3k9IjcxNy4wIiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iNjY3LjMiIGN5PSI3MTUuNSIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjY4My4wIiBjeT0iNzE0LjIiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI2OTguNyIgY3k9IjcxMy4zIiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iNzE0LjQiIGN5PSI3MTIuNSIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjczMC4xIiBjeT0iNzEyLjEiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI3NDUuOCIgY3k9IjcxMi4wIiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iNzYxLjUiIGN5PSI3MTIuMiIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9Ijc3Ny4yIiBjeT0iNzEyLjYiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI3OTIuOSIgY3k9IjcxMy40IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iODA4LjYiIGN5PSI3MTQuNCIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjgyNC4zIiBjeT0iNzE1LjciIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI4NDAuMCIgY3k9IjcxNy4zIiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iODU1LjciIGN5PSI3MTkuMSIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9Ijg3MS40IiBjeT0iNzIxLjIiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI4ODcuMSIgY3k9IjcyMy40IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iOTAyLjgiIGN5PSI3MjUuOCIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjkxOC41IiBjeT0iNzI4LjQiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI5MzQuMiIgY3k9IjczMS4xIiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iOTQ5LjkiIGN5PSI3MzQuMCIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9Ijk2NS42IiBjeT0iNzM2LjkiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSI5ODEuMyIgY3k9IjczOS44IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iOTk3LjAiIGN5PSI3NDIuOCIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjEwMTIuNyIgY3k9Ijc0NS44IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iMTAyOC40IiBjeT0iNzQ4LjciIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSIxMDQ0LjEiIGN5PSI3NTEuNiIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjEwNTkuOCIgY3k9Ijc1NC40IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iMTA3NS41IiBjeT0iNzU3LjAiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSIxMDkxLjIiIGN5PSI3NTkuNSIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjExMDYuOSIgY3k9Ijc2MS44IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iMTEyMi42IiBjeT0iNzY0LjAiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz48Y2lyY2xlIGN4PSIxMTM4LjMiIGN5PSI3NjUuOSIgcj0iMS4zNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC40NCIvPjxjaXJjbGUgY3g9IjExNTQuMCIgY3k9Ijc2Ny42IiByPSIxLjM1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjQ0Ii8+PGNpcmNsZSBjeD0iMTE2OS43IiBjeT0iNzY5LjAiIHI9IjEuMzUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuNDQiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjIxMi4wIiBjeT0iNzcyLjIiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSIyMjcuNyIgY3k9Ijc2OS4xIiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iMjQzLjQiIGN5PSI3NjYuMCIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjI1OS4xIiBjeT0iNzYzLjAiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSIyNzQuOCIgY3k9Ijc2MC4yIiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iMjkwLjUiIGN5PSI3NTcuNSIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjMwNi4yIiBjeT0iNzU1LjEiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSIzMjEuOSIgY3k9Ijc1My4wIiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iMzM3LjYiIGN5PSI3NTEuMiIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjM1My4zIiBjeT0iNzQ5LjgiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSIzNjkuMCIgY3k9Ijc0OC44IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iMzg0LjciIGN5PSI3NDguMiIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjQwMC40IiBjeT0iNzQ4LjAiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI0MTYuMSIgY3k9Ijc0OC4zIiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iNDMxLjgiIGN5PSI3NDguOSIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjQ0Ny41IiBjeT0iNzUwLjAiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI0NjMuMiIgY3k9Ijc1MS41IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iNDc4LjkiIGN5PSI3NTMuNCIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjQ5NC42IiBjeT0iNzU1LjUiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI1MTAuMyIgY3k9Ijc1OC4wIiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iNTI2LjAiIGN5PSI3NjAuNyIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjU0MS43IiBjeT0iNzYzLjYiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI1NTcuNCIgY3k9Ijc2Ni42IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iNTczLjEiIGN5PSI3NjkuNiIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjU4OC44IiBjeT0iNzcyLjciIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI2MDQuNSIgY3k9Ijc3NS43IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iNjIwLjIiIGN5PSI3NzguNyIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjYzNS45IiBjeT0iNzgxLjQiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI2NTEuNiIgY3k9Ijc4My45IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iNjY3LjMiIGN5PSI3ODYuMiIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjY4My4wIiBjeT0iNzg4LjEiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI2OTguNyIgY3k9Ijc4OS43IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iNzE0LjQiIGN5PSI3OTAuOCIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjczMC4xIiBjeT0iNzkxLjYiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI3NDUuOCIgY3k9Ijc5Mi4wIiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iNzYxLjUiIGN5PSI3OTEuOSIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9Ijc3Ny4yIiBjeT0iNzkxLjQiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI3OTIuOSIgY3k9Ijc5MC41IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iODA4LjYiIGN5PSI3ODkuMSIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjgyNC4zIiBjeT0iNzg3LjQiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI4NDAuMCIgY3k9Ijc4NS40IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iODU1LjciIGN5PSI3ODMuMCIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9Ijg3MS40IiBjeT0iNzgwLjQiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI4ODcuMSIgY3k9Ijc3Ny42IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iOTAyLjgiIGN5PSI3NzQuNyIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjkxOC41IiBjeT0iNzcxLjYiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI5MzQuMiIgY3k9Ijc2OC41IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iOTQ5LjkiIGN5PSI3NjUuNSIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9Ijk2NS42IiBjeT0iNzYyLjUiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSI5ODEuMyIgY3k9Ijc1OS43IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iOTk3LjAiIGN5PSI3NTcuMSIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjEwMTIuNyIgY3k9Ijc1NC43IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iMTAyOC40IiBjeT0iNzUyLjciIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSIxMDQ0LjEiIGN5PSI3NTAuOSIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjEwNTkuOCIgY3k9Ijc0OS42IiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iMTA3NS41IiBjeT0iNzQ4LjYiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSIxMDkxLjIiIGN5PSI3NDguMSIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjExMDYuOSIgY3k9Ijc0OC4wIiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iMTEyMi42IiBjeT0iNzQ4LjMiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz48Y2lyY2xlIGN4PSIxMTM4LjMiIGN5PSI3NDkuMSIgcj0iMS4wNSIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4zMCIvPjxjaXJjbGUgY3g9IjExNTQuMCIgY3k9Ijc1MC4zIiByPSIxLjA1IiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjMwIi8+PGNpcmNsZSBjeD0iMTE2OS43IiBjeT0iNzUxLjgiIHI9IjEuMDUiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMzAiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjIxMi4wIiBjeT0iNjk4LjAiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSIyMjcuNyIgY3k9IjY5NC44IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iMjQzLjQiIGN5PSI2OTEuNyIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjI1OS4xIiBjeT0iNjg4LjgiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSIyNzQuOCIgY3k9IjY4NS45IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iMjkwLjUiIGN5PSI2ODMuMiIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjMwNi4yIiBjeT0iNjgwLjciIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSIzMjEuOSIgY3k9IjY3OC40IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iMzM3LjYiIGN5PSI2NzYuMyIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjM1My4zIiBjeT0iNjc0LjQiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSIzNjkuMCIgY3k9IjY3Mi43IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iMzg0LjciIGN5PSI2NzEuMyIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjQwMC40IiBjeT0iNjcwLjEiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI0MTYuMSIgY3k9IjY2OS4yIiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iNDMxLjgiIGN5PSI2NjguNSIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjQ0Ny41IiBjeT0iNjY4LjEiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI0NjMuMiIgY3k9IjY2OC4wIiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iNDc4LjkiIGN5PSI2NjguMSIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjQ5NC42IiBjeT0iNjY4LjUiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI1MTAuMyIgY3k9IjY2OS4yIiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iNTI2LjAiIGN5PSI2NzAuMSIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjU0MS43IiBjeT0iNjcxLjMiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI1NTcuNCIgY3k9IjY3Mi44IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iNTczLjEiIGN5PSI2NzQuNSIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjU4OC44IiBjeT0iNjc2LjQiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI2MDQuNSIgY3k9IjY3OC41IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iNjIwLjIiIGN5PSI2ODAuOCIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjYzNS45IiBjeT0iNjgzLjMiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI2NTEuNiIgY3k9IjY4Ni4wIiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iNjY3LjMiIGN5PSI2ODguOCIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjY4My4wIiBjeT0iNjkxLjgiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI2OTguNyIgY3k9IjY5NC45IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iNzE0LjQiIGN5PSI2OTguMSIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjczMC4xIiBjeT0iNzAxLjMiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI3NDUuOCIgY3k9IjcwNC42IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iNzYxLjUiIGN5PSI3MDguMCIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9Ijc3Ny4yIiBjeT0iNzExLjMiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI3OTIuOSIgY3k9IjcxNC43IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iODA4LjYiIGN5PSI3MTguMCIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjgyNC4zIiBjeT0iNzIxLjMiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI4NDAuMCIgY3k9IjcyNC41IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iODU1LjciIGN5PSI3MjcuNiIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9Ijg3MS40IiBjeT0iNzMwLjYiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI4ODcuMSIgY3k9IjczMy41IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iOTAyLjgiIGN5PSI3MzYuMiIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjkxOC41IiBjeT0iNzM4LjciIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI5MzQuMiIgY3k9Ijc0MS4xIiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iOTQ5LjkiIGN5PSI3NDMuMiIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9Ijk2NS42IiBjeT0iNzQ1LjIiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSI5ODEuMyIgY3k9Ijc0Ni45IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iOTk3LjAiIGN5PSI3NDguNCIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjEwMTIuNyIgY3k9Ijc0OS42IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iMTAyOC40IiBjeT0iNzUwLjYiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSIxMDQ0LjEiIGN5PSI3NTEuMyIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjEwNTkuOCIgY3k9Ijc1MS44IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iMTA3NS41IiBjeT0iNzUyLjAiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSIxMDkxLjIiIGN5PSI3NTEuOSIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjExMDYuOSIgY3k9Ijc1MS42IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iMTEyMi42IiBjeT0iNzUxLjAiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz48Y2lyY2xlIGN4PSIxMTM4LjMiIGN5PSI3NTAuMSIgcj0iMS42MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4yMCIvPjxjaXJjbGUgY3g9IjExNTQuMCIgY3k9Ijc0OC45IiByPSIxLjYwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjIwIi8+PGNpcmNsZSBjeD0iMTE2OS43IiBjeT0iNzQ3LjYiIHI9IjEuNjAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMjAiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjIxMi4wIiBjeT0iNzk1LjIiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSIyMjcuNyIgY3k9Ijc5Mi4zIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iMjQzLjQiIGN5PSI3ODkuNCIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjI1OS4xIiBjeT0iNzg2LjciIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSIyNzQuOCIgY3k9Ijc4NC4zIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iMjkwLjUiIGN5PSI3ODIuMSIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjMwNi4yIiBjeT0iNzgwLjQiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSIzMjEuOSIgY3k9Ijc3OS4xIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iMzM3LjYiIGN5PSI3NzguMyIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjM1My4zIiBjeT0iNzc4LjAiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSIzNjkuMCIgY3k9Ijc3OC4zIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iMzg0LjciIGN5PSI3NzkuMCIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjQwMC40IiBjeT0iNzgwLjMiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI0MTYuMSIgY3k9Ijc4Mi4wIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iNDMxLjgiIGN5PSI3ODQuMiIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjQ0Ny41IiBjeT0iNzg2LjYiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI0NjMuMiIgY3k9Ijc4OS4zIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iNDc4LjkiIGN5PSI3OTIuMiIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjQ5NC42IiBjeT0iNzk1LjEiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI1MTAuMyIgY3k9Ijc5OC4wIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iNTI2LjAiIGN5PSI4MDAuNyIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjU0MS43IiBjeT0iODAzLjIiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI1NTcuNCIgY3k9IjgwNS41IiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iNTczLjEiIGN5PSI4MDcuMyIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjU4OC44IiBjeT0iODA4LjciIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI2MDQuNSIgY3k9IjgwOS42IiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iNjIwLjIiIGN5PSI4MTAuMCIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjYzNS45IiBjeT0iODA5LjgiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI2NTEuNiIgY3k9IjgwOS4yIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iNjY3LjMiIGN5PSI4MDguMCIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjY4My4wIiBjeT0iODA2LjQiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI2OTguNyIgY3k9IjgwNC4zIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iNzE0LjQiIGN5PSI4MDEuOSIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjczMC4xIiBjeT0iNzk5LjMiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI3NDUuOCIgY3k9Ijc5Ni40IiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iNzYxLjUiIGN5PSI3OTMuNSIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9Ijc3Ny4yIiBjeT0iNzkwLjYiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI3OTIuOSIgY3k9Ijc4Ny44IiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iODA4LjYiIGN5PSI3ODUuMyIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjgyNC4zIiBjeT0iNzgzLjAiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI4NDAuMCIgY3k9Ijc4MS4xIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iODU1LjciIGN5PSI3NzkuNiIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9Ijg3MS40IiBjeT0iNzc4LjYiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI4ODcuMSIgY3k9Ijc3OC4xIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iOTAyLjgiIGN5PSI3NzguMSIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjkxOC41IiBjeT0iNzc4LjYiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI5MzQuMiIgY3k9Ijc3OS43IiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iOTQ5LjkiIGN5PSI3ODEuMyIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9Ijk2NS42IiBjeT0iNzgzLjIiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSI5ODEuMyIgY3k9Ijc4NS42IiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iOTk3LjAiIGN5PSI3ODguMiIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjEwMTIuNyIgY3k9Ijc5MS4wIiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iMTAyOC40IiBjeT0iNzkzLjkiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSIxMDQ0LjEiIGN5PSI3OTYuOCIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjEwNTkuOCIgY3k9Ijc5OS42IiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iMTA3NS41IiBjeT0iODAyLjIiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSIxMDkxLjIiIGN5PSI4MDQuNiIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjExMDYuOSIgY3k9IjgwNi42IiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iMTEyMi42IiBjeT0iODA4LjIiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz48Y2lyY2xlIGN4PSIxMTM4LjMiIGN5PSI4MDkuMyIgcj0iMC45MCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xNiIvPjxjaXJjbGUgY3g9IjExNTQuMCIgY3k9IjgwOS45IiByPSIwLjkwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjE2Ii8+PGNpcmNsZSBjeD0iMTE2OS43IiBjeT0iODEwLjAiIHI9IjAuOTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTYiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjIxMi4wIiBjeT0iNjQ0LjYiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSIyMjcuNyIgY3k9IjY0My41IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iMjQzLjQiIGN5PSI2NDIuNSIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjI1OS4xIiBjeT0iNjQxLjYiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSIyNzQuOCIgY3k9IjY0MC44IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iMjkwLjUiIGN5PSI2NDAuMSIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjMwNi4yIiBjeT0iNjM5LjUiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSIzMjEuOSIgY3k9IjYzOC45IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iMzM3LjYiIGN5PSI2MzguNSIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjM1My4zIiBjeT0iNjM4LjMiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSIzNjkuMCIgY3k9IjYzOC4xIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iMzg0LjciIGN5PSI2MzguMCIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjQwMC40IiBjeT0iNjM4LjAiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI0MTYuMSIgY3k9IjYzOC4yIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iNDMxLjgiIGN5PSI2MzguNSIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjQ0Ny41IiBjeT0iNjM4LjgiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI0NjMuMiIgY3k9IjYzOS4zIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iNDc4LjkiIGN5PSI2MzkuOSIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjQ5NC42IiBjeT0iNjQwLjYiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI1MTAuMyIgY3k9IjY0MS40IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iNTI2LjAiIGN5PSI2NDIuMiIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjU0MS43IiBjeT0iNjQzLjIiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI1NTcuNCIgY3k9IjY0NC4zIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iNTczLjEiIGN5PSI2NDUuNCIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjU4OC44IiBjeT0iNjQ2LjciIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI2MDQuNSIgY3k9IjY0OC4wIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iNjIwLjIiIGN5PSI2NDkuMyIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjYzNS45IiBjeT0iNjUwLjgiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI2NTEuNiIgY3k9IjY1Mi4zIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iNjY3LjMiIGN5PSI2NTMuOCIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjY4My4wIiBjeT0iNjU1LjQiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI2OTguNyIgY3k9IjY1Ny4wIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iNzE0LjQiIGN5PSI2NTguNyIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjczMC4xIiBjeT0iNjYwLjMiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI3NDUuOCIgY3k9IjY2Mi4wIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iNzYxLjUiIGN5PSI2NjMuNyIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9Ijc3Ny4yIiBjeT0iNjY1LjQiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI3OTIuOSIgY3k9IjY2Ny4xIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iODA4LjYiIGN5PSI2NjguOCIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjgyNC4zIiBjeT0iNjcwLjUiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI4NDAuMCIgY3k9IjY3Mi4xIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iODU1LjciIGN5PSI2NzMuNyIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9Ijg3MS40IiBjeT0iNjc1LjMiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI4ODcuMSIgY3k9IjY3Ni44IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iOTAyLjgiIGN5PSI2NzguMiIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjkxOC41IiBjeT0iNjc5LjYiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI5MzQuMiIgY3k9IjY4MC45IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iOTQ5LjkiIGN5PSI2ODIuMiIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9Ijk2NS42IiBjeT0iNjgzLjQiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSI5ODEuMyIgY3k9IjY4NC41IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iOTk3LjAiIGN5PSI2ODUuNSIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjEwMTIuNyIgY3k9IjY4Ni40IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iMTAyOC40IiBjeT0iNjg3LjIiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSIxMDQ0LjEiIGN5PSI2ODcuOSIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjEwNTkuOCIgY3k9IjY4OC41IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iMTA3NS41IiBjeT0iNjg5LjAiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSIxMDkxLjIiIGN5PSI2ODkuNCIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjExMDYuOSIgY3k9IjY4OS43IiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iMTEyMi42IiBjeT0iNjg5LjkiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz48Y2lyY2xlIGN4PSIxMTM4LjMiIGN5PSI2OTAuMCIgcj0iMS4xMCIgZmlsbD0iIzIwQURGRiIgb3BhY2l0eT0iMC4xMiIvPjxjaXJjbGUgY3g9IjExNTQuMCIgY3k9IjY5MC4wIiByPSIxLjEwIiBmaWxsPSIjMjBBREZGIiBvcGFjaXR5PSIwLjEyIi8+PGNpcmNsZSBjeD0iMTE2OS43IiBjeT0iNjg5LjgiIHI9IjEuMTAiIGZpbGw9IiMyMEFERkYiIG9wYWNpdHk9IjAuMTIiLz4NCiAgICAgIDxwYXRoIGQ9Ik0yMTIgNzQyQzMxNiA3MTIgMzcyIDc3MiA0OTIgNzQyUzY2MCA2OTAgNzY0IDc0MlM5MzYgODAwIDEwNDAgNzYyUzExMzIgNzE2IDExNjggNzMwIiBmaWxsPSJub25lIiBzdHJva2U9IiMwODg5RkIiIHN0cm9rZS13aWR0aD0iMS4yIiBvcGFjaXR5PSIwLjIyIi8+DQogICAgICA8cGF0aCBkPSJNMjEyIDcxMEMzMzAgNjY0IDQzMCA3NDggNTUyIDcxMlM3MjAgNjU2IDg1MiA3MDZTMTA0MCA3NjIgMTE2OCA3MTYiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzA4ODlGQiIgc3Ryb2tlLXdpZHRoPSIxIiBvcGFjaXR5PSIwLjE0Ii8+DQogICAgICA8cGF0aCBkPSJNMjEyIDc3MEMzNDAgNzQ0IDQ1MiA4MDAgNTg4IDc3MlM3ODAgNzI2IDkyOCA3NzJTMTA5NiA4MTIgMTE2OCA3ODYiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzkxNjJGQiIgc3Ryb2tlLXdpZHRoPSIxLjIiIG9wYWNpdHk9IjAuMTIiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjMwMCIgY3k9IjczNiIgcj0iOSIgZmlsbD0iIzA4ODlGQiIgb3BhY2l0eT0iMC4xMCIvPg0KICAgICAgPGNpcmNsZSBjeD0iMzAwIiBjeT0iNzM2IiByPSIyLjYiIGZpbGw9IiNFRUY0RkYiIG9wYWNpdHk9IjAuNzUiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjQ4NiIgY3k9IjcxNiIgcj0iOSIgZmlsbD0iIzA4ODlGQiIgb3BhY2l0eT0iMC4xMCIvPg0KICAgICAgPGNpcmNsZSBjeD0iNDg2IiBjeT0iNzE2IiByPSIyLjYiIGZpbGw9IiNFRUY0RkYiIG9wYWNpdHk9IjAuNzUiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjY2OCIgY3k9IjY5MCIgcj0iOSIgZmlsbD0iIzA4ODlGQiIgb3BhY2l0eT0iMC4xMCIvPg0KICAgICAgPGNpcmNsZSBjeD0iNjY4IiBjeT0iNjkwIiByPSIyLjYiIGZpbGw9IiNFRUY0RkYiIG9wYWNpdHk9IjAuNzUiLz4NCiAgICAgIDxjaXJjbGUgY3g9Ijg1MiIgY3k9IjcwNCIgcj0iOSIgZmlsbD0iIzA4ODlGQiIgb3BhY2l0eT0iMC4xMCIvPg0KICAgICAgPGNpcmNsZSBjeD0iODUyIiBjeT0iNzA0IiByPSIyLjYiIGZpbGw9IiNFRUY0RkYiIG9wYWNpdHk9IjAuNzUiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjEwMzAiIGN5PSI3NDgiIHI9IjkiIGZpbGw9IiMwODg5RkIiIG9wYWNpdHk9IjAuMTAiLz4NCiAgICAgIDxjaXJjbGUgY3g9IjEwMzAiIGN5PSI3NDgiIHI9IjIuNiIgZmlsbD0iI0VFRjRGRiIgb3BhY2l0eT0iMC43NSIvPg0KICAgICAgPGNpcmNsZSBjeD0iMTE0MiIgY3k9IjcyNiIgcj0iOSIgZmlsbD0iIzA4ODlGQiIgb3BhY2l0eT0iMC4xMCIvPg0KICAgICAgPGNpcmNsZSBjeD0iMTE0MiIgY3k9IjcyNiIgcj0iMi42IiBmaWxsPSIjRUVGNEZGIiBvcGFjaXR5PSIwLjc1Ii8+DQogICAgPC9nPg0KPC9nPg0KPC9zdmc+DQo='

const XY_CHROME_CSS = `
/* ===== 分片 1/3：几何 + 主题色别名 ===== */
:root {
  --xy-chrome-bottom-h: ${XY_CHROME_BOTTOM_H}px;
  /* 40：高于应用内容（panes z-10/20；in-pane sticky/rail 最高 z-40 —— 本带挂在 body 末位，
     同 z 由 DOM 顺序胜出），低于官方全部弹层梯队。官方 z 实况（styles.css:282-296 令牌 +
     字面量）：over-modal 200/210、switcher 220、modal 120-140、titlebar 控件簇 70；
     overlay 卡片 / sheet / find-bar / context-menu / dropdown-menu / popover 一律字面量 50
     （全 app 20 处）。取 60 会盖住这 20 处弹层（实测：命令中心卡片顶部的关闭按钮
     elementFromPoint 命中顶带内的元素而非按钮）；取 40 后弹层重新压在本带之上，
     而本带仍压住应用内容与原生标题栏条。 */
  --xy-chrome-z: 40;
  --xy-chrome-bg: var(--ui-bg-chrome);
  --xy-chrome-panel: var(--ui-bg-editor);
  --xy-chrome-sidebar: var(--ui-bg-sidebar);
  --xy-chrome-text: var(--ui-text-primary);
  --xy-chrome-text-faint: var(--ui-text-quaternary);
  --xy-chrome-stroke: var(--ui-stroke-secondary);
  --xy-chrome-stroke-strong: var(--ui-stroke-primary);
  --xy-chrome-accent: var(--ui-accent);
  --xy-chrome-accent-2: var(--ui-accent-secondary);
  /* ── 设计稿字面量（BD定制 · 深蓝主题 V3 / worktree global-chrome.tsx）────────
     chrome 三块在设计稿里就是自绘结构，这些取值没有「主题变量」形态（母版直接写
     Tailwind 任意值：31 处 hex/rgba）。集中在此一处、与 f2 皮肤同源；主题化表面
     （正文 / 侧栏 / Composer）仍全部走 --ui-* 变量（f2 XY_SKIN_CSS）。 */
  --xy-design-band-line: #244661;
  --xy-design-nav-text: #B9D8FB;
  --xy-design-nav-accent: #0889FB;
  --xy-design-nav-hover-bg: color-mix(in srgb, #0889FB 10%, transparent);
  --xy-design-nav-active-bg: color-mix(in srgb, #063255 85%, transparent);
  --xy-design-input-bg: #041522;
  --xy-design-muted: #7E98B6;
  /* 正本三档不透明文字色（text-1/2/3，对比度 15.95 / 13.17 / 6.46:1）+ 选中行两个特例。
     上游把同一基色拆成 8 档 alpha 且三档不过 AA，f2 皮肤收敛回这组命名色。 */
  --xy-design-text-1: #DCEAFF;
  --xy-design-text-2: #B9D8FB;
  --xy-design-text-3: #7E98B6;
  --xy-design-text-selected: #F2F5FF;
  --xy-design-text-selected-dim: #9DB7D9;
  /* 正本字号刻度：次级标签 10.5（出现 208 处）、辅助/底栏 11 */
  --xy-design-fs-label: 10.5px;
  --xy-design-fs-small: 11px;
  --xy-design-wordmark: #EEF4FF;
  --xy-design-status-bg: #020E19;
  --xy-design-ok: #00DBA9;
  --xy-design-warn: #FFBD4B;
  --xy-design-bad: #EF4444;
  --xy-design-bad-line: #EF4444;
  --xy-design-new: #20ADFF;
  --xy-design-chip-line: #2B506F;
  --xy-design-warn-soft: color-mix(in srgb, #FFBD4B 15%, transparent);
  --xy-design-warn-soft-hover: color-mix(in srgb, #FFBD4B 25%, transparent);
  /* 对话区「执行中」扫光色（tw-shimmer 的 --shimmer-color）。上游不设该变量时扫光带由
     currentColor 派生（暗色分支 oklch(from currentColor max(0.8, calc(l + 0.4)) c h / …)），
     偏灰白；此处收敛到正本亮青，与背景装饰/极光同族（见下方“扫光色”规则段）。 */
  --xy-design-shimmer: var(--xy-design-new);
  --xy-chrome-left: 240px;
}

/* ===== 对话区「执行中」扫光色 =====
   机制：tw-shimmer 的 @utility shimmer 里 --_fg: var(--shimmer-color, <currentColor 派生>)、
   --_bg: currentColor —— 扫光带走 --shimmer-color（它既不是 currentColor 也不是主题变量）。
   应用自身从不设置它（apps/desktop/src 内 --shimmer-color 命中 0），故插件在 html 上设一次
   即被全部 .shimmer 继承。非 @layer 规则，胜过包内 @layer utilities 的 .shimmer，无需 !important。
   命中的都是会话区“在执行”提示：thread/status.tsx:55,137（等待/进度提示）、
   thread/message-parts.tsx:308（思考标签）、tool/fallback.tsx:329,859（工具行标题/工具组摘要）、
   tool/delegate.tsx:120（子代理 ticker）。
   两个例外自带同名声明，不受本规则影响（元素自身声明优先于继承）：
   app/pet-generate/components/draft-grid.tsx:43、components/pet/pet-egg-hatch.tsx:52 的
   .shimmer-color-primary { --shimmer-color: var(--dt-primary) }。 */
html[data-xy-chrome='on'] {
  --shimmer-color: var(--xy-design-shimmer);
}

/* ===== 「思考中」行右侧实时计时的颜色 =====
   被点名的元素：message-parts.tsx:303-304 trailing 槽里的那枚 ActivityTimerText。
   注意它声明的是 text-midground/55，但那是**死类**：cn() = twMerge(clsx(…))，
   activity-timer-text.tsx:23 的 'text-midground/55' 被 message-parts.tsx:304 传入的
   SCAFFOLD_META_CLASS '… text-(--conversation-scaffold-meta)' 按同族工具类吃掉
   （tailwind-merge 3.6.0 实测：twMerge(声明串, 覆盖串) 结果里不含 text-midground）。
   ⇒ 真 DOM 上这枚计时带的是 --conversation-scaffold-meta，跟工具行耗时同一个类。
   本插件第一版就是照组件源码写 [class*='text-midground']，结果全应用命中 0 个、静默不生效。
   现在分两层锚：思考行自身的 data-slot（message-parts.tsx:295）+ 该行内的 scaffold-meta 类，
   再用 :not([data-slot='timeline-timestamp']) 把同一 trailing 槽里的时间戳排除在外
   （时间戳不是本次点名的那一个；要连它一起改，删掉这个 :not() 即可）。
   非 @layer 规则，胜过包内 @layer utilities 的该类，无需 !important。 */
html[data-xy-chrome='on'] [data-slot='aui_thinking-disclosure'] [class*='text-(--conversation-scaffold-meta)']:not([data-slot='timeline-timestamp']) {
  color: var(--xy-design-new);
}

/* ===== 激活态：为底栏让位（顶带已撤下，不再留顶部偏移）=====
   官方外壳 [data-contrib-shell] 是 h-screen flex-col，全局 *{box-sizing:border-box}
   ⇒ 加 padding 只压缩内容区高度，不会把内容推出窗口；官方 titlebar 控件簇是
   position:fixed，不受外壳 padding 影响。
   若实测该属性名不对，改用下方注释里的 fallback（互斥，切勿同时启用，会双重偏移）。 */
html[data-xy-chrome='on'] body {
  background-color: var(--xy-chrome-bg);
}
html[data-xy-chrome='on'] [data-contrib-shell] {
  /* 不再预定 --xy-chrome-offset：顶带撤下后它是孤儿，且会把原生标题栏整行下推 34px，
     使 y=0..34 成为无拖动带的死区（用户实测「窗口拖不动」，2026-09-29 修复）。 */
  padding-bottom: var(--xy-chrome-bottom-h) !important;
  background-color: transparent !important;
}

/* ===== 输入框贴住底栏：归零官方为原生状态栏预留的下沉 =====
   官方 composer-dock 底部留 --composer-shell-pad-block-end（2.5rem = 40px）给原生状态栏；
   本插件隐藏原生状态栏、自绘底栏占的是外壳 padding-bottom 那条带，这 40px 就变成输入框
   与底栏之间的空档。归零它 —— 改**变量**而不是只改 dock 的 padding：该变量同时参与
   --composer-measured-height（消息区底部留白 / 线程视口高度），改变量两条链路才同步。
   实测（窗口 1793×1070）：改前 composer-surface 底边 994、底栏顶 1034（40px 空档）；
   改后输入框底边 1034 = 底栏顶边，贴合。 */
html[data-xy-chrome='on'] {
  --composer-shell-pad-block-end: 0px !important;
}

/* ===== 侧栏底部行并入底栏水平带 =====
   官方侧栏底部这行（[data-slot='profile-rail'] 的容器：配置档案头像 + 新建/导入/管理/网关）
   在 [data-contrib-shell] 的 padding-bottom 之上，实测底边 y=1034 —— 正好停在底栏顶沿，
   比底栏低半行。设计稿里这行与底栏同属一条 36px 底带（global-chrome.tsx 底栏
   left: SIDEBAR_W，左段本来就是留给侧栏的），故把它整行 fixed 到窗口左下：
   宽度取 --xy-chrome-left（与底栏左缘同值 ⇒ 侧栏拖宽/折叠自动跟随，折叠成 0 时
   靠 overflow:hidden 收起），高度取 --xy-chrome-bottom-h。
   实测（窗口 1793×1070）：容器 [0,1034,260,1070] 与 .xyc-status [260,1034,1793,1070]
   同带同高，rail 内 5 个按钮 elementFromPoint 全部命中自身。 */

html[data-xy-chrome='on'] [data-xy-sidebar-rail] {
  position: fixed !important;
  left: 0 !important;
  bottom: 0 !important;
  width: var(--xy-chrome-left, 240px) !important;
  height: var(--xy-chrome-bottom-h) !important;
  box-sizing: border-box !important;
  padding: 6px 10px !important;
  overflow: hidden !important;
  z-index: var(--xy-chrome-z, 40) !important;
}
/* fallback（无 [data-contrib-shell] 时逐块让位）：
html[data-xy-chrome='on'] [data-tour='sessions-sidebar'] {
  padding-top: var(--xy-chrome-offset) !important;
  padding-bottom: var(--xy-chrome-bottom-h) !important;
}
html[data-xy-chrome='on'] [data-slot='aui_thread-viewport'] {
  padding-top: var(--xy-chrome-offset) !important;
  padding-bottom: var(--xy-chrome-bottom-h) !important;
}
*/

/* ===== 隐藏官方 chrome：原生底栏 =====
   侧栏导航组（[data-slot='sidebar-content'] > [data-slot='sidebar-group']:first-of-type，
   5 项：新建会话 / 技能与工具 / 消息平台 / 产物 / 定时任务）自本轮起**放回原版、不再隐藏** ——
   它本来就是侧栏左上的纵向导航；插件自绘的那套已整段撤下。 */
html[data-xy-chrome='on'] [data-slot='statusbar'] {
  display: none !important;
}

/* ===== overlay 视图：撤下本插件 chrome（顶带 + 底栏 + 让位 padding）=====
   官方 8 个 overlay 视图（routes.ts:163-172 OVERLAY_VIEWS）渲染成一张
   fixed inset-0 z-50 的模态卡片（overlays/overlay-view.tsx:77）。--xy-chrome-z 已降到 40，
   弹层本就在本带之上；此处再撤下是照抄官方做法 —— routes.ts:160-186
   hidesFixedTitlebarClusters 在 overlay 打开时撤下自己的 z-70 控件簇，理由是它们 z 高于
   卡片；插件同理不该在卡片边缘露出自己的 chrome（卡片是半透明的，本带会透出来）。
   撤下后卡片顶栏（拖拽带 + 关闭按钮，overlay-view.tsx:116-129）与官方控件簇均可见可用 ——
   实测：卡片打开时「关闭命令中心」按钮 [1708,57] 命中自身 codicon-close，点击即关闭。
   无 overlay 时 data-xy-overlay='off'，样式零影响。 */
html[data-xy-overlay='on'] .xyc-status,
html[data-xy-overlay='on'] [data-xy-sidebar-rail] {
  display: none !important;
}
html[data-xy-overlay='on'][data-xy-chrome='on'] [data-contrib-shell] {
  padding-bottom: 0 !important;
}
/* 2026-09-29 用户裁定：恢复原版顶带与侧栏结构，只改颜色与背景。
   原先这里用 JS 打标记隐藏侧栏「会话 | 机器人」页签行、并把它的列容器压成 0 高
   （[data-xy-sidebar-header='hide'] / [data-xy-sidebar-column='collapse']），
   副作用是侧栏列表整体上移进 34px 原生标题栏带 —— 拖动带被列表项盖住、顶带也不再是原版形态。
   两条规则与打标记的 JS（原 XY_CHROME_tagSidebarHeader）已整条删除。 */
/* ── 原版侧栏导航美化（用户 2026-09-29 选 1 / 2 / 4）─────────────────────────
   1. 行高 28px → 24px：上下 padding 各压到 4px，16px 图标居中不被裁；
   2. 选中态：官方浅色药丸底**保留**，另加左侧 2px 强调色竖条（--xy-design-nav-accent）；
   3. 导航组与「搜索会话」行之间加 1px 分隔线（--xy-design-band-line）。
   选中标记挂点说明：官方给选中按钮加的是工具类 bg-(--ui-control-active-background)（Tailwind 任意值），
   实测 data-active 恒为 false（不是可用锚点），故按 class 子串匹配 —— 官方若改名，
   本段只是不再命中，不会破坏布局。 */
html[data-xy-chrome='on'] [data-slot='sidebar-content'] > [data-slot='sidebar-group']:first-of-type {
  border-bottom: 1px solid var(--xy-design-band-line);
  padding-bottom: 0.375rem;
}

html[data-xy-chrome='on'] [data-slot='sidebar-menu-item'] {
  height: 1.5rem !important;
  min-height: 1.5rem !important;
}

/* 内层按钮撑满行；上下 padding 各 4px ⇒ 16px 图标在 24px 行内垂直居中。
   注意不要在同一段给「行」也设 padding：实测那样会把按钮顶下去 4px、行内溢出。 */
html[data-xy-chrome='on'] [data-slot='sidebar-menu-item'] button {
  height: 1.5rem !important;
  min-height: 1.5rem !important;
  padding-top: 0.25rem !important;
  padding-bottom: 0.25rem !important;
}

html[data-xy-chrome='on'] [data-slot='sidebar-menu-item'] button[class*="bg-(--ui-control-active-background)"] {
  box-shadow: inset 2px 0 0 0 var(--xy-design-nav-accent);
}

/* 侧栏「搜索会话」：官方把整条面板头声明成 drag（实测 drag 区 26,74 234x34），输入框又带
   [field-sizing:content]、空值仅 45px —— 实测点 x=45/68 能落焦、x>=90 全落到 body（被 drag 吞）。
   表现就是「看着有搜索框，点进去打不了字」。这里只做两件事：输入框撑满所在行 + 整行 no-drag。 */
html[data-xy-chrome='on'] [data-xy-chrome-search-row] {
  width: 100%;
  -webkit-app-region: no-drag;
}

html[data-xy-chrome='on'] [data-xy-chrome-search-row] input {
  field-sizing: fixed;
  flex: 1 1 auto;
  width: 100%;
  -webkit-app-region: no-drag;
}

/* 原生侧栏开关按钮（x14..38 / y5..29，含未读角标）已按用户 2026-09-29 决定**恢复显示**：
   顶带撤下后它与原生标题栏同层，不再被任何插件元素遮住，故隐藏规则整条删除。
   左簇里的其它工具（页面自带，如看板切换器）本就不受影响。 */

/* ===== 背景层：fixed inset-0、pointer-events:none、最低 z =====
   z-index:-1：压在 body 底色之上、一切内容之下（官方 body 背景为 rgba(0,0,0,0)，
   故不会遮住本层），同时保证绝不拦截点击。
   图像 = 设计稿《整体应用背景图》v1.0 的 bg-app.svg：设计稿要求以 fixed inset-0
   全屏层承载、全应用共用（含看板）、背景不随内容滚动；并把「基底 + 38px 网格 +
   四角光晕 + 边缘压暗」全部画进资产本身，因此本层不再另叠渐变——按设计稿
   「只用 4 色、中心 70% 区不许有 α>0.06 的新增装饰、禁止在背景层叠加
   mix-blend-mode / backdrop-filter」的口径，这里只承载这一张图。
   资产内置 preserveAspectRatio="xMidYMid slice"，配合 cover 即设计稿的居中裁切；
   宽高比 ≥2.13（21:9 等）时设计稿建议改 background-position: center 42%。 */
.xyc-backdrop {
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background-image: url('${XY_CHROME_BG_APP_URI}');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* 对话流背景装饰：画在官方滚动容器的 padding box 上（背景默认不随内容滚动，
   与设计稿「不随内容滚动」一致）；100% 100% 让整幅构图（含底部波形）都在。 */
html[data-xy-chrome='on'] [data-slot='aui_thread-viewport'] {
  background-image: url("${XY_CHROME_CHAT_AURORA_URI}");
  background-repeat: no-repeat;
  background-position: center center;
  background-size: 100% 100%;
}

/* 看板页背景装饰（用户 2026-09-29 定）：复用会话页那张 aurora，同一 data URI，不新增资产。
   依据：整应用背景 bg-app.svg 本就覆盖看板页 —— 背景层刷红的实测里，看板页纯红占比 53.79%
   （会话页 10.84%），说明该页没有被任何不透明层盖住；缺的只是这层装饰。
   显隐由 JS 按路由写 html 属性 data-xy-view 控制（路径以 /kanban 开头 ⇒ kanban）。
   落点对齐正文区：左起侧栏右缘、底让出状态条，与会话页装饰的口径一致。 */
.xyc-view-deco {
  position: absolute;
  left: var(--xy-chrome-left, 240px);
  right: 0;
  top: 0;
  bottom: var(--xy-chrome-bottom-h);
  pointer-events: none;
  background-image: url("${XY_CHROME_CHAT_AURORA_URI}");
  background-repeat: no-repeat;
  background-position: center center;
  background-size: cover;
  display: none;
}

html[data-xy-chrome='on'][data-xy-view='kanban'] .xyc-view-deco {
  display: block;
}

/* 让位（否则本层整层看不见）：html 与 body 都带不透明底色时，body 的背景不再上交给
   画布，而是实打实画在 z-index:-1 之上 —— 实测把本层刷成纯红、截图里红色像素占比
   0.00%，即本层一直被盖住。资产 bg-app.svg 本身已含设计稿「基底 #000D17 + 纵向微渐变
   #020E19→#000D17@55%→#010B14 + 边缘压暗」，故把 body 的底让给画布层（画布底色由上游对 html 的
   声明与资产自带的满铺基底共同兜底 —— 本插件不再重复声明 html 底色：实测该声明压不过
   上游同 important 的 html 底色，留着只是误导），这也让设计稿的层级口径成立：背景层在最下、内容在其上。
   这里必须 !important，两条理由都实测过：① f2 的主题段用 html, body 选择器 + background-image … !important 覆盖上游玻璃层
   写进 html/body 的行内底色，非 important 的规则压不过它；② 上游那层是行内样式，行内优先级高于任何
   普通规则。选择器带 [data-xy-chrome='on']，特异性本就高于 f2 的纯 body，两条同
   important 时本规则胜出；插件关闭（chrome 关）时本规则不注入，f2 的底色仍在。 */
html[data-xy-chrome='on'] body {
  background-color: transparent !important;
  background-image: none !important;
}

/* ===== 底部状态条（设计稿 global-chrome.tsx:412：h-9 / bg #020E19 / px-7 / gap-4 /
   text-[0.7rem] / left: 侧栏宽 —— 侧栏左侧那块留给 profile-rail）===== */
.xyc-status {
  position: fixed;
  left: var(--xy-chrome-left, 240px);
  right: 0;
  bottom: 0;
  height: var(--xy-chrome-bottom-h);
  z-index: var(--xy-chrome-z);
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0 1.75rem;
  box-sizing: border-box;
  background: var(--xy-design-status-bg);
  color: var(--xy-design-nav-text);
  font-family: var(--dt-font-sans, inherit);
  /* 11px = --xy-design-fs-small。这里必须与 f2 皮肤 ⑭ 段的取值一致：
     两处同权重、只靠样式表次序决胜负，热重载后次序可变 ⇒ 写同一值即与次序无关。 */
  font-size: var(--xy-design-fs-small);
  line-height: 1;
  -webkit-app-region: no-drag;
}
.xyc-status-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 1rem;
  min-width: 0;
}
.xyc-status-item {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  white-space: nowrap;
  flex: none;
}
/* 中性计数默认 400（与 f2 皮肤 ⑭ 段同值，避免依赖样式表次序）；
   强调/警告段 600 见下方提权规则。 */
.xyc-status-value {
  font-weight: 400;
  color: inherit;
}
/* 上下文用量的「数据部分」字色 = #00E2A9（用户 2026-09-29 指定；标签「上下文」不在此列，
   它继承 .xyc-status 的 --xy-design-nav-text，只改本类即只影响值）。值复用 f3 的 contextLabel 口径。 */
.xyc-status-context {
  color: #00E2A9;
}

/* 强调/警告段 600：f2 皮肤 ⑭ 段也声明了 600，但同段的 .xyc-status-value{400} 与它同权重，
   胜负只看样式表次序（热重载会翻转 —— 2026-09-29 实测强调段掉回 400）。
   这里提到 0,2,0，使结果与次序无关。 */
.xyc-status .xyc-status-accent,
.xyc-status .xyc-status-warn {
  font-weight: 600;
}
.xyc-status-accent {
  color: var(--xy-design-nav-accent);
}
.xyc-status-warn {
  color: var(--xy-design-warn);
}
.xyc-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--xy-design-ok);
  flex: none;
}
.xyc-dot-off {
  background: var(--xy-design-bad);
}
.xyc-dot-sm {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--xy-design-ok);
  flex: none;
}
/* 版本角标点：设计稿 457 行无更新时是 #7E98B6（有更新才转 #20ADFF）。 */
.xyc-dot-ver {
  background: var(--xy-design-muted);
}
.xyc-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  height: 1.5rem;
  padding: 0 0.625rem;
  border: 1px solid var(--xy-design-nav-accent);
  border-radius: 0.75rem;
  background: var(--xy-design-nav-hover-bg);
  color: var(--xy-design-ok);
  flex: none;
}
.xyc-pill[data-off] {
  border-color: var(--xy-design-bad-line);
  background: color-mix(in srgb, #FF3B5C 12%, transparent);
  color: var(--xy-design-bad);
}
/* 当前目录 pill：文字色取「审批模式」族那一档（.xyc-chip-warn 的 color: var(--xy-design-warn)，
   用户 2026-09-30 要求）。只改字色 —— 描边（--xy-design-nav-accent）与底色
   （--xy-design-nav-hover-bg）保持原样。规则置于 .xyc-pill 之后，同权重靠后者胜出。 */
.xyc-pill-cwd {
  color: var(--xy-design-warn);
}
.xyc-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  height: 1.5rem;
  padding: 0 0.5rem;
  border: 1px solid var(--xy-design-chip-line);
  border-radius: 0.375rem;
  background: var(--xy-design-input-bg);
  color: var(--xy-design-nav-text);
  font: inherit;
  font-size: 0.7rem;
  cursor: pointer;
  flex: none;
  transition: border-color 0.15s ease, color 0.15s ease;
}
.xyc-chip:hover {
  border-color: var(--xy-design-nav-accent);
  color: var(--xy-design-wordmark);
}
/* 审批模式 chip（设计稿 430 行：琥珀描边 + 15% 琥珀底 + 琥珀字 + 半粗）。 */
.xyc-chip-warn {
  gap: 0.25rem;
  border-color: var(--xy-design-warn);
  background: var(--xy-design-warn-soft);
  color: var(--xy-design-warn);
  font-weight: 600;
}
.xyc-chip-warn:hover {
  border-color: var(--xy-design-warn);
  background: var(--xy-design-warn-soft-hover);
  color: var(--xy-design-warn);
}
/* ===== 审批模式下拉菜单（可写）=====
   官方式样：标题 + 分隔线 + 三项（手动/智能/关闭），每项「标签 + 一行描述」，
   宽 288px（官方 statusbar 项 menuClassName: 'w-72'）；命中项左侧打勾。
   位置由 JS 固定到 chip 上方（右对齐），不随本栏布局参与。  */
.xyc-approval-menu {
  position: fixed;
  z-index: calc(var(--xy-chrome-z) + 1);
  min-width: 288px;
  padding: 0.25rem;
  border: 1px solid var(--xy-design-chip-line);
  border-radius: 0.5rem;
  background: var(--xy-design-input-bg);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45);
  color: var(--xy-design-nav-text);
  font: inherit;
  font-size: 0.75rem;
}
.xyc-approval-title {
  padding: 0.375rem 0.5rem;
  color: var(--xy-design-muted);
  font-size: 0.6875rem;
  letter-spacing: 0.02em;
}
.xyc-approval-sep {
  height: 1px;
  margin: 0.125rem 0.25rem 0.25rem;
  background: var(--xy-design-chip-line);
  opacity: 0.6;
}
.xyc-approval-item {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  width: 100%;
  padding: 0.375rem 0.5rem;
  border: 0;
  border-radius: 0.375rem;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.xyc-approval-item:hover {
  background: var(--xy-design-nav-hover-bg);
}
.xyc-approval-item[data-current='true'] {
  background: var(--xy-design-nav-active-bg);
}
.xyc-approval-tick {
  width: 1rem;
  margin-top: 0.0625rem;
  color: var(--xy-design-warn);
  opacity: 0;
}
.xyc-approval-item[data-current='true'] .xyc-approval-tick {
  opacity: 1;
}
.xyc-approval-col {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.125rem;
}
.xyc-approval-label {
  color: var(--xy-design-wordmark);
}
.xyc-approval-desc {
  color: var(--xy-design-muted);
  font-size: 0.6875rem;
  line-height: 1.35;
}
`

/* ---------------------------------------------------------------- 基础工具 */

const XY_CHROME_warn = err => {
  try {
    if (typeof console !== 'undefined' && console.warn) {
      console.warn('[xy-chrome]', err)
    }
  } catch (ignored) {
    /* 仅日志，绝不抛出 */
  }
}

/** 安全执行：皮肤层不该把宿主搞崩，出错只记日志并回退。 */
const XY_CHROME_try = (fn, fallback) => {
  try {
    const value = fn()

    return value === undefined ? fallback : value
  } catch (err) {
    XY_CHROME_warn(err)

    return fallback
  }
}

const XY_CHROME_el = (tag, className, text) => {
  const node = document.createElement(tag)

  if (className) {
    node.className = className
  }

  if (text !== undefined && text !== null) {
    node.textContent = String(text)
  }

  return node
}

const XY_CHROME_svgEl = (tag, attrs) => {
  const node = document.createElementNS(XY_CHROME_SVG_NS, tag)

  if (attrs) {
    Object.keys(attrs).forEach(key => node.setAttribute(key, attrs[key]))
  }

  return node
}

/** codicon 字形（官方已全局载入 @vscode/codicons）。 */
const XY_CHROME_icon = (name, className) => {
  const node = XY_CHROME_el('i', className ? 'codicon codicon-' + name + ' ' + className : 'codicon codicon-' + name)

  node.setAttribute('aria-hidden', 'true')

  return node
}

const XY_CHROME_setText = (node, value) => {
  if (!node) {
    return
  }

  const next = value === undefined || value === null || value === '' ? '—' : String(value)

  if (node.textContent !== next) {
    node.textContent = next
  }
}

const XY_CHROME_fmt = (value, fallback) => {
  const num = Number(value)

  if (!Number.isFinite(num)) {
    return fallback === undefined ? '—' : fallback
  }

  return num.toLocaleString('zh-CN')
}

const XY_CHROME_hostRef = () => (typeof host === 'undefined' || !host ? null : host)

/**
 * 读 atom：host.state.* 是 nanostore 只读原子。
 * 依据：sdk/index.ts:13-14「nanostore atoms; `.get()` or subscribe」；状态表 666-712；
 * 文档 907-909「readonly — read with .get()」；文档 1178「get() / subscribe() / listen()」。
 */
const XY_CHROME_atomValue = (atom, fallback) =>
  XY_CHROME_try(() => {
    if (!atom) {
      return fallback
    }

    const value = typeof atom.get === 'function' ? atom.get() : atom.value

    return value === undefined || value === null || value === '' ? fallback : value
  }, fallback)

/** 底栏显示输入框实际展示的模型；视图不明确时隐藏，不能回退到其他会话的全局模型。 */
const XY_CHROME_visibleComposerModel = () => {
  const surfaces = Array.from(document.querySelectorAll('[data-slot="composer-surface"]')).filter(el =>
    el.isConnected && el.getClientRects().length > 0 && !el.closest('[aria-hidden="true"], [hidden]')
  )
  if (surfaces.length !== 1) return null
  const pill = surfaces[0].querySelector('[data-tour="model-pill"]')
  const label = pill && pill.querySelector('span.truncate')
  const value = label && label.textContent ? label.textContent.trim() : ''
  return value || null
}

/**
 * 订阅 atom → 返回退订函数。
 * 主路 subscribe()（文档 1178 / 1220 的 `ctx.onDispose(atom.subscribe(fn))`），
 * 兜底 listen()（老版 nanostores），两者都不支持则退化为一次性读取（返回空函数）。
 */
const XY_CHROME_watchAtom = (atom, onChange) => {
  if (!atom) {
    return () => undefined
  }

  const runner = () => XY_CHROME_try(onChange, undefined)
  const off =
    typeof atom.subscribe === 'function'
      ? XY_CHROME_try(() => atom.subscribe(runner), null)
      : typeof atom.listen === 'function'
        ? XY_CHROME_try(() => atom.listen(runner), null)
        : null

  return typeof off === 'function' ? off : () => undefined
}

/**
 * 事件监听：有 ctx 就用 ctx.addEventListener（文档 193-196，随插件生命周期回收），
 * 否则裸监听 + 自行解绑（文档 1556-1560 警告裸监听不受宿主追踪，必须自己clean）。
 */
const XY_CHROME_listen = (ctx, target, type, handler) => {
  if (!target || typeof target.addEventListener !== 'function') {
    return () => undefined
  }

  if (ctx && typeof ctx.addEventListener === 'function') {
    const off = XY_CHROME_try(() => ctx.addEventListener(target, type, handler), null)

    if (typeof off === 'function') {
      return off
    }
  }

  return XY_CHROME_try(() => {
    target.addEventListener(type, handler)

    return () => target.removeEventListener(type, handler)
  }, () => undefined)
}

/** 当前路由：官方是 hash 路由（sdk/index.ts:749-753 host.navigate 直接写 window.location.hash）。 */
const XY_CHROME_currentPath = () => {
  const raw = XY_CHROME_try(() => String(window.location.hash || ''), '')

  return raw.replace(/^#/, '') || '/'
}

const XY_CHROME_onPathChange = (ctx, handler) => {
  const run = () => {
    XY_CHROME_syncOverlay()
    XY_CHROME_syncView()
    XY_CHROME_try(handler, undefined)
  }
  const offs = [XY_CHROME_listen(ctx, window, 'hashchange', run), XY_CHROME_listen(ctx, window, 'popstate', run)]

  return () => offs.forEach(off => XY_CHROME_try(off, undefined))
}

/** 官方 overlay 视图集（逐字对齐源码 apps/desktop/src/app/routes.ts:163-172 OVERLAY_VIEWS）。 */
const XY_CHROME_OVERLAY_PATHS = [
  '/agents',
  '/command-center',
  '/cron',
  '/profiles',
  '/session-import',
  '/settings',
  '/starmap',
  '/webhooks'
]

const XY_CHROME_isOverlayPath = path =>
  XY_CHROME_OVERLAY_PATHS.some(known => path === known || path.indexOf(known + '/') === 0 || path.indexOf(known + '?') === 0)

/** 把「当前是否 overlay 视图」写到 <html data-xy-overlay>，CSS 据此撤下顶带/底栏
    （原因见 CSS 段 overlays 注释）。读取实时 hash，故不依赖路由事件 —— 底栏的时钟
    刷新与侧栏 bind()（body MutationObserver）都会顺带校正。值不变则不写属性。 */
const XY_CHROME_syncOverlay = () => {
  const next = XY_CHROME_isOverlayPath(XY_CHROME_currentPath()) ? 'on' : 'off'

  if (XY_CHROME_state.overlay !== next) {
    XY_CHROME_state.overlay = next
    XY_CHROME_try(() => document.documentElement.setAttribute('data-xy-overlay', next), undefined)
  }
}

/** 视图态：按路由开正文区的装饰层（当前只有看板页用；路径以 /kanban 开头 ⇒ 'kanban'）。
    装饰层是背景层的子节点，显隐由 CSS 按 html[data-xy-view] 控制。 */
const XY_CHROME_syncView = () => {
  const next = XY_CHROME_currentPath().indexOf('/kanban') === 0 ? 'kanban' : 'off'

  if (XY_CHROME_state.view !== next) {
    XY_CHROME_state.view = next
    XY_CHROME_try(() => document.documentElement.setAttribute('data-xy-view', next), undefined)
  }
}

/**
 * 导航统一走 SDK：host.navigate(path)
 * 依据：文档 942「host.navigate('/route') // hash-route navigation」；
 * 源码 sdk/index.ts:749-761 签名 (path: string) => void，写 hash 并 syncWorkspaceRoute。
 * 「新建会话」优先 host.newChat()：文档 946；源码 sdk/index.ts:1368
 * 签名 (profile?: null|string|PluginProfileRoute, options = {}) => void。
 */
/* ---------------------------------------------------------------- 注入 / 清理 */

const XY_CHROME_state = {
  installId: 0,
  refs: 0,
  slots: { backdrop: null, status: null },
  /** 侧栏宽跟踪的清理函数（被三块共享，引用计数到 0 时释放）。 */
  leftCleanup: null,
  /** 右栏「默认隐藏」一次性任务的状态（见 XY_CHROME_rightDefault）。 */
  rightDone: false,
  rightStartedAt: 0,
  rightBusy: false,
  rightClicks: 0,
  /** 当前 <html data-xy-overlay> 值（'on' / 'off' / '' 未写），避免重复写属性。 */
  overlay: '',
  /** 当前 <html data-xy-view> 值（'kanban' / 'off' / '' 未写）。 */
  view: ''
}

/** 幂等挂载共享资源（<style id="xy-chrome-css"> + html[data-xy-chrome]），引用计数释放。 */
const XY_CHROME_mountShared = () => {
  XY_CHROME_try(() => document.documentElement.setAttribute(XY_CHROME_ATTR, 'on'), undefined)
  XY_CHROME_try(() => {
    if (!document.getElementById(XY_CHROME_STYLE_ID)) {
      const style = XY_CHROME_el('style')

      style.id = XY_CHROME_STYLE_ID
      style.textContent = XY_CHROME_CSS
      document.head.appendChild(style)
    }
  }, undefined)

  XY_CHROME_state.refs += 1

  if (XY_CHROME_state.refs === 1) {
    XY_CHROME_state.leftCleanup = XY_CHROME_try(() => XY_CHROME_trackLeft(), null)
  }

  let released = false

  return () => {
    if (released) {
      return
    }

    released = true
    XY_CHROME_state.refs = Math.max(0, XY_CHROME_state.refs - 1)

    if (XY_CHROME_state.refs > 0) {
      return
    }

    XY_CHROME_stopTrackLeft()
    XY_CHROME_removeShared()
  }
}

/* ───────────────────────────── 侧栏宽跟踪（--xy-chrome-left）───────────────
   设计稿把底栏左缘绑在侧栏宽上（global-chrome.tsx 底栏 `left: SIDEBAR_W = 240`）。
   官方侧栏可折叠、可拖宽，写死 240 会在折叠后留出一块空洞，故按侧栏实测右缘写入
   --xy-chrome-left；读不到侧栏时回落到设计稿常量。CSS 侧消费点：.xyc-status。 */
/* ── 官方状态栏「版本」项（2026-09-29 用户登记：底栏版本要与原版一致）──────
   插件把官方状态栏整条 display:none 了，但它的节点仍在 DOM 里，官方那条版本项
   是 <button><span.truncate>v0.21.5+4534 (+2)</span></button>（实测）。插件没有
   updates store 的写入口，故文字直接镜像是它、点击直接派发它的 click ——
   行为与文字都走官方同一条路径，不做二次实现。 */
const XY_CHROME_officialVersionNode = () =>
  XY_CHROME_try(() => {
    const bar = document.querySelector("[data-slot='statusbar']")

    if (!bar) {
      return null
    }

    const spans = bar.querySelectorAll('span.truncate')

    for (let i = 0; i < spans.length; i += 1) {
      if (/^v?\d+\.\d+/.test(String(spans[i].textContent || '').trim())) {
        return spans[i].closest('button') || spans[i]
      }
    }

    return null
  }, null)

/** 版本 chip 文字/提示与官方对齐；取不到官方节点时才回落后端版本号。
    官方那条的文字是异步填的（appVersion 后到，实测首屏后数秒才有），
    故带一个「有界重试阶梯」：读到即停，最多 3 次一次性 timeout（无常驻定时器，符合 SDK 约束）。 */
const XY_CHROME_VERSION_RETRY_MS = [2500, 7000, 15000]

const XY_CHROME_syncOfficialVersion = (textEl, chip, snapshot, attempt) => {
  const official = XY_CHROME_officialVersionNode()
  const officialText = official ? String(official.textContent || '').trim().replace(/\s+/g, ' ') : ''
  const backend = snapshot && snapshot.version ? 'v' + String(snapshot.version) : null

  XY_CHROME_setText(textEl, officialText || backend)

  if (chip) {
    chip.title = officialText
      ? 'Hermes ' + officialText + ' / 检查更新'
      : 'Hermes ' +
        String((snapshot && snapshot.version) || '?') +
        (snapshot && snapshot.release_date ? ' · ' + String(snapshot.release_date) : '') +
        (snapshot && snapshot.gateway_running ? ' · gateway 运行中' : ' · gateway 未运行')
  }

  const tries = attempt || 0

  if (!officialText && tries < XY_CHROME_VERSION_RETRY_MS.length) {
    XY_CHROME_try(() => {
      window.setTimeout(
        () => XY_CHROME_syncOfficialVersion(textEl, chip, snapshot, tries + 1),
        XY_CHROME_VERSION_RETRY_MS[tries]
      )
    }, undefined)
  }
}

const XY_CHROME_sidebarEl = () =>
  XY_CHROME_try(
    () =>
      document.querySelector("[data-slot='sidebar'][data-tour='sessions-sidebar']") ||
      document.querySelector("[data-slot='sidebar']") ||
      document.querySelector("[data-tour='sessions-sidebar']"),
    null
  )

const XY_CHROME_applyLeft = () => {
  const el = XY_CHROME_sidebarEl()
  let left = XY_CHROME_SIDEBAR_W

  if (el) {
    const rect = XY_CHROME_try(() => el.getBoundingClientRect(), null)

    if (rect && rect.width > 0) {
      left = Math.round(rect.right)
    }
  }

  XY_CHROME_try(() => document.documentElement.style.setProperty('--xy-chrome-left', left + 'px'), undefined)

}


/** 侧栏「搜索会话」输入框：给它所在行打标，让 CSS 能把输入框撑满并整行 no-drag。
 *  官方该输入框只有 45px 宽（[field-sizing:content]），且整条面板头是 -webkit-app-region:drag，
 *  实测点击只有那 45px 落焦、其余落到 body ⇒ 用户表现为「搜索没法输入」。
 *  v3.1 误删本函数定义却保留调用 ⇒ bind() 每次在此抛 ReferenceError（ReferenceError 会中断
 *  同一次 bind 的后续步骤），导致 tagSidebarRail()（侧栏底部并入底栏，用户第 1 条需求）
 *  与 rightDefault() 全部不执行。2026-09-29 恢复定义。 */
const XY_CHROME_tagSidebarSearch = () => {
  const sidebar = XY_CHROME_sidebarEl()

  if (!sidebar) {
    return
  }

  const input =
    sidebar.querySelector('input[placeholder*="搜索"]') || sidebar.querySelector('input')
  const row = input ? input.parentElement : null

  if (row && row.getAttribute('data-xy-chrome-search-row') !== '1') {
    row.setAttribute('data-xy-chrome-search-row', '1')
  }
}

/** 给侧栏底部行（profile-rail 的容器）打标记，让其并入底栏水平带（CSS 见
    [data-xy-sidebar-rail] 段）。取 [data-slot='profile-rail'] 的父节点 —— 那一层才是
    带 padding 的整行容器；侧栏节点变化时在每次 bind() 里重打，拿不到就把旧标记摘掉。 */
const XY_CHROME_tagSidebarRail = () => {
  const sidebar = XY_CHROME_sidebarEl()
  const rail = sidebar ? XY_CHROME_try(() => sidebar.querySelector("[data-slot='profile-rail']"), null) : null
  const wrap = rail ? rail.parentElement : null

  if (wrap && wrap.getAttribute('data-xy-sidebar-rail') === '1') {
    return
  }

  Array.prototype.forEach.call(document.querySelectorAll('[data-xy-sidebar-rail]'), el => {
    if (el !== wrap) {
      el.removeAttribute('data-xy-sidebar-rail')
    }
  })

  if (wrap) {
    wrap.setAttribute('data-xy-sidebar-rail', '1')
  }
}

/* ────────────────────────── 右侧栏默认隐藏（复刻 worktree 的 resting 语义）──────────
   worktree 的 Default 预设加了 resting: ['review','files','terminal']，applyTree() 会
   对每个 resting pane 调它的 paneCloser —— 官方 preset 没有这一项，所以右栏默认展开。
   插件改不了源码，这里点「关闭」复刻同一语义（应用自己的关闭按钮走的正是同一个
   closer）。只在**首次挂载**后的一次性窗口内执行，随后落盘 rightDefaultDone 永久
   让位：用户自己 ⌘J / 点开 diff 预览把右栏拉出来之后，插件不会再去关它。 */
const XY_CHROME_RIGHT_FLAG = 'rightDefaultDone'
/** 首次挂载后的动手窗口（ms）：窗口内关右栏，窗口外一律不再干预。 */
const XY_CHROME_RIGHT_GRACE = 8000
const XY_CHROME_RIGHT_MAXCLICKS = 8
/** 永不触碰的组：会话侧栏 / 主工作区（它们的 pane 归应用自己管）。 */
const XY_CHROME_RIGHT_SKIP = /^grp-(sessions|main)$/

/** 读/写插件作用域的标记（key 与 ctx.storage 同构：hermes.plugin.<id>.<key>）。 */
const XY_CHROME_flagKey = () => 'hermes.plugin.' + XY_PLUGIN_ID + '.' + XY_CHROME_RIGHT_FLAG

const XY_CHROME_flagGet = () => {
  if (XY_CTX && XY_CTX.storage && typeof XY_CTX.storage.get === 'function') {
    return XY_CHROME_try(() => XY_CTX.storage.get(XY_CHROME_RIGHT_FLAG, false), false) === true
  }

  return XY_CHROME_try(
    () => JSON.parse(localStorage.getItem(XY_CHROME_flagKey()) || 'false') === true,
    false
  )
}

const XY_CHROME_flagSet = () => {
  if (XY_CTX && XY_CTX.storage && typeof XY_CTX.storage.set === 'function') {
    XY_CHROME_try(() => XY_CTX.storage.set(XY_CHROME_RIGHT_FLAG, true), undefined)

    return
  }

  XY_CHROME_try(() => localStorage.setItem(XY_CHROME_flagKey(), 'true'), undefined)
}

const XY_CHROME_boxOf = el => XY_CHROME_try(() => el.getBoundingClientRect(), null)

const XY_CHROME_shown = el => {
  const rect = XY_CHROME_boxOf(el)

  return !!(rect && rect.width > 2 && rect.height > 2)
}

/** 是否落在窗口右半（右栏一侧）。不依赖预设名，故自定义布局同样适用。 */
const XY_CHROME_inRightRegion = el => {
  const rect = XY_CHROME_boxOf(el)
  const width = window.innerWidth || 0

  return !!(rect && width > 0 && rect.x >= width * 0.55)
}

/** 右栏是否还有渲染出来的内容——判定「目标是否已达成」的唯一依据。 */
const XY_CHROME_rightColumnSeen = () => {
  const split = XY_CHROME_try(() => document.querySelector('[data-tree-split="spl-right"]'), null)

  if (split && XY_CHROME_shown(split)) {
    return true
  }

  const chromeEls = XY_CHROME_try(
    () =>
      Array.prototype.slice.call(
        document.querySelectorAll('[data-zone-header], [data-zone-tabstrip], [data-panel-header]')
      ),
    []
  )

  return chromeEls.some(el => XY_CHROME_shown(el) && XY_CHROME_inRightRegion(el))
}

/** 文件树 pane 是否正开在右栏——是则它的关闭就是标题栏那个开关（= ⌘J 的
    $fileBrowserOpen），点它同时把整条右栏折叠掉。 */
const XY_CHROME_filesPaneVisible = () => {
  const el = XY_CHROME_try(
    () => document.querySelector('[data-project-tree]') || document.querySelector('[data-tree-group="grp-files"]'),
    null
  )

  return !!(el && XY_CHROME_shown(el) && XY_CHROME_inRightRegion(el))
}

const XY_CHROME_rightToggleBtn = () =>
  XY_CHROME_try(() => document.querySelector('[data-tour="right-pane-toggle"]'), null)

/** 右栏里其它工具 pane 的「关闭」按钮（= 应用自己的 paneCloser）。两道闸：
    ① 跳过会话侧栏/主工作区所在组；② 几何上必须在窗口右半、且按钮落在组头部条内
    （避免点到 pane 正文里的其它 close 图标）。 */
const XY_CHROME_rightPaneClosers = () => {
  const groups = XY_CHROME_try(
    () => Array.prototype.slice.call(document.querySelectorAll('[data-tree-group]')),
    []
  )

  return groups
    .filter(group => {
      const id = group.getAttribute('data-tree-group') || ''

      return (
        !XY_CHROME_RIGHT_SKIP.test(id) && XY_CHROME_shown(group) && XY_CHROME_inRightRegion(group)
      )
    })
    .map(group => {
      const rect = XY_CHROME_boxOf(group)
      const icon = XY_CHROME_try(() => group.querySelector('.codicon-close'), null)
      const btn = icon ? XY_CHROME_try(() => icon.closest('button'), null) : null

      if (!rect || !btn) {
        return null
      }

      const box = XY_CHROME_boxOf(btn)

      if (!box || box.top - rect.top > 44) {
        return null
      }

      return btn
    })
    .filter(Boolean)
}

const XY_CHROME_rightDefault = () => {
  const state = XY_CHROME_state

  if (state.rightDone) {
    return
  }

  if (!state.rightStartedAt) {
    state.rightStartedAt = Date.now()
  }

  if (XY_CHROME_flagGet()) {
    state.rightDone = true

    return
  }

  /* 目标达成（右栏已不可见，含「本来就隐藏」）才落盘标记；否则下次启动再试。 */
  if (!XY_CHROME_rightColumnSeen()) {
    XY_CHROME_flagSet()
    state.rightDone = true
    XY_CHROME_try(() => host.logs('xianyi-ui: 右侧栏默认隐藏 → 已就位'), undefined)

    return
  }

  if (
    Date.now() - state.rightStartedAt > XY_CHROME_RIGHT_GRACE ||
    state.rightClicks >= XY_CHROME_RIGHT_MAXCLICKS
  ) {
    state.rightDone = true

    return
  }

  if (state.rightBusy) {
    return
  }

  let actions = []

  if (XY_CHROME_filesPaneVisible()) {
    const toggle = XY_CHROME_rightToggleBtn()

    if (toggle) {
      actions = [toggle]
    }
  }

  if (!actions.length) {
    actions = XY_CHROME_rightPaneClosers()
  }

  if (!actions.length) {
    return
  }

  state.rightBusy = true
  state.rightClicks += actions.length

  actions.forEach(btn => XY_CHROME_try(() => btn.click(), undefined))

  XY_CHROME_try(
    () => host.logs('xianyi-ui: 右侧栏默认隐藏 → 关闭 ' + actions.length + ' 处'),
    undefined
  )

  setTimeout(() => {
    state.rightBusy = false
    XY_CHROME_try(XY_CHROME_rightDefault, undefined)
  }, 300)
}

/** 返回清理函数：撤销 ResizeObserver / MutationObserver / resize 监听。 */
const XY_CHROME_trackLeft = () => {
  let resizeObserver = null
  let mutationObserver = null
  let boundEl = null

  const observerCtor = typeof ResizeObserver === 'function' ? ResizeObserver : null

  const bind = () => {
    XY_CHROME_syncOverlay()
    XY_CHROME_syncView()
    const el = XY_CHROME_sidebarEl()

    if (el !== boundEl) {
      if (resizeObserver) {
        XY_CHROME_try(() => resizeObserver.disconnect(), undefined)
        resizeObserver = null
      }

      boundEl = el

      if (el && observerCtor) {
        resizeObserver = XY_CHROME_try(() => {
          const observer = new observerCtor(() => XY_CHROME_applyLeft())

          observer.observe(el)

          return observer
        }, null)
      }
    }

    XY_CHROME_tagSidebarSearch()
    XY_CHROME_tagSidebarRail()
    XY_CHROME_applyLeft()
    XY_CHROME_rightDefault()
  }

  bind()

  const onResize = () => bind()

  XY_CHROME_try(() => window.addEventListener('resize', onResize), undefined)

  if (typeof MutationObserver === 'function') {
    mutationObserver = XY_CHROME_try(() => {
      const observer = new MutationObserver(() => bind())

      /* subtree: true —— React 重渲染会换掉节点、连带丢掉我们打的标记；
         只监听 childList（不监听 attributes），命中即早退，不会形成自激循环。 */
      observer.observe(document.body, { childList: true, subtree: true })

      return observer
    }, null)
  }

  return () => {
    XY_CHROME_try(() => window.removeEventListener('resize', onResize), undefined)

    if (resizeObserver) {
      XY_CHROME_try(() => resizeObserver.disconnect(), undefined)
    }

    if (mutationObserver) {
      XY_CHROME_try(() => mutationObserver.disconnect(), undefined)
    }

    resizeObserver = null
    mutationObserver = null
    boundEl = null
  }
}

const XY_CHROME_stopTrackLeft = () => {
  const fn = XY_CHROME_state.leftCleanup

  XY_CHROME_state.leftCleanup = null

  if (typeof fn === 'function') {
    XY_CHROME_try(fn, undefined)
  }
}

const XY_CHROME_removeShared = () => {
  XY_CHROME_try(() => document.documentElement.removeAttribute(XY_CHROME_ATTR), undefined)
  XY_CHROME_try(() => {
    const style = document.getElementById(XY_CHROME_STYLE_ID)

    if (style && style.parentNode) {
      style.parentNode.removeChild(style)
    }
  }, undefined)
}

const XY_CHROME_removeById = id => {
  XY_CHROME_try(() => {
    const stale = document.getElementById(id)

    if (stale && stale.parentNode) {
      stale.parentNode.removeChild(stale)
    }
  }, undefined)
}

/** 一组 teardown 的袋子：后进先出，单个异常不阻断其余。 */
const XY_CHROME_bag = () => {
  const items = []

  return {
    add(fn) {
      if (typeof fn === 'function') {
        items.push(fn)
      }
    },
    run() {
      while (items.length) {
        const fn = items.pop()

        try {
          fn()
        } catch (err) {
          XY_CHROME_warn(err)
        }
      }
    }
  }
}

const XY_CHROME_teardown = slot => {
  const fn = XY_CHROME_state.slots[slot]

  XY_CHROME_state.slots[slot] = null

  if (typeof fn === 'function') {
    try {
      fn()
    } catch (err) {
      XY_CHROME_warn(err)
    }
  }
}

/** 安装一个覆盖层：先清同槽位旧实例（热重载/重复注册都会走到这里）。 */
const XY_CHROME_install = (slot, build, ctx) => {
  XY_CHROME_teardown(slot)

  const bag = XY_CHROME_bag()
  const node = XY_CHROME_try(() => build(bag, ctx), null)

  if (node && node.parentNode) {
    bag.add(() => {
      if (node.parentNode) {
        node.parentNode.removeChild(node)
      }
    })
  }

  XY_CHROME_state.slots[slot] = () => bag.run()

  return XY_CHROME_state.slots[slot]
}

/** 拆掉全部三块（XyChromeInstall 的清理函数与 React 包装组件共用）。 */
const XY_CHROME_dispose = () => {
  XY_CHROME_teardown('backdrop')
  XY_CHROME_teardown('status')
  XY_CHROME_removeById(XY_CHROME_BACKDROP_ID)
  XY_CHROME_removeById(XY_CHROME_STATUS_ID)
  XY_CHROME_stopTrackLeft()
  XY_CHROME_removeShared()
  XY_CHROME_state.refs = 0
  /* 撤下 overlay 标记，避免禁用插件后 <html data-xy-overlay='on'> 残留影响官方界面。 */
  XY_CHROME_state.overlay = ''
  XY_CHROME_try(() => document.documentElement.removeAttribute('data-xy-overlay'), undefined)
}

/* ---------------------------------------------------------------- 全局背景层 */

const XY_CHROME_buildBackdrop = bag => {
  const layer = XY_CHROME_el('div', 'xyc-backdrop')

  layer.id = XY_CHROME_BACKDROP_ID
  layer.setAttribute('aria-hidden', 'true')
  XY_CHROME_removeById(XY_CHROME_BACKDROP_ID)
  /* 视图装饰层（看板页）：与整应用背景同层、同 z，靠 CSS 按 data-xy-view 显隐。 */
  layer.appendChild(XY_CHROME_el('div', 'xyc-view-deco'))
  document.body.appendChild(layer)
  bag.add(XY_CHROME_mountShared())

  return layer
}

/* ---------------------------------------------------------------- 底部状态条 */

/** label + 值节点。 */
const XY_CHROME_field = (label, valueClass, suffix) => {
  const wrap = XY_CHROME_el('span', 'xyc-status-item')

  if (label) {
    wrap.appendChild(document.createTextNode(label))
  }

  const value = XY_CHROME_el('b', valueClass || 'xyc-status-value')

  wrap.appendChild(value)

  if (suffix) {
    wrap.appendChild(document.createTextNode(suffix))
  }

  return { value, wrap }
}

const XY_CHROME_clock = () => {
  const now = new Date()

  return String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0')
}

/* ── 「当前目录」族的数据源（用户 2026-09-29 增；2026-09-30 改为官方同口径）─────
   旧实现读 localStorage 的 'hermes.desktop.workspace-cwd'：那个键只在用户**显式选定**
   目录时写（store/session.ts 的 setCurrentCwd），跟随会话走 setCurrentCwdTransient 不写 ——
   所以只要用户没做过选目录的动作，键永远不存在、格子永久空白（2026-09-30 实测：键缺席
   而同一含义的 host.state.cwd 有值）。

   官方口径（app/shell/hooks/use-statusbar-items.tsx:284/:555）：
     hidden = !currentCwd；label = projectName || pathLeaf(currentCwd)，
     currentCwd = $currentCwd 原子（SDK 已暴露为 host.state.cwd，sdk/index.ts「cwd」）。
   projectName = projectNameForCwd(cwd)（store/projects.ts:223）：跳过 isAuto / isNoProject，
   在 paths（project.path + repos[].path + groups[].path）里取「包含 cwd 的最长一条」所属项目的
   label；比较语义来自 lib/path-compare.ts（分隔符、尾斜杠、盘符/UNC 大小写折叠）。
   插件取同源项目树的通道：host.request('projects.tree', { preview_limit, profile })。
   取不到就整格隐藏（不编造）；完整路径放 title。 */
const XY_CHROME_cleanPath = path =>
  String(path === undefined || path === null ? '' : path).trim().replace(/\\/g, '/').replace(/\/+$/, '') || '/'

const XY_CHROME_comparisonPath = path =>
  (/^[A-Za-z]:(\/|$)/.test(path) || path.indexOf('//') === 0 ? path.toLowerCase() : path)

const XY_CHROME_isUnderPath = (parent, child) => {
  const p = XY_CHROME_comparisonPath(XY_CHROME_cleanPath(parent))
  const c = XY_CHROME_comparisonPath(XY_CHROME_cleanPath(child))

  return c === p || c.indexOf(p + '/') === 0
}

const XY_CHROME_readCwd = state =>
  XY_CHROME_try(() => {
    const path = String(XY_CHROME_atomValue(state && state.cwd, '') || '').trim().replace(/[\\/]+$/, '')

    return path || null
  }, null)

const XY_CHROME_dirName = path => {
  const parts = String(path).split(/[\\/]/).filter(Boolean)

  return parts.length ? parts[parts.length - 1] : null
}

/* projects.tree 缓存（官方前端在项目树返回上做同一匹配；插件按 TTL 拉同源数据）。
   不用 setInterval：只在刷新节拍上按需拉，SDK 也禁用常驻定时器。 */
const XY_CHROME_PROJECTS_TTL_MS = 5000
let XY_CHROME_projects = []
let XY_CHROME_projectsAt = 0
let XY_CHROME_projectsPending = false

/** 官方 projectNameForCwd 的等价实现（store/projects.ts:223-250）。 */
const XY_CHROME_projectNameForCwd = cwd => {
  const target = XY_CHROME_cleanPath(cwd)

  if (!target) {
    return null
  }

  let best = null
  let bestLen = -1

  for (const project of XY_CHROME_projects) {
    if (!project || project.isAuto || project.isNoProject) {
      continue
    }

    const repos = Array.isArray(project.repos) ? project.repos : []
    const paths = [project.path].concat(
      repos.flatMap(repo => [repo && repo.path].concat(((repo && repo.groups) || []).map(group => group && group.path)))
    )

    for (const path of paths) {
      const p = String(path === undefined || path === null ? '' : path).trim()

      if (p && XY_CHROME_isUnderPath(p, target) && p.length > bestLen) {
        bestLen = p.length
        best = project.label
      }
    }
  }

  return best || null
}

/** 拉一次 projects.tree（与官方同一 RPC、同一参数形态）；到达后调用 onArrive 就地补名。
    profile 为空（全部 profile 视图）时官方同样取不到项目树，直接跳过（回退目录名）。 */
const XY_CHROME_refreshProjects = (hostRef, onArrive) => {
  if (!hostRef || typeof hostRef.request !== 'function' || XY_CHROME_projectsPending) {
    return
  }

  if (XY_CHROME_projects.length && Date.now() - XY_CHROME_projectsAt < XY_CHROME_PROJECTS_TTL_MS) {
    return
  }

  const profile = XY_CHROME_atomValue((hostRef.state || {}).profile, '')

  if (!profile) {
    return
  }

  XY_CHROME_projectsPending = true

  XY_CHROME_try(() => {
    Promise.resolve(hostRef.request('projects.tree', { preview_limit: 3, profile }))
      .then(res => {
        XY_CHROME_projects = (res && res.projects) || []
        XY_CHROME_projectsAt = Date.now()
      })
      .catch(err => {
        XY_CHROME_warn(err)
      })
      .then(() => {
        XY_CHROME_projectsPending = false
        onArrive()
      })
  }, undefined)
}

/**
 * 底部状态条（设计稿 global-chrome.tsx:412-486 的 1:1 对齐版）。
 * 项集与顺序按设计稿：左簇 HERMES·状态药丸·模型·活跃会话·消息 ｜ 右簇 时钟·TOKEN·版本。
 * 每个字段的来源都已核实；核不到的字段一律不显示、不编造：
 *   gateway / model / focusedUsage → host.state 原子（sdk/index.ts:666-712）
 *   version / active_sessions / gateway_running → host.status()（文档 956；
 *       源码 sdk/index.ts:1469 async () => getStatus()；返回 StatusResponse，
 *       version:1389 / active_sessions:1369 / gateway_running:1380 / release_date:1388，types/hermes.ts）
 *   focusedUsage.total:842、context_percent:835、cost_usd:839（Partial ⇒ 逐字段判空）
 *   活跃会话/消息 → host.request('session.list', {})：行数 / Σ message_count
 *   审批模式     → host.request('config.get', { key: 'approvals.mode' })，响应取 .value
 *   时钟 → 事件驱动（路由变化 / 窗口聚焦 / 可见性），无 setInterval（SDK 禁用常驻定时器）
 * 设计稿有、本插件未实现的一项（官方数据面取不到，绝不编造）：
 *   今日 token → session.list 的行里没有 token 字段（SessionListRow），用量类方法只有
 *                usage.bars（订阅额度条）。设计稿该项来自桌面本地会话库，插件读不到。
 */
const XY_CHROME_buildStatus = (bag, ctx) => {
  const bar = XY_CHROME_el('div', 'xyc-status')

  bar.id = XY_CHROME_STATUS_ID
  bar.setAttribute('role', 'contentinfo')

  const hostRef = XY_CHROME_hostRef()
  const state = hostRef && hostRef.state ? hostRef.state : {}

  /* 左簇：网关药丸 + 模型 + 上下文 */

  const pill = XY_CHROME_el('span', 'xyc-pill')
  const pillDot = XY_CHROME_el('span', 'xyc-dot-sm')
  const pillText = XY_CHROME_el('span', null, '—')

  pill.appendChild(pillDot)
  pill.appendChild(pillText)

  /* 模型信息按需求排在「消息」右侧；「上下文」再排模型右侧（值复用 f3 的格式化函数，
     数据同源 = host.state.focusedUsage，与底栏 TOKEN 一格同一订阅）。 */
  const model = XY_CHROME_field('模型 ', 'xyc-status-value xyc-status-accent', ' ⚡')
  const context = XY_CHROME_field('上下文 ', 'xyc-status-value xyc-status-context')
  /* 首次渲染先整格隐藏：数据拿不到时不该只剩「上下文」三个字（有值时由 refreshStatus 打开）。 */
  context.wrap.style.display = 'none'
  /* 「当前目录」族（用户 2026-09-29 指定）：位置在「系统正常」药丸之前，沿用同一胶囊样式
     （xyc-pill），但按用户后续要求**不带圆点**；只显示目录名 —— 不带标签文字、不带路径。先整格隐藏。
     2026-09-30 用户要求：字色改跟「审批模式」族（xyc-pill-cwd）。 */
  const cwdPill = XY_CHROME_el('span', 'xyc-pill xyc-pill-cwd')
  const cwdText = XY_CHROME_el('span', null, '—')

  cwdPill.appendChild(cwdText)
  cwdPill.style.display = 'none'
  /* 设计稿 424 行的「今日 token」本插件不实现：session.list 的行结构里没有 token 字段
     （gateway-contract.generated.ts:2962-2970 SessionListRow 只有
     id/title/preview/started_at/message_count/source），用量类方法只有 usage.bars
     （订阅额度条，非本地 token 计数）。设计稿那一项来自桌面本地会话库，插件读不到 ——
     宁缺勿造：这一格留空，不显示 0。 */

  ;[cwdPill, pill, model.wrap, context.wrap].forEach(node => bar.appendChild(node))

  /* 右簇：时钟 + TOKEN + 版本 chip（点击 → 设置页） */
  const right = XY_CHROME_el('div', 'xyc-status-right')
  const clockValue = XY_CHROME_el('span', null, XY_CHROME_clock())
  const clockItem = XY_CHROME_el('span', 'xyc-status-item')

  clockItem.appendChild(clockValue)

  const tokens = XY_CHROME_field('TOKEN ', 'xyc-status-value xyc-status-warn', ' ⏱')
  /* 首次渲染先整格隐藏：无 token 数据时不露出「TOKEN — ⏱」空壳（有值由 refreshStatus 打开）。 */
  tokens.wrap.style.display = 'none'
  const versionChip = XY_CHROME_el('button', 'xyc-chip')
  const versionDot = XY_CHROME_el('span', 'xyc-dot-sm xyc-dot-ver')
  const versionText = XY_CHROME_el('span', null, 'v—')

  versionChip.type = 'button'
  versionChip.title = 'Hermes 版本 / 检查更新'
  versionChip.appendChild(versionDot)
  versionChip.appendChild(versionText)
  /* 与原版一致（2026-09-29 用户登记）：官方该项 onSelect = openUpdateOverlayFor('client')
     —— 打开更新面板并强制检查更新，而不是跳设置页（原实现跳 /settings 是错的）。
     插件没有该 store 的写入口，于是直接派发官方那条（仍挂在 DOM 里的）按钮的 click，
     走的就是官方同一条 handler；取不到才回落设置页。 */
  versionChip.addEventListener('click', () => {
    if (typeof haptic === 'function') {
      XY_CHROME_try(() => haptic('tap'), undefined)
    }

    XY_CHROME_try(() => {
      const official = XY_CHROME_officialVersionNode()

      if (official && typeof official.click === 'function') {
        official.click()
        return
      }

      if (hostRef && typeof hostRef.navigate === 'function') {
        hostRef.navigate('/settings')
      }
    }, undefined)
  })

  /* 审批模式（设计稿 427-437：原 Hermes 原生状态栏项并入本栏）。可写：点击 chip →
     本插件自绘下拉（手动 / 智能 / 关闭），写入走官方同一条 config.set 路径。
     官方同款交互：app/shell/approval-mode-menu.tsx（状态栏项 + radio 组）。 */
  const approvalChip = XY_CHROME_el('button', 'xyc-chip xyc-chip-warn')

  approvalChip.type = 'button'
  approvalChip.title = '审批模式尚未获取，点击重试'
  approvalChip.setAttribute('aria-haspopup', 'menu')
  approvalChip.appendChild(XY_CHROME_icon('zap'))
  const approvalText = XY_CHROME_el('span', null, '审批模式：未连接')

  approvalChip.appendChild(approvalText)
  approvalChip.addEventListener('click', event => {
    event.preventDefault()
    event.stopPropagation()

    if (!approvalMode) {
      refreshApproval()
      return
    }

    if (XY_CHROME_approvalMenu) {
      XY_CHROME_closeApprovalMenu()
      return
    }

    if (typeof haptic === 'function') {
      XY_CHROME_try(() => haptic('tap'), undefined)
    }

    XY_CHROME_openApprovalMenu(approvalChip, approvalMode, mode => {
      XY_CHROME_closeApprovalMenu()
      writeApprovalMode(mode) /* 只有网关确认后才显示新模式 */
    })
  })

  ;[approvalChip, clockItem, tokens.wrap, versionChip].forEach(node => right.appendChild(node))
  bar.appendChild(right)

  /* host.status() 是异步一次性快照：安装时取一次，gateway 状态变化时再取（事件驱动，无轮询）。 */
  let statusTicket = 0
  let statusSnapshot = null

  const refreshStatus = () => {
    if (!hostRef || typeof hostRef.status !== 'function') {
      return
    }

    statusTicket += 1
    const ticket = statusTicket

    XY_CHROME_try(() => {
      Promise.resolve(hostRef.status())
        .then(snapshot => {
          if (ticket !== statusTicket || !snapshot) {
            return
          }

          statusSnapshot = snapshot
          XY_CHROME_syncOfficialVersion(versionText, versionChip, statusSnapshot)
        })
        .catch(err => XY_CHROME_warn(err))
    }, undefined)
  }

  /* 审批模式（host.request('config.get', { key: 'approvals.mode' })，响应取 .value ——
     与官方 store/approval-mode.ts:50-65 的 syncApprovalModeForProfile 同型；
     文案照抄 i18n/zh.ts:4335-4343）。 */
  let approvalMode = ''
  let approvalTicket = 0

  const clearApproval = () => {
    approvalMode = ''
    XY_CHROME_setText(approvalText, '审批模式：未连接')
    approvalChip.title = '审批模式尚未获取，点击重试'
  }

  const approvalParams = params => {
    const profile = String(XY_CHROME_atomValue(state.profile, '') || '').trim()
    return profile ? { ...params, profile } : params
  }

  /** 渲染一个模式（chip 文案 + title；菜单打勾项也读 approvalMode）。 */
  const applyApproval = mode => {
    const label = XY_CHROME_APPROVAL_LABELS[mode]

    if (!label) {
      return
    }

    approvalMode = mode
    XY_CHROME_setText(approvalText, label)
    approvalChip.title = '审批模式：' + label + ' · 点击切换'
    approvalChip.hidden = false
  }

  const refreshApproval = () => {
    if (!hostRef || typeof hostRef.request !== 'function') {
      return
    }

    approvalTicket += 1
    const ticket = approvalTicket

    XY_CHROME_try(() => {
      Promise.resolve(hostRef.request('config.get', approvalParams({ key: 'approvals.mode' })))
        .then(result => {
          if (ticket !== approvalTicket) {
            return
          }

          const raw = result && typeof result === 'object' && 'value' in result ? result.value : result
          const mode = typeof raw === 'string' ? raw.trim().toLowerCase() : ''

          // Match the native store: a missing/unrecognised configured value is manual.
          applyApproval(XY_CHROME_APPROVAL_LABELS[mode] ? mode : 'manual')
        })
        .catch(err => {
          if (ticket === approvalTicket) clearApproval()
          XY_CHROME_warn(err)
        })
    }, undefined)
  }

  /** 写入审批模式（官方 store/approval-mode.ts:67-95 同型：config.set → 以响应 .value 为准；
      失败回滚 —— 这里回滚 = 重新 config.get，显示回到网关的真实值，不猜）。 */
  const writeApprovalMode = mode => {
    if (!hostRef || typeof hostRef.request !== 'function') {
      return
    }

    approvalTicket += 1
    const ticket = approvalTicket

    XY_CHROME_try(() => {
      Promise.resolve(hostRef.request('config.set', approvalParams({ key: 'approvals.mode', value: mode })))
        .then(result => {
          if (ticket !== approvalTicket) {
            return
          }

          const raw = result && typeof result === 'object' && 'value' in result ? result.value : result
          const authoritative = typeof raw === 'string' ? raw.trim().toLowerCase() : ''

          if (XY_CHROME_APPROVAL_LABELS[authoritative]) {
            applyApproval(authoritative)
            return
          }

          refreshApproval()
        })
        .catch(err => {
          XY_CHROME_warn(err)
          clearApproval()
          refreshApproval()
        })
    }, undefined)
  }

  const refresh = () => {
    const gateway = String(XY_CHROME_atomValue(state.gateway, '') || '')
    const online = gateway === 'open'

    XY_CHROME_setText(pillText, online ? '系统正常' : gateway ? 'Gateway ' + gateway : 'Gateway 离线')
    if (online) {
      pill.removeAttribute('data-off')
      pillDot.className = 'xyc-dot-sm'
    } else {
      pill.setAttribute('data-off', 'true')
      pillDot.className = 'xyc-dot-sm xyc-dot-off'
    }

    const composerModel = XY_CHROME_visibleComposerModel()
    XY_CHROME_setText(model.value, composerModel)
    model.wrap.style.display = composerModel ? '' : 'none'

    const usage = XY_CHROME_atomValue(state.focusedUsage, null)
    const contextText = XY_CHATFX_contextLabel(usage)

    /* 取不到上下文数据时整格隐藏（标签 + 值一起），有数据再显示 —— 需求：不要只显示「上下文」三个字。
       隐藏放在 inline style 上：CSS 里 .xyc-status-item 是 display:inline-flex，清空即回落该类。
       防御性过滤：任何来源的前导「~」/「≈」都不进底栏（用户 2026-09-29 要求彻底不要）。 */
    const contextClean = contextText ? String(contextText).replace(/^[\s~≈]+/, '').replace('/', ' / ') : ''

    XY_CHROME_setText(context.value, contextClean || null)
    context.wrap.style.display = contextClean ? '' : 'none'

    /* 「当前目录」族：自定义显示为 BD（用户指定）。
       数据来源仍是 host.state.cwd（用于判断是否显示），但文字固定为 BD。 */
    const cwdPath = XY_CHROME_readCwd(state)
    const cwdName = cwdPath ? 'BD' : null

    XY_CHROME_setText(cwdText, cwdName || null)
    cwdPill.style.display = cwdName ? '' : 'none'

    if (cwdPath) {
      cwdPill.title = cwdPath
    }

    /* 取不到 token 数据时整格隐藏（标签 + 值 + ⏱ 一起），有数据再显示 —— 与「上下文」同一规则。 */
    const tokenTotal = usage && typeof usage.total === 'number' ? usage.total : null

    XY_CHROME_setText(tokens.value, tokenTotal === null ? null : XY_CHROME_fmt(tokenTotal))
    tokens.wrap.style.display = tokenTotal === null ? 'none' : ''
    XY_CHROME_setText(clockValue, XY_CHROME_clock())
    /* 官方版本文案会随更新检查变化（(+N) ↔ (update)）：外壳每次刷新顺带镜像一次。 */
    XY_CHROME_syncOfficialVersion(versionText, versionChip, statusSnapshot)
  }

  refresh()
  refreshStatus()
  refreshApproval()

  /* atom 订阅（退订函数全部进 bag，清理时统一断开） */
  ;['gateway', 'model', 'focusedSessionId', 'focusedStoredSessionId', 'focusedUsage'].forEach(key => {
    bag.add(XY_CHROME_watchAtom(state[key], refresh))
  })
  /* 输入框是会话级数据，SDK 的 state.model 是全局数据；监听输入框变更并镜像其标签。 */
  if (typeof MutationObserver === 'function') {
    const observer = new MutationObserver(records => {
      const relevant = records.some(record => {
        const target = record.target && (record.target.nodeType === 1 ? record.target : record.target.parentElement)
        if (target && target.closest && target.closest('[data-slot="composer-surface"]')) return true
        return Array.from(record.addedNodes || []).concat(Array.from(record.removedNodes || [])).some(node =>
          node.nodeType === 1 && (node.matches('[data-slot="composer-surface"]') || node.querySelector('[data-slot="composer-surface"]'))
        )
      })
      if (relevant) refresh()
    })
    observer.observe(document.body, { childList: true, subtree: true, characterData: true })
    bag.add(() => observer.disconnect())
  }
  bag.add(
    XY_CHROME_watchAtom(state.gateway, () => {
      refreshStatus()
      if (XY_CHROME_atomValue(state.gateway, '') !== 'open') clearApproval()
      refreshApproval()
    })
  )
  bag.add(XY_CHROME_watchAtom(state.profile, () => {
    clearApproval()
    refreshApproval()
  }))

  /* 时钟：事件驱动刷新（原 worktree 用 setInterval(30s)，SDK 禁止常驻定时器） */
  bag.add(XY_CHROME_onPathChange(ctx, refresh))
  bag.add(
    XY_CHROME_listen(ctx, window, 'focus', () => {
      XY_CHROME_setText(clockValue, XY_CHROME_clock())
      XY_CHROME_syncOfficialVersion(versionText, versionChip, statusSnapshot)
    })
  )
  bag.add(
    XY_CHROME_listen(ctx, document, 'visibilitychange', () => {
      XY_CHROME_setText(clockValue, XY_CHROME_clock())
      XY_CHROME_syncOfficialVersion(versionText, versionChip, statusSnapshot)
    })
  )

  bag.add(XY_CHROME_closeApprovalMenu)
  XY_CHROME_removeById(XY_CHROME_STATUS_ID)
  document.body.appendChild(bar)
  bag.add(XY_CHROME_mountShared())

  return bar
}

/* ════════════ 审批模式下拉菜单（可写）════════════
   写入路径与官方 store/approval-mode.ts:67-95 同型：
     host.request('config.set', { key: 'approvals.mode', value }) → 以响应 .value 为准；
     乐观更新在前（:74），失败回滚为上次确认值（:92）—— 插件同样：先按点击更新显示，
     失败再回读 config.get。官方按 profile 分桶缓存（$approvalModes），插件的
     config.get / config.set 走当前网关路由 ⇒ 天然落「当前 profile」，与官方 chip 同域。
   文案（标题 + 三项 + 三项描述）照抄 i18n/zh.ts:4335-4344；宽 288px 对齐官方 'w-72'。 */
const XY_CHROME_APPROVAL_LABELS = { manual: '手动', smart: '智能', off: '关闭' }
const XY_CHROME_APPROVAL_DESC = {
  manual: '执行需要审批的操作前询问',
  smart: '自动评估操作，并在需要时询问',
  off: '不显示审批提示，直接运行',
}
const XY_CHROME_APPROVAL_ORDER = ['manual', 'smart', 'off']

let XY_CHROME_approvalMenu = null
let XY_CHROME_approvalUnbind = null

/** 关掉审批菜单（幂等）。 */
const XY_CHROME_closeApprovalMenu = () => {
  if (XY_CHROME_approvalUnbind) {
    XY_CHROME_try(XY_CHROME_approvalUnbind, undefined)
    XY_CHROME_approvalUnbind = null
  }

  if (XY_CHROME_approvalMenu && XY_CHROME_approvalMenu.parentNode) {
    XY_CHROME_approvalMenu.parentNode.removeChild(XY_CHROME_approvalMenu)
  }

  XY_CHROME_approvalMenu = null
}

/**
 * 在 anchor（底栏审批 chip）上方打开审批菜单。
 *   current — 当前模式（决定打勾项）；onPick(mode) — 由调用方执行写入。
 * 关闭条件：点击菜单外 / Esc / 窗口 resize / 任意滚动（与官方 dropdown 同侧：右对齐）。
 */
const XY_CHROME_openApprovalMenu = (anchor, current, onPick) => {
  XY_CHROME_closeApprovalMenu()

  const panel = XY_CHROME_el('div', 'xyc-approval-menu')

  panel.setAttribute('role', 'menu')
  panel.setAttribute('aria-label', '审批模式')
  panel.appendChild(XY_CHROME_el('div', 'xyc-approval-title', '审批模式'))
  panel.appendChild(XY_CHROME_el('div', 'xyc-approval-sep'))

  XY_CHROME_APPROVAL_ORDER.forEach(mode => {
    const item = XY_CHROME_el('button', 'xyc-approval-item')
    const col = XY_CHROME_el('span', 'xyc-approval-col')

    item.type = 'button'
    item.setAttribute('role', 'menuitemradio')
    item.setAttribute('aria-checked', mode === current ? 'true' : 'false')
    if (mode === current) {
      item.setAttribute('data-current', 'true')
    }

    col.appendChild(XY_CHROME_el('span', 'xyc-approval-label', XY_CHROME_APPROVAL_LABELS[mode]))
    col.appendChild(XY_CHROME_el('span', 'xyc-approval-desc', XY_CHROME_APPROVAL_DESC[mode]))
    item.appendChild(XY_CHROME_icon('check', 'xyc-approval-tick'))
    item.appendChild(col)
    item.addEventListener('click', event => {
      event.preventDefault()
      event.stopPropagation()
      onPick(mode)
    })
    panel.appendChild(item)
  })

  document.body.appendChild(panel)
  XY_CHROME_approvalMenu = panel

  /* 定位：右对齐 chip、上方 6px；窗口太矮则翻到下方。 */
  const rect = anchor.getBoundingClientRect()
  const width = panel.offsetWidth
  const height = panel.offsetHeight
  const maxLeft = Math.max(8, window.innerWidth - width - 8)
  const left = Math.min(Math.max(8, rect.right - width), maxLeft)
  const above = rect.top - height - 6
  const top = above < 8 ? rect.bottom + 6 : above

  panel.style.left = Math.round(left) + 'px'
  panel.style.top = Math.round(top) + 'px'

  const onPointerDown = event => {
    if (!panel.contains(event.target)) {
      XY_CHROME_closeApprovalMenu()
    }
  }
  const onKeyDown = event => {
    if (event.key === 'Escape') {
      XY_CHROME_closeApprovalMenu()
    }
  }
  const onClose = () => XY_CHROME_closeApprovalMenu()

  /* 监听推迟一拍：本次「打开」的点击否则立刻命中外部判定，菜单一闪即关。 */
  const timer = window.setTimeout(() => {
    document.addEventListener('pointerdown', onPointerDown, true)
    document.addEventListener('keydown', onKeyDown, true)
    window.addEventListener('resize', onClose)
    window.addEventListener('scroll', onClose, true)
  }, 0)

  XY_CHROME_approvalUnbind = () => {
    window.clearTimeout(timer)
    document.removeEventListener('pointerdown', onPointerDown, true)
    document.removeEventListener('keydown', onKeyDown, true)
    window.removeEventListener('resize', onClose)
    window.removeEventListener('scroll', onClose, true)
  }
}

/* ---------------------------------------------------------------- 唯一入口 */

/**
 * 装配入口（拼装契约：无参数或可选 ctx，返回清理函数）。
 * f0-main.js 的用法：`React.useEffect(() => XyChromeInstall(), [])`。
 * 幂等：重复调用先拆旧实例；返回的清理函数只在「仍是当前实例」时生效，
 * 因此热重载不会互相拆台（旧 cleanup 空转，不拆新实例的 UI）。
 */
const XyChromeInstall = ctx => {
  XY_CHROME_state.installId += 1
  const myId = XY_CHROME_state.installId

  XY_CHROME_install('backdrop', bag => XY_CHROME_buildBackdrop(bag), ctx)
  XY_CHROME_install('status', (bag, innerCtx) => XY_CHROME_buildStatus(bag, innerCtx), ctx)

  /* 首屏校正：启动时 hash 可能已在 overlay 视图上（如 #/settings）。 */
  XY_CHROME_syncOverlay()
  XY_CHROME_syncView()

  // 宿主若给了 onDispose，再挂一道保险（清理函数幂等，重复调用无副作用）。
  if (ctx && typeof ctx.onDispose === 'function') {
    XY_CHROME_try(() => ctx.onDispose(() => XyChromeDispose(myId)), undefined)
  }

  return () => XyChromeDispose(myId)
}

/** 清理实现（XyChromeInstall 的返回值调用它；带 installId 防止过期实例拆台）。 */
const XyChromeDispose = installId => {
  if (installId !== undefined && installId !== XY_CHROME_state.installId) {
    return
  }

  XY_CHROME_state.installId += 1
  XY_CHROME_dispose()
}

/* ---------------------------------------------------------------- 可选 React 包装组件 */

/** 全局背景层（fixed、pointer-events:none、最低 z-index，绝不拦截点击）。 */
function XyChromeBackdrop(ctx) {
  React.useEffect(() => {
    const stop = XY_CHROME_install('backdrop', bag => XY_CHROME_buildBackdrop(bag), ctx)

    return () => {
      try {
        stop()
      } catch (err) {
        XY_CHROME_warn(err)
      }
    }
  }, [])

  return null
}

/** 底部状态条。 */
function XyChromeStatus(ctx) {
  React.useEffect(() => {
    const stop = XY_CHROME_install('status', (bag, innerCtx) => XY_CHROME_buildStatus(bag, innerCtx), ctx)

    return () => {
      try {
        stop()
      } catch (err) {
        XY_CHROME_warn(err)
      }
    }
  }, [])

  return null
}
// ─────────────────────── f3-chatfx.js ───────────────────────
/**
 * 分片 3/3 —— 聊天区头像（DOM 注入）。
 *
 * 装配契约（父 agent f0-main.js）：
 *   - 纯 ESM 片段：无 import / export / JSX；顶层只有声明，无自动执行副作用。
 *   - 可用全局：jsx / jsxs / React / host / haptic / useValue（本片只用到 React 与 host）。
 *   - XyChatFx 是 React 组件，`return null`，全部工作在 React.useEffect(..., []) 内；
 *     effect 返回清理函数（断 observer / 撤订阅 / 移除所有注入节点与 <style id="xy-chatfx-css">）。
 *   - 顶层标识符一律 XyChatFx* / XY_CHATFX*；不使用 setInterval（只用一次性 rAF 批处理）。
 *
 * 核实结论（官方 0.21.5，逐文件搜过）：头像与上下文计量在本仓库中**均无逐行等价元素** → 两项都注入 DOM。
 *   头像：官方线程里没有助手/用户逐行头像槽；唯一的 <img> 头像是「中继发送者」提示图标
 *         —— user-message.tsx:212 / agent-delivery.tsx:142 的 size-4（16px）rounded-full，
 *         由 resolveAgentAvatar(handle)（:107）经 gateway 拉取，与 worktree 的 30px rounded-lg 印记不同源。
 *
 * 复现来源（上游 UI worktree，只读，未修改）。行号均已 grep 复核：
 *   - 头像视觉  components/assistant-ui/thread/chat-avatar.tsx:13（AVATAR_BASE =
 *               'size-[30px] shrink-0 select-none overflow-hidden rounded-lg [-webkit-app-region:no-drag]'）、
 *               :20-27+:31（助手印记 hmTile/hmSheen + H 形 path）、
 *               :41-48+:53+:55（用户印记 usTile/usSheen + circle r=7.5 + 双肩 path）、
 *               :63（助手 span）、:89（用户 button cursor-pointer hover:opacity-90）、:96（隐藏 file input）
 *   - 头像存储  store/chat-avatar.ts（localStorage 键，与 worktree 同键互通）
 *   - 行接线    components/assistant-ui/thread/index.tsx:140-141（助手行 flex items-start gap-x-2.5
 *               + AssistantAvatar mt-0.5）、:144（名签 mb-1 text-[0.78rem] font-bold
 *               tracking-[0.01em] text-[#EEF4FF]）、:161+:168（用户行 flex justify-end
 *               gap-x-2.5 + UserAvatar mt-1）
 *   （原 composer 上下文计量行已按需求移到底部状态栏，本片只保留头像；
 *               :72（金色 #FFBD4B + label.replace('/', ' / ')）、:73（92×5 轨道 bg-[#152638]）
 *     XY_CHATFX_contextLabel 仍保留 —— 底栏「上下文」一格复用它的格式化口径。）
 *               :1553（挂载点，位于 composer-fade 容器 :1489 之内、COMPOSER_AREAS.bottom :1558 之前；
 *               flex gap-3 pt-1 max-sm:hidden + 右侧 ml-auto xl:inline 提示）
 *   - 取数口径  lib/statusbar.tsx:44-46（usageContextLabel）+ apps/shared/src/format.ts:7/:14/:22（compactNumber）
 *               —— 本片按语义等价重实现，不 import。
 *   - 官方对照  app/shell/context-usage-panel.tsx:38 [data-slot='context-usage-panel']、:91
 *               [data-slot='context-usage-bar'] 是**外壳面板**里的用量视图（百分比 + 分类 token 列表，
 *               w-72 弹层），既不在 composer 内、也没有 worktree 的「12.4k / 128k」金色行
 *               → composer 计量仍属新增，需注入。
 *
 * 官方 0.21.5 等价元素核实结论：本片要复现的两处 —— 逐行聊天头像、composer 内的金色计量行 ——
 *   在官方源码里都**没有可直接复用的元素**，故两项均注入 DOM。两个易被误认的「貌似等价物」已排除：
 *   ① 官方头像 <img> 只出现在「中继发送者提示」（16px 圆头像）；② 官方上下文用量只存在于
 *   shell 侧（状态栏 label + context-usage-panel 弹层），composer 目录内 context_* 命中数为 0。
 */

/* ===================== 样式（本片新增 DOM 专属） ===================== */

const XY_CHATFX_CSS = `
/* 1. 头像节点：等价 chat-avatar.tsx:13 AVATAR_BASE
      （size-[30px] shrink-0 select-none overflow-hidden rounded-lg [-webkit-app-region:no-drag]）。
      选择器带两个自定义属性 (0,2,0)，稳压官方 [data-slot='aui_user-message-root'] button (0,1,1)。 */
[data-xy-chatfx-node='row-avatar'][data-xy-chatfx-avatar] {
  display: block;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: none;
  overflow: hidden;
  -webkit-user-select: none;
  user-select: none;
  -webkit-app-region: no-drag;
  cursor: default;
}
[data-xy-chatfx-node='row-avatar'][data-xy-chatfx-avatar='user'] {
  cursor: pointer;
  transition: opacity 0.15s ease-out;
}
[data-xy-chatfx-node='row-avatar'][data-xy-chatfx-avatar='user']:hover {
  opacity: 0.9;
}
[data-xy-chatfx-node='row-avatar'] > svg,
[data-xy-chatfx-node='row-avatar'] > img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 2. 助手行：[头像 30px][10px][内容]（等价 thread/index.tsx:140-152 flex gap-x-2.5 items-start）。
      :has() 保证布局只在头像确实注入成功后生效；官方 styles.css:2316-2327 自身即用 :has()，本运行时安全。 */
[data-slot='aui_assistant-message-root']:has(> [data-xy-chatfx-node='row-avatar']) {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  column-gap: 10px;
}
[data-slot='aui_assistant-message-root']:has(> [data-xy-chatfx-node='row-avatar'])
  > *:not([data-xy-chatfx-node='row-avatar']):not([data-xy-chatfx-node='assistant-name']) {
  grid-column: 2;
}
[data-slot='aui_assistant-message-root'] > [data-xy-chatfx-node='row-avatar'] {
  grid-column: 1;
  grid-row: 1;
  margin-top: 2px;
}
/* 可选名签行（默认不注入，见 XY_CHATFX_setAssistantName）。等价 thread/index.tsx:144。 */
[data-slot='aui_assistant-message-root'] > [data-xy-chatfx-node='assistant-name'] {
  grid-column: 2;
  grid-row: 1;
  margin-bottom: 4px;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #EEF4FF;
}

/* 3. 用户行：[内容][10px][头像 30px]，头像靠右（等价 thread/index.tsx:161-169 justify-end gap-x-2.5）。 */
[data-slot='aui_user-message-root']:has(> [data-xy-chatfx-node='row-avatar']) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 30px;
  column-gap: 10px;
}
[data-slot='aui_user-message-root']:has(> [data-xy-chatfx-node='row-avatar'])
  > *:not([data-xy-chatfx-node='row-avatar']) {
  grid-column: 1;
  min-width: 0;
}
[data-slot='aui_user-message-root'] > [data-xy-chatfx-node='row-avatar'] {
  grid-column: 2;
  grid-row: 1;
  margin-top: 4px;
  justify-self: end;
  align-self: start;
}

/* 4. 独立舞台容器（共享 <defs> + 隐藏 file input）：挂 body，永不落在 React 树内。 */
[data-xy-chatfx-stage='defs'] {
  position: absolute;
  top: 0;
  left: 0;
  width: 0;
  height: 0;
  overflow: hidden;
  pointer-events: none;
}
[data-xy-chatfx-node='avatar-input'] {
  display: none;
}

`

/* ===================== 常量 ===================== */

/** 用户头像 localStorage 键（= worktree store/chat-avatar.ts:10，同键互通）。 */
const XY_CHATFX_USER_AVATAR_KEY = 'hermes.desktop.chat.user-avatar'

/** 样式节点 id（装配契约指定）。 */
const XY_CHATFX_STYLE_ID = 'xy-chatfx-css'

/** 官方 0.21.5 锚点实证（grep 复核）：styles.css:1359/1640/1774/2306/2316（:2316 起即 :has() 用法）、
 *  composer/index.tsx:1458 [data-slot="composer-surface"] / :1488 [data-slot="composer-fade"]。 */
const XY_CHATFX_SEL_ASSISTANT = '[data-slot="aui_assistant-message-root"]'
const XY_CHATFX_SEL_USER = '[data-slot="aui_user-message-root"]'
const XY_CHATFX_SEL_COMPOSER = '[data-slot="composer-surface"]'
const XY_CHATFX_SEL_COMPOSER_FADE = '[data-slot="composer-fade"]'
const XY_CHATFX_SEL_ROW_AVATAR = '[data-xy-chatfx-node="row-avatar"]'
/* 用户轮的图片附件行：上游把嵌入图放在 aui_user-message-root 之外的整宽 flex 行里。 */
const XY_CHATFX_SEL_USER_IMAGE_ROW = 'div:has(> [data-slot="aui_directive-text"] > [data-slot="aui_embedded-images"])'
const XY_CHATFX_SEL_USER_BUBBLE = '[data-slot="aui_user-bubble-actions"] button'

const XY_CHATFX_ATTR_NODE = 'data-xy-chatfx-node'
const XY_CHATFX_ATTR_AVATAR = 'data-xy-chatfx-avatar'
const XY_CHATFX_ATTR_STAGE = 'data-xy-chatfx-stage'
const XY_CHATFX_ATTR_EMPTY = 'data-xy-chatfx-empty'

/** 助手印记：逐字抄 chat-avatar.tsx:18-32；gradient id 加 xy-chatfx- 前缀（全文档只放一份 defs）。 */
const XY_CHATFX_SVG_DEFS =
  '<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg"><defs>' +
  '<linearGradient id="xy-chatfx-hm-tile" x1="0" x2="1" y1="0" y2="0">' +
  '<stop offset="0" stop-color="#20ADFF"></stop><stop offset="1" stop-color="#0889FB"></stop>' +
  '</linearGradient>' +
  '<linearGradient id="xy-chatfx-hm-sheen" x1="0" x2="0" y1="0" y2="1">' +
  '<stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"></stop>' +
  '<stop offset="0.55" stop-color="#FFFFFF" stop-opacity="0"></stop>' +
  '</linearGradient>' +
  '<linearGradient id="xy-chatfx-us-tile" x1="0" x2="1" y1="0" y2="1">' +
  '<stop offset="0" stop-color="#9162FB"></stop><stop offset="1" stop-color="#0889FB"></stop>' +
  '</linearGradient>' +
  '<linearGradient id="xy-chatfx-us-sheen" x1="0" x2="0" y1="0" y2="1">' +
  '<stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"></stop>' +
  '<stop offset="0.55" stop-color="#FFFFFF" stop-opacity="0"></stop>' +
  '</linearGradient>' +
  '</defs></svg>'

const XY_CHATFX_SVG_ASSISTANT =
  '<svg aria-hidden="true" viewBox="0 0 60 60" xmlns="http://www.w3.org/2000/svg">' +
  '<rect fill="url(#xy-chatfx-hm-tile)" height="60" rx="16" width="60"></rect>' +
  '<rect fill="url(#xy-chatfx-hm-sheen)" height="60" rx="16" width="60"></rect>' +
  '<path d="M14 18L18 14H23V25.5H37V14H42L46 18V46H37V34.5H23V46H14Z" fill="#010B14"></path>' +
  '</svg>'

const XY_CHATFX_SVG_USER =
  '<svg aria-hidden="true" viewBox="0 0 60 60" xmlns="http://www.w3.org/2000/svg">' +
  '<rect fill="url(#xy-chatfx-us-tile)" height="60" rx="16" width="60"></rect>' +
  '<rect fill="url(#xy-chatfx-us-sheen)" height="60" rx="16" width="60"></rect>' +
  '<g fill="#010B14"><circle cx="30" cy="21.5" r="7.5"></circle>' +
  '<rect height="10" width="6" x="27" y="26"></rect>' +
  '<path d="M14 46V38L18 34H42L46 38V46Z"></path></g>' +
  '</svg>'

/* ===================== 可变状态（只经下面的函数改动） ===================== */

/** 助手名签文案；默认 ''（不注入名签，严格按本片要求「只注入头像」）。
 *  设 'HERMES' 即完整复现 worktree thread/index.tsx:144 的名签行。 */
let XY_CHATFX_ASSISTANT_NAME = ''

/** 单例安装状态；refs 计数保证多个挂载点只装一套。 */
let XY_CHATFX_ACTIVE = null

/* ===================== 纯函数 ===================== */

/** 语义等价 apps/shared/src/format.ts:7/:14/:22（999→'999'、1230→'1.2k'、1.5e6→'1.5M'，阈值 999.5 / 999950）。 */
function XY_CHATFX_compactNumber(value) {
  const num = Number(value === null || value === undefined ? 0 : value)

  if (!Number.isFinite(num) || num <= 0) {
    return '0'
  }

  const scaled = function (v, suffix) {
    return v.toFixed(1).replace(/\.0$/, '') + suffix
  }

  if (num >= 999950) {
    return scaled(num / 1000000, 'M')
  }

  if (num >= 999.5) {
    return scaled(num / 1000, 'k')
  }

  return String(Math.round(num))
}

/** 语义等价 apps/desktop/src/lib/statusbar.tsx:44-46 usageContextLabel。 */
function XY_CHATFX_contextLabel(usage) {
  if (!usage) {
    return ''
  }

  if (usage.context_max) {
    /* 不再输出「~」前缀（用户 2026-09-29 要求彻底过滤）：上游 usageContextLabel
       用它标 context_estimated（估算值），但显示出来是路径缩写的样子，用户明确要求
       任何情况下都不出现 —— 底栏与 composer 计量行共用本函数，故两处一致去掉。 */
    return (
      XY_CHATFX_compactNumber(usage.context_used || 0) +
      '/' +
      XY_CHATFX_compactNumber(usage.context_max)
    )
  }

  return usage.total > 0 ? XY_CHATFX_compactNumber(usage.total) + ' tok' : ''
}

function XY_CHATFX_raf(fn) {
  if (typeof requestAnimationFrame === 'function') {
    return requestAnimationFrame(fn)
  }

  return setTimeout(fn, 16)
}

function XY_CHATFX_cancelRaf(handle) {
  if (!handle) {
    return
  }

  if (typeof cancelAnimationFrame === 'function') {
    cancelAnimationFrame(handle)
  } else {
    clearTimeout(handle)
  }
}

function XY_CHATFX_readUserAvatar() {
  try {
    return window.localStorage.getItem(XY_CHATFX_USER_AVATAR_KEY) || ''
  } catch (error) {
    return ''
  }
}

function XY_CHATFX_writeUserAvatar(dataUrl) {
  try {
    if (dataUrl) {
      window.localStorage.setItem(XY_CHATFX_USER_AVATAR_KEY, dataUrl)
    } else {
      window.localStorage.removeItem(XY_CHATFX_USER_AVATAR_KEY)
    }
  } catch (error) {
    // 配额等失败：与 worktree store/chat-avatar.ts:29-31 同策略，仅内存态失效
  }
}

/** 直接子元素里按 data-xy-chatfx-node 找（不用 :scope，jsdom/旧引擎也能跑）。 */
function XY_CHATFX_childOf(parent, nodeName) {
  if (!parent || !parent.children) {
    return null
  }

  for (let i = 0; i < parent.children.length; i += 1) {
    if (parent.children[i].getAttribute(XY_CHATFX_ATTR_NODE) === nodeName) {
      return parent.children[i]
    }
  }

  return null
}

/* ===================== 样式 / 舞台 ===================== */

function XY_CHATFX_ensureStyle() {
  const existing = document.getElementById(XY_CHATFX_STYLE_ID)

  if (existing) {
    existing.textContent = XY_CHATFX_CSS

    return existing
  }

  const style = document.createElement('style')
  style.setAttribute('id', XY_CHATFX_STYLE_ID)
  style.textContent = XY_CHATFX_CSS
  ;(document.head || document.documentElement).appendChild(style)

  return style
}

/** 共享 defs + 唯一隐藏 file input 的宿主，挂 body：React 永远不碰它。 */
function XY_CHATFX_ensureStage() {
  const existing = document.querySelector('[' + XY_CHATFX_ATTR_STAGE + '="defs"]')

  if (existing) {
    return existing
  }

  const stage = document.createElement('div')
  stage.setAttribute(XY_CHATFX_ATTR_STAGE, 'defs')
  stage.innerHTML = XY_CHATFX_SVG_DEFS

  const input = document.createElement('input')
  input.setAttribute(XY_CHATFX_ATTR_NODE, 'avatar-input')
  input.setAttribute('accept', 'image/*')
  input.setAttribute('type', 'file')
  input.addEventListener('change', XY_CHATFX_onAvatarPicked)
  stage.appendChild(input)

  document.body.appendChild(stage)

  return stage
}

/* ===================== 头像注入 ===================== */

function XY_CHATFX_renderAvatar(el) {
  const isUser = el.getAttribute(XY_CHATFX_ATTR_AVATAR) === 'user'
  const stored = isUser ? XY_CHATFX_readUserAvatar() : ''

  if (stored) {
    el.innerHTML = ''

    const img = document.createElement('img')
    img.setAttribute('alt', '我')
    img.setAttribute('draggable', 'false')
    img.setAttribute('src', stored)
    el.appendChild(img)
  } else {
    el.innerHTML = isUser ? XY_CHATFX_SVG_USER : XY_CHATFX_SVG_ASSISTANT
  }

  if (isUser) {
    el.setAttribute('title', stored ? '点击更换头像' : '点击上传头像')
  }
}

function XY_CHATFX_openAvatarPicker() {
  const stage = XY_CHATFX_ACTIVE ? XY_CHATFX_ACTIVE.stageEl : null

  if (!stage) {
    return
  }

  const input = stage.querySelector('[' + XY_CHATFX_ATTR_NODE + '="avatar-input"]')

  if (input) {
    input.click()
  }
}

function XY_CHATFX_onAvatarPicked(event) {
  const target = event ? event.target : null
  const file = target && target.files ? target.files[0] : null

  if (!file) {
    return
  }

  const reader = new FileReader()

  reader.onload = function () {
    XY_CHATFX_writeUserAvatar(String(reader.result || ''))
    XY_CHATFX_paintAvatars()
  }

  reader.readAsDataURL(file)
  target.value = ''
}

/** 换图后重绘所有已注入的用户头像（等价 worktree $chatUserAvatar 订阅重渲染）。 */
function XY_CHATFX_paintAvatars() {
  const nodes = document.querySelectorAll(XY_CHATFX_SEL_ROW_AVATAR)

  for (let i = 0; i < nodes.length; i += 1) {
    XY_CHATFX_renderAvatar(nodes[i])
  }
}

/** 助手=span（纯装饰），用户=button（点击换图）。两者同标记，便于统一幂等与统一样式。 */
function XY_CHATFX_makeAvatarNode(kind) {
  const isUser = kind === 'user'
  const el = document.createElement(isUser ? 'button' : 'span')

  el.setAttribute(XY_CHATFX_ATTR_NODE, 'row-avatar')
  el.setAttribute(XY_CHATFX_ATTR_AVATAR, kind)
  el.setAttribute('aria-hidden', 'true')

  if (isUser) {
    el.setAttribute('type', 'button')
    el.setAttribute('tabindex', '-1')
    el.addEventListener('click', XY_CHATFX_openAvatarPicker)
  }

  XY_CHATFX_renderAvatar(el)

  return el
}

/**
 * 幂等注入：以「本行是否已有 row-avatar 直接子节点」为唯一判据，先查后插。
 * 位置 = 行的第一个子节点；insertBefore 只插入自有节点，从不移动/删除 React 的节点。
 * 布局由 CSS 按 :not([data-xy-chatfx-node]) 分列，与兄弟顺序无关，故 React 之后重排也不会错位。
 * 不往 React 管理的行上写标记属性：判据随 React 重建自然失效 → 自动重注入（自愈）。
 */
function XY_CHATFX_decorateRow(row, kind) {
  const avatar = XY_CHATFX_childOf(row, 'row-avatar')
  const hasName = Boolean(XY_CHATFX_childOf(row, 'assistant-name'))
  const wantsName = kind === 'assistant' && Boolean(XY_CHATFX_ASSISTANT_NAME)

  if (avatar && (!wantsName || hasName)) {
    return
  }

  let node = avatar

  if (!node) {
    node = XY_CHATFX_makeAvatarNode(kind)
    row.insertBefore(node, row.firstChild)
  }

  if (wantsName && !hasName) {
    const name = document.createElement('div')

    name.setAttribute(XY_CHATFX_ATTR_NODE, 'assistant-name')
    name.textContent = XY_CHATFX_ASSISTANT_NAME
    row.insertBefore(name, node.nextSibling)
  }
}

/**
 * 用户轮的图片附件右对齐。
 *
 * 上游把用户嵌入图放在 aui_user-message-root **之外**的一条整宽 flex 行里，该行没有
 * justify，于是图片贴着内容区最左边（实测行右边界 1367、用户气泡内容右边界 1291）。
 * 这里把该行右对齐，并把右内边距设成「行右边界 − 气泡内容右边界」的实测差值，
 * 使图片右缘与用户文字/气泡边界齐平 —— 差值现场量取，不写死像素。
 * 助手侧图片位于 aui_assistant-message-content 气泡内（x337 = 气泡 324 + 内边距），本就对齐，不动。
 */
function XY_CHATFX_alignUserImages() {
  const rows = document.querySelectorAll(XY_CHATFX_SEL_USER_IMAGE_ROW)

  for (let i = 0; i < rows.length; i += 1) {
    const row = rows[i]

    if (row.closest('[data-slot="aui_assistant-message-content"]')) {
      continue
    }

    const prev = row.previousElementSibling
    const root =
      prev && prev.matches && prev.matches('[data-slot="aui_user-message-root"]')
        ? prev
        : row.parentElement && row.parentElement.querySelector('[data-slot="aui_user-message-root"]')
    const bubble = root && root.querySelector(XY_CHATFX_SEL_USER_BUBBLE)

    if (!bubble) {
      continue
    }

    const box = bubble.getBoundingClientRect()
    const padRight = parseFloat(getComputedStyle(bubble).paddingRight) || 0
    /* 气泡内容右边界：气泡右缘减去内边距（实测 1327 − 36 = 1291，与用户文字块 1290 齐平）。 */
    const contentRight = box.right - padRight
    const offset = Math.max(0, Math.round(row.getBoundingClientRect().right - contentRight))

    row.style.justifyContent = 'flex-end'
    row.style.paddingRight = String(offset) + 'px'
    row.setAttribute(XY_CHATFX_ATTR_NODE, 'user-image-row')
  }
}

function XY_CHATFX_decorateThread() {
  const assistantRows = document.querySelectorAll(XY_CHATFX_SEL_ASSISTANT)

  for (let i = 0; i < assistantRows.length; i += 1) {
    XY_CHATFX_decorateRow(assistantRows[i], 'assistant')
  }

  const userRows = document.querySelectorAll(XY_CHATFX_SEL_USER)

  for (let j = 0; j < userRows.length; j += 1) {
    XY_CHATFX_decorateRow(userRows[j], 'user')
  }
}



function XY_CHATFX_pass() {
  const state = XY_CHATFX_ACTIVE

  if (!state) {
    return
  }

  /* 仪表计量两步（ensureInfoRows / paintMeter）已随 composer 计量行移除 */
  XY_CHATFX_decorateThread()
  XY_CHATFX_alignUserImages()
}

/** 只对「可能与锚点相关」的增删排批；流式 token 追加的文本节点直接忽略。 */
function XY_CHATFX_isRelevant(node) {
  if (!node || node.nodeType !== 1) {
    return false
  }

  if (node.hasAttribute(XY_CHATFX_ATTR_NODE) || node.hasAttribute(XY_CHATFX_ATTR_STAGE)) {
    return true
  }

  if (typeof node.matches === 'function') {
    if (
      node.matches(XY_CHATFX_SEL_ASSISTANT) ||
      node.matches(XY_CHATFX_SEL_USER) ||
      node.matches(XY_CHATFX_SEL_COMPOSER)
    ) {
      return true
    }
  }

  if (typeof node.querySelector === 'function') {
    if (
      node.querySelector(XY_CHATFX_SEL_ASSISTANT) ||
      node.querySelector(XY_CHATFX_SEL_USER) ||
      node.querySelector(XY_CHATFX_SEL_COMPOSER)
    ) {
      return true
    }
  }

  return false
}

function XY_CHATFX_schedule() {
  const state = XY_CHATFX_ACTIVE

  if (!state || state.frame) {
    return
  }

  state.frame = XY_CHATFX_raf(function () {
    const live = XY_CHATFX_ACTIVE

    if (live) {
      live.frame = 0
    }

    XY_CHATFX_pass()
  })
}

function XY_CHATFX_onMutations(records) {
  for (let i = 0; i < records.length; i += 1) {
    const record = records[i]

    if (XY_CHATFX_isRelevant(record.target)) {
      XY_CHATFX_schedule()

      return
    }

    const lists = [record.addedNodes, record.removedNodes]

    for (let j = 0; j < lists.length; j += 1) {
      const list = lists[j]

      for (let k = 0; k < list.length; k += 1) {
        if (XY_CHATFX_isRelevant(list[k])) {
          XY_CHATFX_schedule()

          return
        }
      }
    }
  }
}



function XY_CHATFX_teardown(state) {
  if (state.observer) {
    state.observer.disconnect()
    state.observer = null
  }

  XY_CHATFX_cancelRaf(state.frame)
  state.frame = 0

  if (state.resize && typeof window !== 'undefined' && window.removeEventListener) {
    window.removeEventListener('resize', state.resize)
    state.resize = null
  }

  /* 还原被本插件改过内联样式的官方元素（用户图片附件行）。 */
  const touched = document.querySelectorAll('[' + XY_CHATFX_ATTR_NODE + '="user-image-row"]')

  for (let i = 0; i < touched.length; i += 1) {
    touched[i].style.justifyContent = ''
    touched[i].style.paddingRight = ''
  }

  if (typeof state.unsubscribe === 'function') {
    try {
      state.unsubscribe()
    } catch (error) {
      // 忽略：退订失败不该阻断清理
    }

    state.unsubscribe = null
  }

  const injected = document.querySelectorAll(
    '[' + XY_CHATFX_ATTR_NODE + '], [' + XY_CHATFX_ATTR_STAGE + ']'
  )

  for (let i = 0; i < injected.length; i += 1) {
    const el = injected[i]

    if (el.parentNode) {
      el.parentNode.removeChild(el)
    }
  }

  const style = document.getElementById(XY_CHATFX_STYLE_ID)

  if (style && style.parentNode) {
    style.parentNode.removeChild(style)
  }
}

function XY_CHATFX_disposer() {
  let done = false

  return function () {
    if (done) {
      return
    }

    done = true

    const state = XY_CHATFX_ACTIVE

    if (!state) {
      return
    }

    state.refs -= 1

    if (state.refs > 0) {
      return
    }

    XY_CHATFX_ACTIVE = null
    XY_CHATFX_teardown(state)
  }
}

/**
 * 安装：样式 + 舞台 + MutationObserver + 首轮注入。
 * 返回 disposer（= XyChatFx 的 effect cleanup，也可交给父 agent 的 ctx.onDispose）。
 * 重复调用只加引用计数，绝不叠第二套 observer/样式。
 */
function XY_CHATFX_install() {
  if (typeof document === 'undefined' || !document.body) {
    return function () {}
  }

  if (XY_CHATFX_ACTIVE) {
    XY_CHATFX_ACTIVE.refs += 1
    XY_CHATFX_schedule()

    return XY_CHATFX_disposer()
  }

  const state = {
    frame: 0,
    observer: null,
    refs: 1,
    stageEl: null,
    unsubscribe: null,
    resize: null,
    /* usage 字段随计量行一并移除 */
  }

  XY_CHATFX_ACTIVE = state

  try {
    XY_CHATFX_ensureStyle()
    state.stageEl = XY_CHATFX_ensureStage()

    if (typeof MutationObserver === 'function') {
      state.observer = new MutationObserver(XY_CHATFX_onMutations)
      state.observer.observe(document.body, { childList: true, subtree: true })
    }

    if (typeof window !== 'undefined' && window.addEventListener) {
      state.resize = () => XY_CHATFX_schedule()
      window.addEventListener('resize', state.resize)
    }

    XY_CHATFX_pass()
  } catch (error) {
    try {
      host.logs('xianyi-ui: chatfx install 失败 ' + String((error && error.stack) || error))
    } catch (logError) {
      // 忽略
    }

    XY_CHATFX_ACTIVE = null
    XY_CHATFX_teardown(state)

    return function () {}
  }

  return XY_CHATFX_disposer()
}

/** 可选：把助手名签设成 worktree 的「HERMES」行（thread/index.tsx:144）；传 '' 关闭。 */
function XY_CHATFX_setAssistantName(name) {
  XY_CHATFX_ASSISTANT_NAME = typeof name === 'string' ? name : ''

  if (typeof document !== 'undefined') {
    const names = document.querySelectorAll('[' + XY_CHATFX_ATTR_NODE + '="assistant-name"]')

    for (let i = 0; i < names.length; i += 1) {
      names[i].textContent = XY_CHATFX_ASSISTANT_NAME
    }
  }

  XY_CHATFX_schedule()
}

/* ===================== React 外壳 ===================== */

/** 挂到 TITLEBAR / STATUSBAR 等永久挂载点；自身不渲染任何可见 UI，全部工作交给 effect。
 *  effect 返回 XY_CHATFX_install() 的 disposer：断 observer、撤订阅、移除所有注入节点与样式。
 */
function XyChatFx() {
  React.useEffect(function () {
    return XY_CHATFX_install()
  }, [])

  return null
}

/* ════════════ 主题跟随（启用即套用；用户手动换走则永久让位） ════════════ */
let XY_CTX = null

function XyThemeGuard() {
  const theme = useTheme()
  const name = theme && theme.themeName
  const switching = React.useRef(false)

  React.useEffect(() => {
    if (!XY_CTX) return
    if (XY_CTX.storage.get('themeOptOut', false)) return
    if (name === XY_THEME_NAME) return
    switching.current = true
    try {
      requestTheme(XY_THEME_NAME)
    } catch (err) {
      switching.current = false
      host.logs('xianyi-ui: requestTheme 失败 ' + String(err))
    }
  }, [])

  React.useEffect(() => {
    if (!XY_CTX || !switching.current) return
    if (name === XY_THEME_NAME) return
    // 切走了且不是本插件干的 → 用户自己的选择优先，此后不再抢占
    switching.current = false
    XY_CTX.storage.set('themeOptOut', true)
  }, [name])

  return null
}

/* ════════════ 常驻挂载点（shell 级，导航不卸载） ════════════
   三个 chrome 分片各自在 useEffect 里向 body 注入覆盖层并自管清理；
   本组件只负责给它们一个稳定挂载位与 ctx。 */
function XyRoot(props) {
  const ctx = props.ctx

  React.useEffect(() => {
    XY_CTX = ctx
    return () => {
      XY_CTX = null
      try {
        XY_CHROME_dispose()
      } catch (err) {
        host.logs('xianyi-ui: chrome dispose 失败 ' + String(err))
      }
    }
  }, [])

  return jsxs(React.Fragment, {
    children: [
      jsx(XyChromeBackdrop, ctx),
      jsx(XyChromeStatus, ctx),
      jsx(XyThemeGuard, {}),
      jsx(XyChatFx, {}),
    ],
  })
}

/* ════════════ 插件入口 ════════════ */
const XY_PLUGIN_ID = 'hermes-ui-bd' // 必须与目录名一致

export default {
  id: XY_PLUGIN_ID,
  name: 'HermesUI-BD定制',
  description: 'BD定制外观：深蓝主题 V3.4 皮肤 + 底栏状态条 + 聊天区头像与上下文计量',
  // 安装后默认启用
  defaultEnabled: true,
  register(ctx) {
    XY_CTX = ctx
    ctx.register({ id: 'theme', area: THEMES_AREA, data: XY_THEME })
    ctx.register({
      id: 'mount',
      area: STATUSBAR_AREAS.left,
      order: 900,
      render: () => jsx(XyRoot, { ctx }),
    })
  },
}
