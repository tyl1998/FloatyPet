# 「浮宠」V1.0 MVP — 测试用例设计文档

## 一、文档基础信息

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V1.0 |
| 覆盖版本 | 浮宠 V1.0 安卓 MVP |
| 测试框架 | JUnit 4 + AndroidX Test + Espresso + Compose UI Test |
| 测试层级 | 单元测试 / 集成测试 / UI 测试 / 架构验证 / 性能验收 |
| 核心参考 | [AGENT.md §6 测试验收标准](../AGENT.md)、[CLAUDE.md](../CLAUDE.md) |
| 源码基线 | commit 初始骨架：模型完整 + SpriteRenderer 占位猫脸 + 触摸手势 + 悬浮窗服务 + 首页 |

---

## 二、测试分层策略

```
┌───────────────────────────────────────────────┐
│  E2E 验收测试（手工 + 部分自动化）              │
│  素材导入 → 编辑 → 投放桌面 → 交互 → 免打扰     │
├───────────────────────────────────────────────┤
│  UI 测试（Compose UI Test + Espresso）         │
│  HomeScreen 三态 / 权限跳转 / 通知栏           │
├───────────────────────────────────────────────┤
│  集成测试（Android Instrumentation）           │
│  OverlayService 生命周期 / 触摸→行为→渲染链路  │
├───────────────────────────────────────────────┤
│  架构验证测试（纯 JVM 单元测试）               │
│  PetRenderer 接口契约 / BubbleLayer / 枚举完备性│
├───────────────────────────────────────────────┤
│  单元测试（纯 JVM 单元测试）                   │
│  模型类 / PetBehaviorEngine / FrameSequencer   │
│  / 权限逻辑 / 边缘吸附                        │
├───────────────────────────────────────────────┤
│  性能测试（Android Instrumentation + 手工）    │
│  内存 ≤80MB / 帧率 60fps / 24h 稳定性         │
└───────────────────────────────────────────────┘
```

> **原则**：能用纯 JVM 单元测试的绝不跑 Instrumentation；架构预留必须通过编译期 + 反射验证，不以"是否渲染"为验收标准。

---

## 三、单元测试（`app/src/test/`，纯 JVM，无 Android 依赖）

### 3.1 核心模型测试

#### 3.1.1 `PetAction` 枚举

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-MODEL-001 | 9 个动作语义完整 | 编译通过 | 断言 `PetAction.entries.size == 9` | 含 IDLE/WALK/SLEEP/SIT/STRETCH/GREET/SHRINK/HAPPY/SAD | P0 |
| UT-MODEL-002 | `assetDir` 返回小写目录名 | 任意 PetAction | `PetAction.GREET.assetDir` | 返回 `"greet"` | P1 |
| UT-MODEL-003 | `CORE` 仅含三组核心动作 | 编译通过 | 断言 `PetAction.CORE` | 等于 `[IDLE, GREET, SLEEP]` | P0 |
| UT-MODEL-004 | `SECONDARY` 含剩余 6 组 | 编译通过 | 断言 `PetAction.SECONDARY` | 等于 `[WALK, SIT, STRETCH, SHRINK, HAPPY, SAD]`，不含 CORE | P0 |
| UT-MODEL-005 | CORE + SECONDARY = entries | 编译通过 | 断言合并后集合 | 等于 `PetAction.entries.toSet()`，不重不漏 | P1 |

#### 3.1.2 `PetBodyPart` 枚举

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-MODEL-006 | 3 个区域完整 | 编译通过 | 断言 `PetBodyPart.entries.size == 3` | HEAD / BODY / BELLY | P0 |

#### 3.1.3 `PetAssetType` 枚举

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-MODEL-007 | 含 SPRITE_2D 和 MODEL_3D | 编译通过 | 断言 `PetAssetType.entries` | 同时包含 `SPRITE_2D` 和 `MODEL_3D` | P0 |

#### 3.1.4 `PetResponse` 数据类

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-MODEL-008 | `bubble` 默认值为 null | 构造 `PetResponse(PetAction.IDLE)` | 断言 `.bubble` | 为 `null` | P0 |
| UT-MODEL-009 | 可显式传入 `BubbleContent` | 构造带 bubble 的 PetResponse | 断言 `.bubble` | 等于传入值（验证 V1.1 数据通路） | P1 |
| UT-MODEL-010 | data class copy 保留 action | `PetResponse(PetAction.IDLE).copy(bubble=...)` | 断言 `.action` | 仍为 `IDLE` | P1 |

#### 3.1.5 `PetState` 数据类

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-MODEL-011 | 默认值校验 | `PetState()` | 断言各字段默认值 | `x=0, y=0, scale=1f, alpha=1f, action=IDLE, silent=false, hidden=false` | P0 |
| UT-MODEL-012 | `silent` 独立于 `hidden` | `PetState(silent=true, hidden=false)` | 分别断言 | silent 不影响 hidden，两者独立 | P1 |

