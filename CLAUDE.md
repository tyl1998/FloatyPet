# CLAUDE.md — 「浮宠」开发规范

> 本文件是项目的**硬约束**。所有代码必须遵守。需求细节见 [AGENT.md](AGENT.md)（PRD）。
> 规范与 PRD 冲突时，以本文件为准；本文件未覆盖处，遵循通用 Android skill 与 Google 官方架构指南。

---

## 1. 项目定位（一句话）

纯客户端 Android 桌面宠物 App：用户导入或用自带 AI 服务逐帧生成宠物 → 悬浮窗常驻桌面交互 → 渐进式免打扰。**无服务端、无埋点、无广告。**

---

## 2. 技术栈（不可偏离）

| 维度 | 选型 | 说明 |
| --- | --- | --- |
| 语言 | **Kotlin only** | 不写新增 Java 文件 |
| 异步 | **Coroutines + Flow** | 禁用 RxJava、AsyncTask、裸 Thread（渲染线程除外） |
| 设置界面 UI | **Jetpack Compose** | App 主界面/设置页全部用 Compose，禁用 XML 布局 |
| 悬浮窗渲染 | **Canvas 自绘 + FrameSequencer** | 不引入 Spine/Lottie 等动画库 |
| 架构 | **MVVM + 轻量分层** | ViewModel + Repository，不上完整 Clean Architecture 多模块 |
| 依赖注入 | **Hilt** | |
| 本地存储 | **DataStore**（配置）/ **文件系统**（素材帧） | 不引入 Room（MVP 无关系型数据需求） |
| 网络 | **Retrofit + OkHttp** | 仅用于调用用户配置的 AI 服务与天气 API |
| 最低/目标 SDK | minSdk **29**（Android 10）/ targetSdk 跟随最新稳定版 | |
| 构建 | **Gradle + Version Catalog**（`libs.versions.toml`）+ **KSP** | 禁用 kapt（用 KSP） |
| 代码风格 | **ktlint**（提交前必过） | 见 §8 |

---

## 3. 模块/包结构

单模块 `app`，按功能 + 分层组织包：

```
com.floatypet/
├─ overlay/            # 悬浮窗（核心载体）
│   ├─ OverlayService.kt        # 前台服务，悬浮窗生命周期
│   ├─ render/                  # 渲染层（见 §5）
│   │   ├─ PetRenderer.kt       # 接口（禁止上层绕过）
│   │   ├─ SpriteRenderer.kt    # 2D 逐帧实现
│   │   ├─ FrameSequencer.kt    # 帧序列播放器
│   │   └─ BubbleLayer.kt       # 气泡层（预留，见 §6.3）
│   ├─ interaction/             # 触摸分发、手势识别
│   └─ behavior/                # 行为引擎（输出 PetResponse）+ 状态感知源
│       ├─ PetBehaviorEngine.kt # 触发场景 → PetResponse{action, bubble?}
│       └─ sensor/              # 设备状态(广播)/天气(API)/时间/APP分类(Usage Access)
├─ asset/              # 素材管线（导入/AI生成/编辑/存储）
│   ├─ import/                  # 本地导入 + 拆帧 + 抠图
│   ├─ generate/                # AI 逐帧生成（渐进式）
│   └─ store/                   # pet.json + 帧文件读写
├─ ai/                 # AI 服务配置、请求构造、Key 加密
├─ dnd/                # 免打扰（时段、APP 黑白名单、通话/前台检测）
├─ ui/                 # Compose 设置界面（按屏幕分包）
│   └─ {screen}/                # 每屏：{Screen}Screen.kt + {Screen}ViewModel.kt + {Screen}UiState.kt
├─ data/              # Repository + DataStore + 数据模型
└─ core/              # 跨模块工具、常量、扩展函数
```

---

## 4. 命名约定

| 类型 | 约定 | 示例 |
| --- | --- | --- |
| Compose 屏幕 | `{名}Screen` | `PetEditScreen` |
| ViewModel | `{名}ViewModel` | `PetEditViewModel` |
| UI 状态（不可变 data class） | `{名}UiState` | `PetEditUiState` |
| Repository 接口 | `{名}Repository` | `AssetRepository` |
| Repository 实现 | `{名}RepositoryImpl` | `AssetRepositoryImpl` |
| 用例（如有） | `{动词}{名}UseCase` | `GenerateFramesUseCase` |
| 枚举值 | `UPPER_SNAKE_CASE` | `PetAction.SLEEP` |
| 资源 ID | `{类型}_{模块}_{名}` | `ic_overlay_drag` |

---

## 5. 架构铁律（违反即不通过 review）

1. **Composable 不写业务逻辑**：状态由 ViewModel 持有，UI 只渲染 `UiState` + 上报事件。Composable 内不直接调 Repository、不起协程做业务。
2. **UI 状态不可变**：`UiState` 用 `data class` + 不可变属性，通过 `copy()` 更新。禁用 `LiveData`，统一 `StateFlow`。
3. **渲染层只走 `PetRenderer` 接口**：悬浮窗、交互、自主动作引擎**禁止直接操作 Bitmap/Canvas**，一律通过 `PetRenderer`。这是 3D 升级的隔离边界（见 [AGENT.md](AGENT.md) 第四章），不许绕过。
4. **动作语义统一用枚举**：`PetAction`（9 个）、`PetBodyPart`（HEAD/BODY/BELLY）为 2D/3D 共用契约，不许在业务里散落字符串/魔法数。
5. **行为引擎统一输出 `PetResponse{action, bubble?}`**：所有触发场景（触摸/设备状态/天气/时间/APP 分类）的行为规则，输出结构统一为 `PetResponse`。MVP 阶段 `bubble` 恒为 `null`，禁止为气泡提前写文案逻辑；但所有规则必须走 `PetResponse` 这一出口，不许某些场景直接调 `playAction` 绕过。这是气泡升级的隔离边界（见 [AGENT.md](AGENT.md) §4.5）。
6. **权限按需申请**：任何权限的请求必须在对应功能首次使用时触发，附用途说明；禁止启动即批量申请。强制权限仅 2 个（悬浮窗、存储），其余皆可选且不授予不崩溃。
7. **AI 调用必须可降级**：任何 AI 帧缺失/超时/失败，UI 与渲染必须回退到 idle 帧 + 整体变换，不得出现空白或崩溃。

