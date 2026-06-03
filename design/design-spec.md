# 设计规范 — 「我的桌面宠」

> 视觉主调：**暖色 · 圆润 · 宠物陪伴感**。面向 18-35 岁，避开冷科技蓝。
> 本规范为 MVP 视觉基线，实现时映射到 Compose `MaterialTheme` 的 ColorScheme / Shape / Typography。

## 1. 色板（Design Tokens）

| Token | 色值 | 用途 |
| --- | --- | --- |
| `bg` | `#FBF6F0` | 暖米白，页面背景 |
| `surface` | `#FFFFFF` | 卡片/列表面 |
| `primary` | `#FF8A65` | 珊瑚橘，主按钮/强调 |
| `primaryDeep` | `#F4663B` | 主色加深，文字/按下态 |
| `ink` | `#3A2E2A` | 暖墨，主文字 |
| `ink2` | `#8A7A72` | 次要文字/说明 |
| `line` | `#F0E6DD` | 分隔线/描边 |
| `mint` | `#7FC8A9` | 薄荷绿，成功/陪伴/在线状态 |
| `chipBg` | `#FFF1EA` | chip/次要按钮底 |

阴影：`0 8px 24px rgba(180,130,100,.12)`（卡片）；主按钮 `0 6px 16px rgba(244,102,59,.35)`。

## 2. 形状（Shape）
- 卡片圆角 22dp；大卡/编辑区 24-26dp；按钮 30dp（pill）；chip 20dp；缩略图 18dp。
- 整体走 Material 3 Expressive 的大圆角风格。

## 3. 组件约定
- **主按钮**：实心珊瑚橘 + 白字 + 阴影（pill）。
- **次按钮**：白底 + 珊瑚描边 + 珊瑚字（ghost）。
- **开关**：开=珊瑚橘，关=暖灰 `#E2D6CC`。
- **状态点**：done=薄荷绿，进行中=珊瑚橘，等待=暖灰。

## 4. 原型文件
- 第一批：[mockup.html](mockup.html) / [mockup.png](mockup.png) —— 悬浮窗、首页、素材编辑、AI 生成、免打扰
- 第二批：[mockup2.html](mockup2.html) / [mockup2.png](mockup2.png) —— AI 配置、设置、引导、动作库、触摸反馈

> 占位说明：原型中宠物用 emoji 占位，真实 App 为用户导入 / AI 生成的逐帧 PNG。