#### 3.1.6 `BubbleContent` / `BubbleStyle` / `BubbleAnchor`

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-MODEL-013 | `BubbleContent` 默认值 | `BubbleContent("hello")` | 断言 style, durationMs, anchor | `NORMAL, 3000L, TOP` | P1 |
| UT-MODEL-014 | `BubbleStyle` 三种样式 | 编译通过 | 断言枚举数量 | NORMAL / REMINDER / EMOTION，共 3 个 | P1 |
| UT-MODEL-015 | `BubbleAnchor` 三种方位 | 编译通过 | 断言枚举数量 | TOP / LEFT / RIGHT，共 3 个 | P1 |

---

### 3.2 `FrameSequencer` 单元测试

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-SEQ-001 | `setFrames` 注册帧序列不抛异常 | 构造 FrameSequencer，准备 3 帧 Bitmap | 调用 `setFrames(IDLE, frames, 200L, LOOP)` | 不抛异常，内部缓存元数据 | P0 |
| UT-SEQ-002 | `PlayMode.LOOP` 正向循环 | 注册 3 帧 LOOP 模式 | 连续 6 次 `currentFrame()` | 返回顺序 `[0,1,2,0,1,2]` | P0 |
| UT-SEQ-003 | `PlayMode.ONCE` 正向后停在末帧 | 注册 3 帧 ONCE 模式 | 连续 4 次 `currentFrame()` | 返回 `[0,1,2,2]`（末帧停留） | P0 |
| UT-SEQ-004 | `PlayMode.PING_PONG` 乒乓循环 | 注册 3 帧 PING_PONG 模式 | 连续 6 次 `currentFrame()` | 返回 `[0,1,2,1,0,1]` | P0 |
| UT-SEQ-005 | 帧间隔控制 | 注册 3 帧，间隔 200ms | 在 150ms 处连续调用 `currentFrame()` | 始终返回第 0 帧（未到切换时间） | P1 |
| UT-SEQ-006 | 帧间隔到期后切换 | 注册 3 帧，间隔 200ms | 200ms 后调用 `currentFrame()` | 返回第 1 帧 | P1 |
| UT-SEQ-007 | `play(action)` 切换动作重置游标 | 注册 IDLE 3 帧 + GREET 2 帧，先播 IDLE 到第 2 帧 | 调用 `play(GREET)` 后 `currentFrame()` | 返回 GREET 第 0 帧 | P0 |
| UT-SEQ-008 | 无帧动作返回 null | 注册 IDLE 0 帧 | 调用 `play(IDLE)` 后 `currentFrame()` | 返回 `null`（触发整体变换回退） | P0 |
| UT-SEQ-009 | `trimMemory` 回收非当前动作帧 | 注册 IDLE 和 GREET 各 3 帧，IDLE 播放中 | 调用 `trimMemory()` | GREET 帧被 recycle，IDLE 帧保留 | P1 |

---

### 3.3 `PetBehaviorEngine` 单元测试

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-BEH-001 | 单击头部 → GREET | 构造 PetBehaviorEngine | 调用 `onTouch(HEAD)` | `PetResponse(PetAction.GREET)`，bubble 为 null | P0 |
| UT-BEH-002 | 单击身体 → HAPPY | 同上 | 调用 `onTouch(BODY)` | `PetResponse(PetAction.HAPPY)`，bubble 为 null | P0 |
| UT-BEH-003 | 单击腹部 → SHRINK | 同上 | 调用 `onTouch(BELLY)` | `PetResponse(PetAction.SHRINK)`，bubble 为 null | P0 |
| UT-BEH-004 | 双击 → HAPPY | 同上 | 调用 `onDoubleTap()` | `PetResponse(PetAction.HAPPY)`，bubble 为 null | P0 |
| UT-BEH-005 | 解锁触发 → GREET | 实现 `onStateChanged(Unlocked)` | 调用 | `PetResponse(PetAction.GREET)` | P1 |
| UT-BEH-006 | 充电触发 → SLEEP | 实现 `onStateChanged(PowerConnected)` | 调用 | `PetResponse(PetAction.SLEEP)` | P1 |
| UT-BEH-007 | 低电触发 → SAD | 实现 `onStateChanged(BatteryLow)` | 调用 | `PetResponse(PetAction.SAD)` | P1 |
| UT-BEH-008 | 空闲超时 → SLEEP 或 STRETCH | 实现 `onStateChanged(IdleTimeout)` | 调用 | 返回 IDLE 之外的有效 PetResponse | P1 |
| UT-BEH-009 | 晴天 → HAPPY | 实现 `onStateChanged(WeatherChanged("sunny"))` | 调用 | `PetResponse(PetAction.HAPPY)` | P1 |
| UT-BEH-010 | 雨天 → SHRINK | 实现 `onStateChanged(WeatherChanged("rainy"))` | 调用 | `PetResponse(PetAction.SHRINK)` 或 SAD | P1 |
| UT-BEH-011 | 高温 → SIT | 实现 `onStateChanged(WeatherChanged("hot"))` | 调用 | `PetResponse(PetAction.SIT)` | P1 |
| UT-BEH-012 | 低温 → SHRINK | 实现 `onStateChanged(WeatherChanged("cold"))` | 调用 | `PetResponse(PetAction.SHRINK)` | P1 |
| UT-BEH-013 | 清晨 → STRETCH | 实现 `onStateChanged(WeatherChanged("morning"))` | 调用 | `PetResponse(PetAction.STRETCH)` | P1 |
| UT-BEH-014 | 深夜 → SLEEP | 实现 `onStateChanged(WeatherChanged("night"))` | 调用 | `PetResponse(PetAction.SLEEP)` | P1 |
| UT-BEH-015 | 社交 APP → IDLE | 实现 `onStateChanged(ForegroundAppCategory("SOCIAL"))` | 调用 | `PetResponse(PetAction.IDLE)` | P2 |
| UT-BEH-016 | 游戏 APP → SHRINK | 实现 `onStateChanged(ForegroundAppCategory("GAME"))` | 调用 | `PetResponse(PetAction.SHRINK)` | P2 |
| UT-BEH-017 | 办公 APP → 静默 | 实现 `onStateChanged(ForegroundAppCategory("WORK"))` | 调用 | 返回 action 与静默状态标记 | P2 |
| UT-BEH-018 | MVP 阶段所有 `PetResponse.bubble` 恒为 null | 遍历所有 onTouch / onStateChanged | 断言 `.bubble` | 全部为 `null` | P0 |

