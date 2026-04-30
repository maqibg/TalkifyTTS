# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Talkify 是一款 Android TTS (Text-to-Speech) 引擎应用，作为连接器将云端大模型的语音合成能力通过 Android 标准 TTS 接口提供给系统和第三方阅读软件。应用采用 MVVM + Clean Architecture 架构，完全基于 Jetpack Compose 构建。

## 常用命令

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（启用 ProGuard 混淆）
./gradlew assembleRelease

# 代码检查 (Lint)
./gradlew lint

# 运行所有单元测试
./gradlew test

# 运行单个测试类
./gradlew test --tests "com.github.lonepheasantwarrior.talkify.service.TtsErrorCodeTest"

# 清理构建缓存
./gradlew clean
```

## 技术栈

- **语言**: Kotlin 2.3.20
- **UI**: Jetpack Compose (BOM 2026.02.01) + Material 3 Expressive
- **架构**: MVVM (Model-View-ViewModel) + Clean Architecture
- **网络**: OkHttp 4.12.0 (HTTP/2, Streaming) + DashScope SDK 2.22.13
- **音频解码**: JLayer 1.0.1 (MP3 流式解码)
- **最低兼容**: Android 11 (API 30)
- **目标版本**: Android 16 (API 36)
- **JDK**: 17

## 架构设计

### 分层结构

```
app/src/main/java/com/github/lonepheasantwarrior/talkify/
├── domain/           # 领域层：纯 Kotlin 业务接口与模型
│   ├── model/        # 引擎配置、更新信息等数据模型
│   └── repository/   # 仓储接口定义
├── infrastructure/   # 基础设施层：技术实现细节
│   ├── app/          # 应用级设施（通知、权限、电源、更新、配置仓储实现）
│   └── engine/       # 引擎级设施（各引擎配置和音色仓储实现）
├── service/          # 服务层：Android Service 实现
│   ├── engine/       # TTS 引擎统一抽象
│   │   ├── TtsEngineApi.kt          # 引擎接口定义
│   │   ├── AbstractTtsEngine.kt     # 引擎抽象基类
│   │   ├── TtsEngineFactory.kt      # 引擎工厂（单例，线程安全）
│   │   └── impl/                    # 具体引擎实现
│   ├── TalkifyTtsService.kt         # 系统 TTS 服务入口
│   └── TalkifyTtsDemoService.kt     # Demo 预览服务
└── ui/               # 表现层：Jetpack Compose UI
    ├── components/   # 通用 UI 组件
    ├── screens/      # 页面级 Composable
    ├── viewmodel/    # 状态管理
    └── theme/        # Material 3 主题定义
```

### 核心设计模式

1. **MVVM 架构**: `MainViewModel` 维护 `StartupState` 状态机，`MainScreen` 响应式渲染，`MainActivity` 仅作容器
2. **插件化引擎**: 通过 `TtsEngineApi` 接口隔离，新增引擎不影响现有代码
3. **工厂模式**: `TtsEngineFactory`（单例）根据引擎 ID 创建引擎、配置仓储、音色仓储三类组件
4. **仓储模式**: Domain 层定义接口（`EngineConfigRepository`, `VoiceRepository`），Infrastructure 层提供实现
5. **密封类**: `EngineIds` 使用密封类确保类型安全的引擎标识

### 关键接口

- **`TtsEngineApi`**: 引擎核心接口，定义 `synthesize()`, `stop()`, `release()`, `getSupportedVoices()` 等方法
- **`AbstractTtsEngine`**: 引擎抽象基类，提供日志、状态检查、文本验证等通用实现
- **`TtsSynthesisListener`**: 合成回调接口，包含 `onAudioAvailable()`, `onSynthesisCompleted()`, `onError()`
- **`BaseEngineConfig`**: 引擎配置基类，只有 `voiceId` 是跨引擎通用属性

## 支持的 TTS 引擎

| 引擎 ID | 名称 | 服务商 | 采样率 | 特点 |
|:---:|:---|:---|:---:|:---|
| microsoft-tts | 微软语音合成 | Microsoft Azure | 24kHz | 无需 API Key，WebSocket 流式，MP3 解码 |
| seed-tts-2.0 | 豆包语音 2.0 | 火山引擎 | 24kHz | 16种音色，人声更自然 |
| tencent-tts | 腾讯语音合成 | 腾讯云 | 24kHz | 47种音色（超自然/大模型/精品） |
| qwen3-tts | 通义千问 3 | 阿里云百炼 | 24kHz | 48种音色，多语种支持，WAV 头剥离 |
| xiaomi-mimo-tts | 小米 MiMo | 小米 | 24kHz | PCM 流式输出 |
| minimax-tts | MiniMax | MiniMax | 32kHz | Hex 编码 PCM 数据 |

**默认引擎**: `microsoft-tts`（开箱即用，无需配置）

## 关键业务流程

### 应用启动状态机

`MainViewModel` 按顺序执行串行检查，任何一步受阻都会暂停流程：

```
CheckingNetwork → NetworkBlocked（失败）
       ↓ 成功
