# 浮宠（FloatyPet）

纯客户端 Android 桌面宠物 App。详见 [AGENT.md](AGENT.md)（PRD）与 [CLAUDE.md](CLAUDE.md)（开发规范）。

## 技术栈

Kotlin · Jetpack Compose · MVVM · Hilt · DataStore · Retrofit/OkHttp
minSdk 29 / compileSdk 35 · AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.11.1

## 工程结构

```
pet/
├─ AGENT.md / CLAUDE.md          # PRD + 开发规范
├─ design/                       # 设计规范 + UI 原型（mockup*.png）
├─ settings.gradle.kts           # 单模块 :app
├─ build.gradle.kts              # 根构建
├─ gradle/libs.versions.toml     # 版本目录（统一依赖版本）
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml      # 2 强制权限 + OverlayService 前台服务
      ├─ res/                     # 主题色、字符串、自适应图标
      └─ kotlin/com/floatypet/
         ├─ FloatyPetApp.kt       # @HiltAndroidApp
         ├─ MainActivity.kt       # Compose 入口
         ├─ core/model/           # PetAction/PetBodyPart/PetResponse/Bubble*/PetState/PetAssetType
         ├─ overlay/
         │   ├─ OverlayService.kt # 前台服务（保活 + 渲染宿主）
         │   ├─ render/           # PetRenderer 接口 + SpriteRenderer/FrameSequencer + BubbleLayer(NoOp)
         │   └─ behavior/         # PetBehaviorEngine（统一输出 PetResponse）
         └─ ui/
             ├─ theme/            # 暖色 MaterialTheme（Color/Theme/Type/Shape）
             └─ home/             # 首页 Screen + ViewModel + UiState
```

> 当前为**可编译的骨架**：接口与分层已落地，渲染循环、悬浮窗接入、素材管线、AI 生成等核心逻辑为 `TODO()` 占位，按 PRD 模块逐步填充。

## 如何打开 / 构建

1. **用 Android Studio 打开**（推荐）：`Open` 选择本 `pet/` 目录，IDE 会自动补全 Gradle Wrapper jar 并同步。
2. **命令行构建**前需先生成 wrapper（仓库未提交 `gradle-wrapper.jar`）：
   ```bash
   gradle wrapper --gradle-version 8.11.1   # 需本机已装 Gradle，仅首次
   ./gradlew assembleDebug                   # 构建 debug APK
   ./gradlew installDebug                    # 安装到已连接设备
   ```

> 本机若无 Android SDK，需先安装 Android Studio 或 cmdline-tools 并配置 `local.properties` 的 `sdk.dir`。

## 设计原型

见 [design/](design/)：`mockup.png`（悬浮窗/首页/编辑/AI生成/免打扰）、`mockup2.png`（AI配置/设置/引导/动作库/触摸反馈）。