---

### 3.4 边缘吸附逻辑测试（从 `OverlayService.snapToEdge` 提取纯逻辑）

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-SNAP-001 | 中心偏左 → 吸附左侧 | 屏幕宽 1080px，View 宽 140px，中心在 400px | 计算吸附后 x | `x = 8dp`（约 22px @ 2.75density） | P1 |
| UT-SNAP-002 | 中心偏右 → 吸附右侧 | 屏幕宽 1080px，View 宽 140px，中心在 800px | 计算吸附后 x | `x = 1080 - 140 - margin` | P1 |
| UT-SNAP-003 | 中心恰好在正中 | 屏幕宽 1080px，View 宽 140px，中心 540px | 计算吸附后 x | 按规则偏左或偏右均可，但须在边缘 | P2 |

---

### 3.5 权限逻辑测试

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UT-PERM-001 | `OverlayPermission.createSettingsIntent` 不含 null | 模拟 Context | 调用 | 返回的 Intent 的 data 包含 `package:` 前缀 | P1 |
| UT-PERM-002 | 权限分层：2 强制 + 2 可选 | 检视 AndroidManifest.xml | 解析权限声明 | INTERNET/FOREGROUND_SERVICE/POST_NOTIFICATIONS 为系统权限；仅 SYSTEM_ALERT_WINDOW + 存储 + READ_PHONE_STATE + PACKAGE_USAGE_STATS 为用户授权 | P1 |

---

## 四、集成测试（`app/src/androidTest/`，需 Android 设备/模拟器）

### 4.1 悬浮窗服务生命周期

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| IT-SRV-001 | 启动 OverlayService → isRunning = true | 已授权悬浮窗权限 | `OverlayService.start(context)` | `OverlayService.isRunning == true` | P0 |
| IT-SRV-002 | 停止 OverlayService → isRunning = false | Service 运行中 | `OverlayService.stop(context)` | `OverlayService.isRunning == false` | P0 |
| IT-SRV-003 | 重复 start 不创建第二个 Service | Service 已在运行 | 再次 `start()` | `isRunning` 仍为 true，无 crash | P1 |
| IT-SRV-004 | onDestroy 释放资源 | Service 运行中 | 调用 `stop` | 触发 renderer.dispose()，bubbleLayer.dismiss()，unregisterReceiver | P1 |
| IT-SRV-005 | `START_NOT_STICKY` 不自启 | Service 被系统杀死 | 等待系统回收 | Service 不自启（验证 onStartCommand 返回值） | P1 |
| IT-SRV-006 | 前台通知存在且渠道为 LOW | Service 运行中 | 检查通知 | 通知存在，importance=LOW，可被用户隐藏 | P1 |

### 4.2 渲染循环

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| IT-RDR-001 | 亮屏时 Choreographer 循环运行 | Service 启动，屏幕亮 | 等待 500ms | `looping == true`，frameCallback 被持续调用 | P0 |
| IT-RDR-002 | 息屏停止渲染循环 | Service 运行中 | 发送 `ACTION_SCREEN_OFF` | `looping == false`，frameCallback 不再被 post | P0 |
| IT-RDR-003 | 亮屏恢复渲染循环 | Service 运行中（已息屏） | 发送 `ACTION_SCREEN_ON` | `looping == true`，frameCallback 恢复 | P0 |