CheckingNotification → RequestingNotification（无权限）
       ↓ 有权限
CheckingBattery → RequestingBatteryOptimization（受限）
       ↓ 无限制
CheckingUpdate → UpdateAvailable（发现新版）
       ↓ 无更新
Completed → 检查默认引擎状态
```

### 语音合成流程

1. 第三方 App 调用 Android `TextToSpeech` API
2. `TalkifyTtsService.onSynthesizeText()` 接收请求
3. `processRequestSynchronously()` 在 `runBlocking` 中执行：
   - 获取 WakeLock + WifiLock
   - 启动前台服务（Android 12+ 静默降级处理）
   - 通过 `TtsEngineFactory` 创建/复用引擎实例
   - 使用 `suspendCancellableCoroutine` 挂起等待合成结果
   - 120秒超时保护
4. 引擎通过网络流式请求音频数据
5. 数据通过 `SynthesisCallback.audioAvailable()` 写入系统音频管道

### 引擎切换机制

服务在每次合成前检查引擎是否切换：
- 读取 `appConfigRepository.getSelectedEngineId()`
- 与 `currentEngineId` 比较
- 若不同，释放旧引擎，创建新引擎，清除配置缓存

## 音频配置

各引擎音频配置在 `AudioConfig` 中定义：

- **通义千问/豆包/腾讯/微软/小米**: 24kHz, PCM_16BIT, 单声道
- **MiniMax**: 32kHz, PCM_16BIT, 单声道

微软引擎特殊处理：
- 使用 JLayer 进行 MP3 流式解码
- 从 MP3 Header 动态提取真实采样率
- 64KB 管道缓冲区防止 OkHttp 接收线程阻塞
- DNS 预热优化 TTFB（首字延迟）

## 错误处理

`TtsErrorCode` 定义统一错误码体系：
- **1001-1099**: 引擎级错误（未找到、未配置、初始化失败等）
- **1100-1105**: 服务级错误（网络超时、限流、认证失败等）
- `inferErrorCodeFromMessage()`: 从错误消息推断错误类型
- `toAndroidError()`: 映射到 Android TTS 标准错误码

## 配置存储

使用 `SharedPreferences` 进行配置持久化：
- **应用级配置**: `SharedPreferencesAppConfigRepository` (talkify_app_config)
  - `selected_engine`: 当前选中的引擎 ID
  - `has_requested_notification`: 是否已请求过通知权限
  - `has_opened_about_page`: 是否已打开过关于页面
- **引擎级配置**: 各引擎独立的配置键值对，继承自 `BaseEngineConfig`

## Android 组件注册

`AndroidManifest.xml` 注册的关键组件：
- **TalkifyTtsService**: 系统 TTS 服务，`foregroundServiceType="mediaPlayback"`
- **TalkifyCheckDataActivity**: TTS 数据检查（`CHECK_TTS_DATA`）
- **TalkifySampleTextActivity**: 样本文本（`GET_SAMPLE_TEXT`）
- **TalkifyDownloadVoiceData**: 音色数据下载（`INSTALL_TTS_DATA`）

## 扩展新引擎

1. 在 `domain/model` 下创建 `XxxConfig`，继承 `BaseEngineConfig`
2. 在 `service/engine/impl` 下创建 `XxxTtsEngine`，继承 `AbstractTtsEngine`
3. 在 `infrastructure/engine/repo` 下创建对应的 Voice 和 Config 仓储实现
4. 在 `EngineIds` 中添加引擎 ID 密封类成员
5. 在 `TtsEngineFactory.initializeRegistry()` 中注册引擎三件套（Engine, ConfigRepo, VoiceRepo）
6. 在 `AudioConfig` 中添加引擎预设配置
7. 在 `res/values` 下创建 `xxx-voices.xml` 定义支持的音色列表

## 依赖管理

关键依赖版本在 `gradle/libs.versions.toml` 中统一管理。**注意**: OkHttp 版本需与 DashScope SDK 内置版本保持一致（4.12.0），不可随意升级。

腾讯云 TTS SDK 以本地 AAR 形式引入：`app/libs/stream_tts-release-v2.0.16-20260128-d80cafe.aar`

## 代码规范

- 所有用户可见文本提取至 `res/values/strings.xml`，禁止硬编码字符串
- 弹窗正文使用左对齐 (`TextAlign.Start`)
- 使用 Material 3 色彩系统和字体样式
- 遵循 Kotlin 官方代码风格 (`kotlin.code.style=official`)
- 日志统一使用 `TtsLogger`，TAG 为 "TalkifyTTS"
- Release 构建启用 ProGuard 混淆（`isMinifyEnabled = true`）

## 测试

- **单元测试**: `app/src/test/java/` 目录
- **仪器化测试**: `app/src/androidTest/java/` 目录
- 运行单个测试: `./gradlew test --tests "类全限定名"`