---

## 6. 关键接口约定（实现前先对齐接口）

### 6.1 PetRenderer（渲染隔离层）
见 [AGENT.md](AGENT.md) §2.2。MVP 实现 `SpriteRenderer`，预留 `GLTFRenderer`（3D）。

### 6.2 FrameSequencer（帧播放器）
与 `PetRenderer` 解耦，负责按动作目录取帧、按间隔切换、支持正向/循环/乒乓三种模式。替换渲染器不应影响帧播放逻辑。

### 6.3 BubbleLayer + PetResponse（气泡对话层 —— 预留）
气泡是 **V1.1 必做交互**，MVP 不实现行为，但**架构必须预留**。以 [AGENT.md](AGENT.md) §4.5 / §4.1 的定义为准：
- 悬浮窗除 `PetRenderer` 外，再持有一个 `BubbleLayer`（接口签名 `show(content: BubbleContent)` / `dismiss()` / `isShowing()`，`BubbleContent{text, style, durationMs, anchor}`）
- MVP 提供 `NoOpBubbleLayer`（空实现，零渲染、零内存）；V1.1 提供 `TextBubbleLayer`（Canvas 绘制，不走 Android 通知系统）
- **行为引擎统一输出 `PetResponse{ action: PetAction, bubble: BubbleContent? }`**：所有触发场景（设备状态/天气/时间/APP 分类）的行为规则，输出结构统一为此。MVP 阶段 `bubble` 恒为 `null`，由 `NoOpBubbleLayer` 消费。V1.1 只需让规则填 `bubble` 字段，行为引擎与触发逻辑零改动。
- **渲染顺序固定两段式**：先 `PetRenderer` 渲染宠物 → 再 `BubbleLayer` 渲染气泡（气泡永远在宠物之上）。MVP 气泡层为空，但调用链必须存在。
- 气泡未来受免打扰体系约束（免打扰时段/黑名单 APP/办公场景一律不弹），复用同一套静默判断。

---

## 7. 性能红线（一票否决，任何提交不得突破）

| 指标 | 红线 |
| --- | --- |
| 后台内存 | ≤ 80MB（含悬浮窗进程）；帧序列**按动作懒加载**，非当前动作的 Bitmap 及时回收 |
| 整机功耗 | 亮屏 ≤ 1%/小时，待机 ≤ 0.5%/小时 |
| 渲染帧率 | 稳定 60fps；**息屏/锁屏必须停止渲染循环** |
| 稳定性 | 24h 无崩溃、无 ANR |
| 进程 | **不自启、不关联唤醒、不互相拉起**；仅用户主动开启时激活 |
| 保活 | 悬浮窗用前台服务 + LOW 优先级常驻通知；通知可被用户隐藏 |

---

## 8. 隐私与合规（不可妥协）

- **零埋点、零第三方统计 SDK、零后台上报**。
- **零广告**（开屏/弹窗/悬浮均无）。
- **用户照片**仅在用户主动 AI 生成时发往用户配置的端点；App 不中转、不留存上传记录。
- **API Key** 用 Android Keystore 加密；**禁止**出现在日志、崩溃报告、明文存储中。
- **网络请求**仅限：用户配置的 AI 端点 + 天气 API。除此之外不得发起任何网络请求。

---

## 9. 明确禁令

- ❌ 不写 Java 文件、不用 XML 布局、不用 LiveData、不用 RxJava
- ❌ 不引入 Spine/Lottie/Room
- ❌ 不加任何后端、不加任何埋点/统计 SDK、不加广告 SDK
- ❌ Composable 内不起业务协程、不直接访问 Repository
- ❌ 不绕过 `PetRenderer` 直接画 Canvas
- ❌ 不在启动时批量申请权限
- ❌ 不引入会导致进程自启/关联启动的库

---

## 10. 改动需先确认（out of bounds）

以下改动必须先与维护者确认，不得擅自进行：
- 修改 [AGENT.md](AGENT.md)（PRD）或本文件（CLAUDE.md）
- 变更 §2 技术栈、§7 性能红线、§8 隐私条款
- 新增任何运行时权限或网络请求目标
- 引入任何新的第三方依赖

---

## 11. 构建与校验命令

单模块 `:app`，Kotlin + Compose + Hilt + KSP。Wrapper jar 未入库，命令行首次需生成（见 [README.md](README.md)）。

```bash
gradle wrapper --gradle-version 8.11.1   # 仅首次（或用 Android Studio 自动补全）
./gradlew assembleDebug                   # 构建 debug APK
./gradlew installDebug                    # 安装到设备
./gradlew testDebugUnitTest               # 单元测试
./gradlew connectedDebugAndroidTest       # 仪器测试（性能/交互）
```

> ktlint 尚未接入构建（待加 ktlint Gradle 插件）。接入前先靠 IDE 格式化保持 official code style。

完成功能后，按 superpowers 的 `verification-before-completion` 流程，先跑校验命令拿到证据，再声明完成。