### 4.3 触摸 → 行为引擎 → 渲染链路

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| IT-TOUCH-001 | 点击头部区域 → GREET | PetOverlayView 显示中 | 在视图上 1/3 区域模拟 ACTION_DOWN+UP | renderer 收到 `playAction(GREET)`，视图 invalidate | P0 |
| IT-TOUCH-002 | 点击身体区域 → HAPPY | 同上 | 在视图中 1/3 区域操作 | renderer 收到 `playAction(HAPPY)` | P0 |
| IT-TOUCH-003 | 点击腹部区域 → SHRINK | 同上 | 在视图下 1/3 区域操作 | renderer 收到 `playAction(SHRINK)` | P0 |
| IT-TOUCH-004 | 点击宠物外部区域 → 无响应 | 同上 | 点击视图边缘空白区 | hitTest 返回 null，不调用 playAction | P1 |
| IT-TOUCH-005 | 双击 → HAPPY | PetOverlayView 显示中 | 模拟双击 | renderer 收到 `playAction(HAPPY)` | P0 |
| IT-TOUCH-006 | 拖拽移动悬浮窗 | PetOverlayView 显示中 | ACTION_DOWN → MOVE 超过 touchSlop | `onDrag` 回调触发，窗口位置更新 | P0 |
| IT-TOUCH-007 | 拖拽松手 → 边缘吸附 | 悬浮窗被拖到屏幕中间 | ACTION_UP | `onDragEnd` 回调触发，窗口 x 坐标变为边缘 | P0 |
| IT-TOUCH-008 | 轻触（未超过 touchSlop）不算拖拽 | PetOverlayView 显示中 | ACTION_DOWN → 微小 MOVE → UP | 不触发 onDrag，触发点击事件 | P1 |
| IT-TOUCH-009 | 非 idle 动作播完后回 idle | 触发 GREET 动作 | 等待 1.2s 后 | `currentAction` 恢复为 `IDLE` | P1 |

### 4.4 `SpriteRenderer` 集成测试

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| IT-SPRITE-001 | `init` + `setViewport` 不抛异常 | 构造 SpriteRenderer | 调用 `init(surface, 200, 200)` | viewW=200, viewH=200，无 crash | P0 |
| IT-SPRITE-002 | `loadPet(SPRITE_2D)` 接受 | 同上 | 调用 `loadPet("/fake", SPRITE_2D)` | 不抛异常 | P1 |
| IT-SPRITE-003 | `loadPet(MODEL_3D)` 应 reject | 同上 | 调用 `loadPet("/fake", MODEL_3D)` | 抛出 `IllegalArgumentException` | P0 |
| IT-SPRITE-004 | `hitTest` 命中头部区域 | view 200x200 | 调用 `hitTest(100, 30)` | 返回 `PetBodyPart.HEAD` | P0 |
| IT-SPRITE-005 | `hitTest` 命中身体区域 | view 200x200 | 调用 `hitTest(100, 90)` | 返回 `PetBodyPart.BODY` | P0 |
| IT-SPRITE-006 | `hitTest` 命中腹部区域 | view 200x200 | 调用 `hitTest(100, 150)` | 返回 `PetBodyPart.BELLY` | P0 |
| IT-SPRITE-007 | `hitTest` 未命中返回 null | view 200x200 | 调用 `hitTest(0, 0)` | 返回 `null` | P1 |
| IT-SPRITE-008 | `applyTransform` 更新变换参数 | SpriteRenderer | 调用 `applyTransform(1.5f, 10f, -5f, 15f)` | scale=1.5, dx=10, dy=-5, rotation=15 | P1 |
| IT-SPRITE-009 | `dispose` 调用 `sequencer.trimMemory()` | SpriteRenderer | 调用 `dispose()` | 不抛异常，资源释放 | P1 |

### 4.5 `BubbleLayer` 集成测试

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| IT-BUB-001 | `NoOpBubbleLayer.show` 无副作用 | NoOpBubbleLayer | 调用 `show(BubbleContent("test"))` | 不抛异常，`isShowing()` 返回 false | P0 |
| IT-BUB-002 | `NoOpBubbleLayer.dismiss` 无副作用 | 同上 | 调用 `dismiss()` | 不抛异常 | P0 |
| IT-BUB-003 | `NoOpBubbleLayer.isShowing` 恒为 false | 同上 | 调用 `isShowing()` | 返回 `false` | P0 |

---

## 五、UI 测试（`app/src/androidTest/`，Compose UI Test）

### 5.1 首页 UI 状态切换

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UI-HOME-001 | `Loading` 态显示加载指示器 | `uiState = Loading` | 渲染 HomeScreen | 页面显示 `CircularProgressIndicator` | P0 |
| UI-HOME-002 | `Empty` 态显示领养引导 | `uiState = Empty` | 渲染 HomeScreen | 显示 "领养你的第一只桌宠" + "开始领养" 按钮 | P0 |
| UI-HOME-003 | `Empty` 态点击「开始领养」 | `uiState = Empty` | 点击按钮 | `onAdoptPet` 回调触发 | P1 |
| UI-HOME-004 | `Ready` 态显示宠物信息 | `uiState = Ready(petName="小橘", statusText="悠闲", overlayRunning=false)` | 渲染 HomeScreen | 显示 "我的桌宠" + 卡片含 "小橘" + "悠闲" | P0 |
| UI-HOME-005 | `Ready` 态 overlayRunning=false → 按钮文案"把宠物放到桌面" | overlayRunning=false | 渲染按钮 | 按钮文案为 "把宠物放到桌面" | P0 |
| UI-HOME-006 | `Ready` 态 overlayRunning=true → 按钮文案"宠物已在桌面·点击收起" | overlayRunning=true | 渲染按钮 | 按钮文案为 "宠物已在桌面 · 点击收起"，颜色变化 | P0 |
| UI-HOME-007 | 点击「放到桌面」→ 触发 onToggleOverlay | overlayRunning=false | 点击按钮 | `onToggleOverlay` 回调触发 | P0 |

