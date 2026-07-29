# Solace

Android 原生 **AI 伴侣聊天客户端**：人设扮演、场景化主动关怀、长期记忆、口语化短气泡节奏。

面向 OpenAI 兼容 API（自定义 Base URL / Key / 模型）。

> **原创声明**：本仓库代码与 UI 均为独立实现。产品体验上可参考常见伴侣客户端的交互直觉，**不包含、不派生** 橘瓣 / RikkaHub 等第三方 AGPL 源码。推送与署名仅属于仓库所有者本人。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 2.0 / JVM 17 |
| UI | Jetpack Compose + Material 3 |
| 架构组件 | Navigation Compose、ViewModel、Lifecycle |
| DI | Hilt |
| 本地存储 | Room、SharedPreferences、EncryptedSharedPreferences（API Key） |
| 网络 | Retrofit + OkHttp + Moshi（SSE 流式） |
| 后台任务 | WorkManager（主动问候） |
| 最低系统 | Android 8.0（API 26），compile/target SDK 36 |

---

## 架构概览

采用经典分层，业务以「对话编排 + 工具循环」为中心：

```
┌─────────────────────────────────────────────────────────┐
│  ui/          Compose 页面 + ViewModel                   │
│  conversation / chat / persona / settings / memory      │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  data/                                                  │
│  ├── ai/           编排、提示词注入、本地工具注册表        │
│  ├── provider/     OpenAI 兼容流式协议（含 tools）        │
│  ├── repository/   会话 / 人设 / 记忆 / Provider          │
│  ├── local/        Room Entity + DAO                    │
│  ├── memory/       异步增量摘要                          │
│  ├── care/         生活感知 / 情绪 / 待跟进               │
│  ├── proactive/    场景化主动关心 Worker                 │
│  └── settings/     伴侣感 / 工具开关                     │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  domain/        Message / Persona / Memory / AppError   │
│  di/            Hilt Module                             │
└─────────────────────────────────────────────────────────┘
```

### 对话与工具循环

```mermaid
sequenceDiagram
  participant UI as ChatUI
  participant VM as ChatViewModel
  participant Orch as ToolChatOrchestrator
  participant API as OpenAICompatibleProvider
  participant Tools as LocalToolRegistry

  UI->>VM: sendMessage
  VM->>Orch: run(messages + system + tools)
  loop until_no_tool_calls
    Orch->>API: chatStreamEvents
    API-->>Orch: content / tool_calls
    Orch->>Tools: execute
    Tools-->>Orch: JSON result
    Orch-->>UI: tool_status_chip
  end
  Orch-->>VM: final_assistant_text
  VM->>UI: 换行/句号拆气泡 + 输入节奏
```

核心类：

- `ToolChatOrchestrator` — 多轮 tool 调用（上限 8 步）
- `OpenAICompatibleProvider` — SSE；产出 `ContentDelta` / `ToolCallDelta` / `Finished`
- `LocalToolRegistry` — 按设置开关组装工具定义
- `PromptContextInjector` — 人设占位符、世界书注入、预设对话、口语风格层、记忆块、时间提醒
- `CareContextBuilder` — 时段关怀、情绪自适应、日程窥探、待跟进线索
- `OutputRegexApplier` — 助手输出正则改写
- `CharacterCardImporter` — SillyTavern 角色卡 → 人设草稿
- `ReplySegmenter` — 优先按换行拆气泡，回退按句号

---

## 功能一览

### 聊天与人设

- OpenAI 兼容流式对话，多 Provider 配置（加密存 Key）
- 人设：System Prompt、Temperature、开场白；智能导入 / JSON 导入导出
- **预设对话（few-shot）**：示范几轮语气，请求时插在 system 与真实历史之间
- **世界书**：关键词触发注入相关设定，避免 system 塞满背景
- **输出正则**：对助手回复做正则替换（压 AI 腔、统一口癖；可仅视觉）
- **SillyTavern 角色卡**：支持 JSON / PNG（`chara` 元数据）导入为助手设定
- 口语伴侣风格层（可关）：短句、少列表、即时通讯式换行
- **生活感知**：按时段注入关怀提示；有日历权限时融入近期日程
- **情绪自适应**：根据近期用户用词调节回复长短与温度
- **待跟进**：从对话抽取「明天面试」等线索，跨会话合适时问起
- **场景化主动关心**：早晚/饭点/深夜/临近日程，而非仅闲置问候
- 自然聊天节奏：完整生成后分段气泡 + 「正在输入」
- Markdown 渲染、会话导出（文本 / 图片）

### 记忆

- **工具记忆**：模型通过 `memory` 增删改查，多条事实注入 System Prompt
- **增量摘要**：对话达阈值后异步滚动摘要（失败不影响主对话）
- 人设维度隔离；记忆管理弹窗可手动删除

### 本地工具（模型可调用）

| 工具 | 说明 | 默认 |
|------|------|------|
| `memory` | 长期记忆 CRUD | 开 |
| `get_current_time` | 本地时间 / 星期 / 时段 | 开 |
| `get_battery` | 电量与充电状态 | 开 |
| `get_device_info` | 品牌型号 / Android 版本 | 开 |
| `calendar_events` | 读近期日程 / 写简单事件 | 开（需日历权限） |
| `set_alarm` | 调起系统闹钟 | 开 |
| `get_location` | 粗略定位 | 关 |
| `get_app_usage` | 今日 App 使用时长 | 关（需「使用情况访问」） |

聊天时间线展示轻量工具过程条（可展开结果摘要）。

### 主动问候

开启后，WorkManager 周期性检查：结合闲置时长与时段/日历场景，用当前人设生成一句短关心并通知；点击进入对应会话。同类问候有冷却，避免刷屏。

---

## 目录结构

```
app/src/main/java/com/agent/chat/
├── AgentChatApp.kt / MainActivity.kt
├── di/                         # Hilt
├── domain/model|error/
├── data/
│   ├── ai/                     # Orchestrator、Prompt、tool/
│   ├── provider/               # AIProvider + SSE 模型
│   ├── repository/
│   ├── local/                  # Room
│   ├── memory|persona|proactive|settings|security/
│   └── error/
└── ui/
    ├── navigation/
    ├── chat|conversation|persona|settings|memory/
    ├── components|theme|export/
```

---

## 构建与运行

环境：Android Studio、JDK 17+、Android SDK。

```bash
./gradlew assembleDebug     # Debug APK
./gradlew assembleRelease   # Release APK
```

用 Android Studio 打开本目录，Sync Gradle 后运行 `app` 模块。

首次使用：进入 **设置** 配置 Provider（Base URL / API Key / 模型），再创建人设开始聊天。敏感工具与主动问候在设置中按需开启。

---

## 设计取舍（简要）

- **体验优先**：人设与记忆注入上限放宽；风格层与工具结果由模型用口语消化，避免「根据工具返回」腔。
- **人设管道**：自由文本 System Prompt + 预设对话定调 + 世界书按需注入 + 正则修口吻（文本人设 + 注入/改写，不做复杂人物属性表）。
- **关怀落地**：时段 / 情绪 / 待跟进 / 日程感知，让关心有由头。
- **工具自研**：对齐常见伴侣客户端的 tools 协议能力，代码独立实现，不拷贝第三方 AGPL 源码。
- **UI 自研**：暖橘伴侣色 + IM 会话列表/气泡；人设编辑全屏分栏。视觉可对标行业常见伴侣 App 的使用习惯，实现与资源均为本仓库原创。