### 5.2 权限引导流程（MainActivity）

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| UI-PERM-001 | 已授权悬浮窗 → 直接启动 Service | 悬浮窗权限已开 | 点击「放到桌面」 | Service 启动，HomeViewModel 收到 `setOverlayRunning(true)` | P0 |
| UI-PERM-002 | 未授权悬浮窗 → 跳转系统设置 | 悬浮窗权限未开 | 点击「放到桌面」 | 跳转系统设置页（`ACTION_MANAGE_OVERLAY_PERMISSION`） | P0 |
| UI-PERM-003 | 从设置页返回且已授权 → 启动 Service | 首次进入设置页开启权限后返回 | Activity 回前台 | Service 启动，toast 不显示 | P1 |
| UI-PERM-004 | 从设置页返回且未授权 → Toast 提示 | 进入设置页但未开启权限，返回 | Activity 回前台 | Toast "未开启悬浮窗权限，宠物无法显示" | P1 |
| UI-PERM-005 | 已运行时再次点击 → 停止 Service | Service 运行中 | 点击「点击收起」 | Service 停止，`setOverlayRunning(false)` | P0 |

---

## 六、架构验证测试（纯 JVM + 反射）

> 目标：验证 `PetRenderer` 接口契约、`BubbleLayer` 接口、枚举覆盖度是否满足 2D→3D 扩展要求。
> 所有架构测试不依赖 Android 运行时，可在 CI 的 JVM 环境执行。

### 6.1 `PetRenderer` 接口契约

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| AV-RDR-001 | 接口含 8 个方法 | 反射 `PetRenderer` | 断言 declaredMethods 数量 | `init`, `setViewport`, `loadPet`, `playAction`, `render`, `drawTo`, `hitTest`, `applyTransform`, `dispose` 均存在 | P0 |
| AV-RDR-002 | `playAction` 接受 `PetAction` 参数 | 反射方法签名 | 检查参数类型 | 参数为 `PetAction`（非字符串/Int 魔法数） | P0 |
| AV-RDR-003 | `hitTest` 返回 `PetBodyPart?` | 反射方法签名 | 检查返回类型 | 返回 `PetBodyPart?`（可为 null） | P0 |
| AV-RDR-004 | `loadPet` 接受 `PetAssetType` 参数 | 反射方法签名 | 检查参数类型 | 参数为 `PetAssetType`（非字符串魔法数） | P0 |
| AV-RDR-005 | `SpriteRenderer` 实现 `PetRenderer` | 编译期检查 | `SpriteRenderer() is PetRenderer` | true | P0 |
| AV-RDR-006 | `FrameSequencer` 不实现 `PetRenderer`（解耦） | 编译期检查 | `FrameSequencer() is PetRenderer` | false | P0 |

### 6.2 `BubbleLayer` 接口契约

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| AV-BUB-001 | 接口含 3 个方法 | 反射 `BubbleLayer` | 断言 declaredMethods | `show(BubbleContent)`, `dismiss()`, `isShowing(): Boolean` 均存在 | P0 |
| AV-BUB-002 | `NoOpBubbleLayer` 实现 `BubbleLayer` | 编译期检查 | `NoOpBubbleLayer() is BubbleLayer` | true | P0 |
| AV-BUB-003 | `NoOpBubbleLayer` 零内存占用 | 构造 1000 个 NoOpBubbleLayer | 对比前后堆内存 | 增长 < 1KB（纯空实现，无内部状态） | P1 |

### 6.3 枚举覆盖度

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| AV-ENUM-001 | `PetAction` 覆盖 PRD 要求的 9 个动作 | 反射枚举常量 | 比对 | IDLE/WALK/SLEEP/SIT/STRETCH/GREET/SHRINK/HAPPY/SAD | P0 |
| AV-ENUM-002 | `PetBodyPart` 覆盖 PRD 要求的 3 个区域 | 反射枚举常量 | 比对 | HEAD/BODY/BELLY | P0 |
| AV-ENUM-003 | `PetAssetType` 预留 `MODEL_3D` | 反射枚举常量 | 断言存在 `MODEL_3D` | 存在 | P0 |
| AV-ENUM-004 | `BehaviorTrigger` 覆盖所有触发源 | 反射 sealed 子类 | 比对 PRD §2.4 | Unlocked/PowerConnected/BatteryLow/IdleTimeout/WeatherChanged/ForegroundAppCategory 均存在 | P1 |

### 6.4 渲染隔离验证

| 用例 ID | 测试项 | 前置条件 | 操作 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| AV-ISO-001 | `PetOverlayView` 只持有 `PetRenderer` 接口 | 检查 `PetOverlayView` 字段类型 | 断言 renderer 字段类型 | 为 `PetRenderer`（非 `SpriteRenderer`） | P0 |
| AV-ISO-002 | `OverlayService` 只持有 `PetRenderer` 接口 | 检查 `OverlayService` 字段类型 | 断言 renderer 字段类型 | 为 `PetRenderer`（非 `SpriteRenderer`） | P0 |
| AV-ISO-003 | `OverlayService` 只持有 `BubbleLayer` 接口 | 检查字段类型 | 断言 bubbleLayer 字段类型 | 为 `BubbleLayer`（非 `NoOpBubbleLayer`） | P0 |
| AV-ISO-004 | `PetOverlayView.onDraw` 只调 `renderer.drawTo()` | 源码审查 | 检查 onDraw 方法体 | 不直接 new Canvas 或 drawBitmap | P1 |
| AV-ISO-005 | `HomeViewModel` / `HomeScreen` 不 import render 包 | 静态 import 检查 | grep import | 不包含 `com.floatypet.overlay.render` | P1 |

---

## 七、性能验收测试（Android Instrumentation + 手工）

> 对应 AGENT.md §5.1，一票否决项。

| 用例 ID | 测试项 | 验收标准 | 测量方法 | 优先级 |
| --- | --- | --- | --- | --- |
| PF-MEM-001 | 悬浮窗进程后台内存占用 | ≤80MB | `adb shell dumpsys meminfo <pid>`，悬浮窗运行 1 小时后采样 | P0 |
| PF-MEM-002 | 帧序列懒加载——非当前动作帧及时回收 | 切换动作后内存不持续增长 | 连续切换 IDLE→GREET→SLEEP→IDLE 10 次，观察内存曲线 | P1 |
| PF-MEM-003 | NoOpBubbleLayer 不增加内存 | 实例化前后内存无显著差异 | `dumpsys meminfo` 对比 | P2 |
| PF-FPS-001 | 渲染帧率 | 稳定 60fps，无肉眼卡顿 | `adb shell dumpsys gfxinfo <package>` 或 Systrace | P0 |
| PF-FPS-002 | 息屏时帧回调停止 | 息屏后 Choreographer 不再 post | 日志计数：息屏后帧回调次数 = 0 | P0 |
| PF-PWR-001 | 亮屏整机功耗 | ≤1%/小时 | 电池监测工具（Battery Historian），亮屏悬浮窗运行 3 小时 | P0 |
| PF-PWR-002 | 待机功耗 | ≤0.5%/小时 | 息屏 4 小时电量消耗对比（关闭悬浮窗 vs 开启） | P1 |
| PF-STB-001 | 24 小时连续运行无崩溃 | 0 crash，0 ANR | monkey 测试或长时间手工运行 + logcat 监控 | P0 |
| PF-STB-002 | `START_NOT_STICKY` 不自启 | 被系统杀死后不自动重启 | `adb shell am force-stop` + 等待 5 分钟 | P1 |
| PF-STB-003 | 关联启动检查 | 打开本 App 后，其他 App 不被唤醒 | `dumpsys activity processes` 检查进程列表 | P1 |
| PF-TOUCH-001 | 触摸交互响应延迟 | ≤100ms | `systrace` 或 Instrumentation 测试录制 ACTION_DOWN → playAction 调用时间 | P0 |

---

## 八、功能验收测试（手工 + 按 PRD §6 逐项核对）

### 8.1 素材获取流程

| 用例 ID | 测试项 | 步骤 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- |
| E2E-IMP-001 | 本地导入单张静态图 | 相册选图 → 裁剪编辑 → 投放桌面 | 宠物显示 + idle 呼吸动画正常 | P0 |
| E2E-IMP-002 | 本地导入 GIF 动图 | 选 GIF → 自动拆帧 → 用户分组 → 投放 | 各动作帧序列播放正确 | P0 |
| E2E-IMP-003 | 导入后素材存储在 App 内部目录 | 导入完成 → 检查文件系统 | 素材在 `{internal}/pet_data/{pet_id}/` 下，系统相册不可见 | P1 |
| E2E-IMP-004 | 抠图功能 | 导入图片 → 进入编辑页 → 使用橡皮擦 | 背景区域透明化生效 | P1 |
| E2E-IMP-005 | 锚点调整 | 编辑页拖拽锚点 → 投放桌面 | 宠物显示位置以锚点为基准 | P1 |

### 8.2 AI 生成流程（依赖有效 AI 服务配置）

| 用例 ID | 测试项 | 步骤 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- |
| E2E-AI-001 | 未配置 AI → 按钮置灰 | 素材编辑页 → 查看「AI 生成」 | 按钮置灰 + 提示 "配置 AI 服务后可用" | P0 |
| E2E-AI-002 | 配置无效 API Key → 报错 | 填入错误 Key → 保存 | `/models` 验证返回错误提示（如 401） | P0 |
| E2E-AI-003 | 配置有效 API Key → 验证通过 | 填入正确 Key → 保存 | 返回「配置成功」 | P0 |
| E2E-AI-004 | API Key 本地加密存储 | 配置成功 → 检查 DataStore | Key 以 AES 加密存储，非明文 | P1 |
| E2E-AI-005 | 核心帧生成（第一阶段） | 上传 3 张宠物照 → 生成 | idle/greet/sleep 三组帧生成完成 → 通知 + 可投放桌面 | P0 |
| E2E-AI-006 | 后台补全（第二阶段） | 核心帧投放桌面后 | 6 组补充动作逐个生成入库 → 全部完成后通知 | P0 |
| E2E-AI-007 | 单帧失败重试 | 模拟某帧生成失败 | 自动重试最多 2 次 → 仍失败则标记 "未生成" | P1 |
| E2E-AI-008 | 生成超时 | 模拟网络延迟 90s+ | 该动作标记 "未生成"，对应交互用 idle 回退 | P1 |
| E2E-AI-009 | 角色描述提取与复用 | 首次生成 | 各帧 prompt 含相同角色描述 | P2 |
| E2E-AI-010 | 中途退出 APP 不丢进度 | 第二阶段生成中 force-stop APP → 重启 | 已生成的帧保留，未完成的继续生成 | P1 |

### 8.3 悬浮窗触摸交互

| 用例 ID | 测试项 | 步骤 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- |
| E2E-TOUCH-001 | 全部 7 种触摸交互 | 逐一执行点击/双击/滑动/戳/缩放/长按 | 每种交互反馈正确，响应 ≤100ms | P0 |
| E2E-TOUCH-002 | 双指缩放保存 | 双指缩放调整大小 → 杀进程重启 | 宠物大小保持缩放后的值 | P1 |
| E2E-TOUCH-003 | 长按 2s 进入拖拽态 | 长按宠物 2s | 进入拖拽态，出现半透明按钮 | P1 |
| E2E-TOUCH-004 | 新手引导提示 | 首次投放桌面 | 显示 "长按可拖拽移动，双击可以互动哦～"，3s 后消失 | P1 |
| E2E-TOUCH-005 | 新手引导仅展示 1 次 | 第二次投放桌面 | 不显示引导提示 | P2 |
| E2E-TOUCH-006 | 音效默认关闭 | 点击/双击 | 无音效播放 | P1 |
| E2E-TOUCH-007 | 音效开启后播放 | 设置开启音效 → 双击宠物 | 播放蹭手音效 | P2 |

### 8.4 自主动作

| 用例 ID | 测试项 | 步骤 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- |
| E2E-AUTO-001 | 解锁触发 GREET | 锁屏 → 解锁 | 宠物播放 GREET | P0 |
| E2E-AUTO-002 | 充电触发 SLEEP | 插入充电线 | 宠物播放 SLEEP + 移动到屏幕下方 | P0 |
| E2E-AUTO-003 | 低电触发 SAD | 电量 < 20% | 宠物播放 SAD | P0 |
| E2E-AUTO-004 | 1 小时内自主动作 ≤3 次 | 连续触发充电/低电/解锁 | 1 小时内总触发频率 ≤3 次 | P0 |
| E2E-AUTO-005 | 无操作 5 分钟触发 | 桌面无操作 5 分钟 | 触发空闲动作 | P1 |
| E2E-AUTO-006 | 天气感知（晴天） | 天气 API 返回晴天 | 宠物播放 HAPPY | P1 |
| E2E-AUTO-007 | 天气感知（雨天） | 天气 API 返回雨天 | 宠物播放 SHRINK/SAD | P1 |
| E2E-AUTO-008 | 关闭天气感知 → 不发网络请求 | 设置关闭天气感知 | 无天气 API 请求发出 | P1 |
| E2E-AUTO-009 | 清晨时间段 | 系统时钟 6:00-9:00 | 宠物播放 STRETCH | P1 |
| E2E-AUTO-010 | 深夜时间段 | 系统时钟 23:00+ | 宠物播放 SLEEP | P1 |

### 8.5 免打扰体系

| 用例 ID | 测试项 | 步骤 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- |
| E2E-DND-001 | 免打扰时段内静默 | 设置时段 9:00-18:00，当前 12:00 | 宠物缩小 + 半透明 + 不自主动作 | P0 |
| E2E-DND-002 | 免打扰时段外恢复 | 当前时间 18:01 | 宠物恢复正常大小和透明度 | P0 |
| E2E-DND-003 | 黑名单 APP 打开 → 隐藏 | 添加抖音到黑名单，打开抖音 | 宠物自动隐藏 | P0 |
| E2E-DND-004 | 退出黑名单 APP → 恢复 | 从抖音切回桌面 | 宠物恢复显示 | P0 |
| E2E-DND-005 | 黑名单优先级高于陪伴反应 | 微信在黑名单 + 社交类 | 宠物隐藏（不做社交陪伴动作） | P0 |
| E2E-DND-006 | 通话中隐藏（需 READ_PHONE_STATE） | 有来电 | 宠物自动完全隐藏 | P1 |
| E2E-DND-007 | 通话结束恢复 | 挂断电话 | 宠物恢复显示 | P1 |
| E2E-DND-008 | 通知栏快捷开关 | 下拉通知栏 → 点击隐藏 | 宠物即时隐藏/显示 | P1 |
| E2E-DND-009 | 未授权通话权限 → 通话时不隐藏 | 拒绝 READ_PHONE_STATE | 通话中宠物仍在桌面（优雅降级） | P1 |
| E2E-DND-010 | 未授权 Usage Access → 黑名单不生效 | 拒绝 PACKAGE_USAGE_STATS | 打开黑名单 APP 宠物不隐藏（优雅降级） | P1 |

### 8.6 权限流程

| 用例 ID | 测试项 | 步骤 | 预期结果 | 优先级 |
| --- | --- | --- | --- | --- |
| E2E-PERM-001 | 「用到才申请」——存储权限 | 首次导入照片 | 触发系统原生存储权限弹窗 | P0 |
| E2E-PERM-002 | 拒绝存储权限 → 无法导入 | 拒绝存储权限 | 提示无法导入，可重试 | P0 |
| E2E-PERM-003 | 悬浮窗权限引导页 | 点击「放到桌面」且未授权 | 展示引导页 → 跳转系统设置 | P0 |
| E2E-PERM-004 | 拒绝悬浮窗权限 → 二次提示 | 跳转设置但不开权限，返回 App | "没有悬浮窗权限宠物无法显示" + 「暂不开启」 | P0 |
| E2E-PERM-005 | 首页轻量提示条 | 每次打开 App 悬浮窗未开启 | 顶部展示轻量提示条（非弹窗），不阻断操作 | P1 |
| E2E-PERM-006 | 可选权限不主动弹窗 | 使用核心功能 | 无通话权限 / Usage Access 弹窗骚扰 | P0 |
| E2E-PERM-007 | 无开屏弹窗/升级弹窗/评分弹窗 | 打开 App | 无任何系统级打扰弹窗 | P1 |

---

## 九、兼容性测试矩阵

| 厂商 | 代表机型 | Android 版本 | 验证项 |
| --- | --- | --- | --- |
| 华为 | P60 / Mate 60 | 13/14 | 悬浮窗不受杀后台影响；前台服务保活 |
| 小米 | 小米 14 / Redmi K70 | 14/15 | 自启动管理 + 悬浮窗权限（MIUI 可能有额外开关） |
| OPPO | Find X7 / Reno 11 | 14 | ColorOS 后台管理 + 悬浮窗兼容 |
| vivo | X100 / S18 | 14 | OriginOS 悬浮窗限制 |
| 三星 | S24 / A55 | 14 | One UI 悬浮窗 + 多窗口模式 |
| 原生 | Pixel 8 / 模拟器 | 14/15 | 基线验证 |

> 最低支持 Android 10（API 29），覆盖主流厂商。

---

## 十、测试数据准备

### 10.1 素材测试数据

| 数据类型 | 用途 | 准备方式 |
| --- | --- | --- |
| `frame_01.png` ~ `frame_05.png` (200x200 透明 PNG) | FrameSequencer 单元测试 | 代码生成 `Bitmap.createBitmap(200, 200, ARGB_8888)` |
| 单张宠物照片 (JPEG, 1024x1024) | 本地导入流程测试 | 放入 `src/androidTest/assets/` |
| 测试 GIF (3-5 帧) | 拆帧功能测试 | 放入测试 assets |
| 无效文件 (txt 改后缀 png) | 异常路径测试 | 测试 assets |

### 10.2 AI 服务测试配置

| 配置项 | 用途 |
| --- | --- |
| 有效 OpenAI 兼容 API Key + Endpoint | AI 生成正常流程 |
| 无效 Key (随意字符串) | 401 错误验证 |
| 限流 Key (429 触发) | 重试逻辑验证 |
| 慢速 Mock Server (延迟 120s) | 超时逻辑验证 |

---

## 十一、测试执行计划

| 阶段 | 周期 | 测试类型 | 负责人 |
| --- | --- | --- | --- |
| 开发期（第 2-3 周） | 随功能开发同步 | 单元测试 + 架构验证测试 + 核心集成测试 | 开发 |
| 测试期（第 4 周） | 第 4 周周一~周三 | UI 测试 + 集成测试补全 + 兼容性冒烟 | QA |
| 测试期（第 4 周） | 第 4 周周四~周五 | 性能验收 + 24h 稳定性 + E2E 功能验收 | QA + 开发 |
| 灰度期（第 5 周） | 持续 | 线上监控（crash 率、ANR 率、内存） | 全员 |

---

## 十二、风险与边界说明

1. **AI 生成测试**（E2E-AI-xxx）依赖外部 AI 服务，需准备测试用 API Key，不适合 CI 自动化，归入手工验收。
2. **性能测试**（PF-xxx）需真实设备，模拟器 GPU 行为与真机差异大，不作为性能基准。
3. **天气感知**依赖和风天气 API 可用性，测试需覆盖 API 不可用时的降级（不崩，不影响核心功能）。
4. **Usage Access** 权限在部分厂商 ROM 上可能有额外限制（如 MIUI 的 "应用行为记录"），兼容性需人工覆盖。
5. **FrameSequencer** 的 UT-SEQ 测试在 `TODO` 实现前预期失败，作为 TDD 驱动开发的脚手架。
6. 本测试用例文档不覆盖 V1.1 的 3D 渲染 / 气泡内容功能，仅验证 MVP 架构预留是否符合 PRD §6 的「3D 架构预留」「气泡架构预留」验收项。