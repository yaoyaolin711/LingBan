# Solace

Android 原生 AI 伴侣客户端。基于 [RikkaHub](https://github.com/re-ovo/rikkahub) 二次开发：保留多 Provider、MCP、Workspace、搜索、备份、Web、消息分支等完整能力，并提供 Solace 伴侣向界面与 Rose Gold 视觉设计。

> **致谢与许可**：核心实现来自 [RikkaHub](https://github.com/re-ovo/rikkahub)（作者 re-ovo / RikkaHub 社区）。本项目以 **GNU Affero General Public License v3.0 (AGPL-3.0)** 发布，完整条款见 [LICENSE](LICENSE)。使用、修改与分发须遵守 AGPL-3.0。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation 3 |
| DI | Koin |
| 本地存储 | Room、DataStore |
| 网络 | OkHttp + kotlinx.serialization |
| 图片 | Coil |
| 健康数据 | Health Connect（只读） |
| 最低系统 | Android 8.0（API 26），compile/target SDK 37 |
| 版本 | 1.0.5（`applicationId`: `com.agent.chat`） |

### 模块

| 模块 | 说明 |
|------|------|
| `app` | 主应用 UI、业务编排、伴侣引擎、悬浮宠物 |
| `ai` | Provider 抽象（OpenAI / Google / Anthropic 兼容） |
| `search` | 联网搜索（Exa、Tavily、Zhipu、Brave 等） |
| `speech` | TTS / ASR（含 SiliconFlow、OpenAI、系统等） |
| `document` | PDF / DOCX 等文档解析 |
| `workspace` | proot Linux 工作区与工具 |
| `web` / `web-ui` | 内嵌 Web 服务与前端 |
| `highlight` / `common` / `material3` | 高亮、公共工具、配色工具 |

---

## 信息架构

启动页为 **Home**：

- **Home**：伴侣空间入口（对话、伙伴、记忆、个人中心）
- **Chat**：完整对话（流式、分支、附件、工具、搜索、MCP、Workspace 等）
- **Agent Center**：助手列表与详情配置（伴侣开关、主动关怀、本机工具等）
- **Profile**：按分组管理能力
  - 伴侣：助手、扩展、收藏、翻译、生图
  - 模型与服务：默认模型、Provider、搜索、语音、MCP、Web
  - 数据：备份、聊天存储、Health Connect、亲密互动（占位）、统计、请求日志
  - 更多：偏好、主题、关于、捐赠
  - 法律：免责声明

底层业务语义与 RikkaHub 一致；Kotlin 包名仍为 `me.rerere.rikkahub`，对外品牌为 Solace。

默认主题：**Solace Rose Gold**（背景 `#FFF9F7`，主色 `#B76E79`）。设计令牌见 `ui/theme/`（通过 `SolaceTheme.colorScheme` 使用）。

---

## 功能一览

### 上游继承

- Material You / 多主题（含 Solace Rose Gold）与深色模式
- Workspace：基于 proot 的 Linux Agent 环境
- 多 AI Provider：自定义 API / URL / 模型（OpenAI、Google、Anthropic 兼容）
- Claude / OpenAI 渠道预设与中继说明
- 多模态输入（图片、文档、PDF、Docx 等）
- Web 访问（多端）
- MCP 支持
- Markdown（代码高亮、LaTeX、表格、Mermaid）
- 消息分支
- 联网搜索（Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity 等）
- Prompt 变量、助手自定义、记忆、AI 翻译
- 二维码导入导出 Provider、自定义 HTTP 头/体
- SillyTavern 角色卡导入
- 备份（本地 / WebDAV / S3）、统计与收藏
- LAN PaddleOCR 兜底识别

### Solace 伴侣能力

- **伴侣状态引擎**：情绪、关系阶段、记忆与 Persona，注入对话上下文
- **主动关怀**：前台服务 + 策略层，按冷却与场景给出建议
- **悬浮宠物**：桌面悬浮头像 / 像素皮肤，快捷操作入口
- **自定义音色**：TTS / ASR 多供应商（含硅基流动 SiliconFlow）
- **本机设备关怀**：前台感知、通知、可选 Shizuku 高级 Shell / 手机控制
- **Health Connect**：只读同步步数、心率、睡眠等到伴侣上下文（非医疗诊断）
- **亲密互动**：设置页占位，方案暂缓，收集方向建议

---

## 构建与运行

环境：Android Studio、JDK 17+、Android SDK。

1. 在 `app/` 放置 `google-services.json`（Firebase）。
2. （可选）完整 Web UI：在 `web-ui/` 执行 `pnpm install && pnpm run build`。若未安装 pnpm，构建会跳过并使用占位静态页。
3. **路径注意（Windows）**：若工程路径含非 ASCII 字符（如中文目录），`workspace` 模块的 CMake/NDK 会自动跳过；完整 Workspace 原生能力请将工程放在纯英文路径下再构建。另需 `git submodule update --init --recursive` 拉取 `material3/material-color-utilities`。
4. Health Connect 相关权限在系统设置 / 应用内授权；未安装 Health Connect 时对应功能不可用。
5. 构建：

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

用 Android Studio 打开本仓库根目录，Sync Gradle 后运行 `app` 模块。

首次使用建议：Home → 个人中心 → 服务商 / 默认模型，配置 API 后回到 Home 开启对话。需要伴侣上下文时，在助手详情中开启伴侣相关选项，并按需配置语音、Health Connect、本机工具。

---

## 相对上游的主要改动

- 工程以 RikkaHub 多模块为根
- `applicationId` → `com.agent.chat`；应用名与启动图标 → Solace
- 新增 Home / Profile 等伴侣向导航与 Rose Gold 设计系统
- 伴侣状态 / 情绪 / 主动关怀 / 悬浮宠物（含像素皮肤）
- Health Connect 只读健康摘要注入伴侣 Prompt
- SiliconFlow TTS / ASR；自定义音色配置增强
- 本机设备关怀与 Shizuku 引导式高级 Shell
- Claude / OpenAI 渠道预设、免责声明页
- 亲密互动页为暂缓占位（原 Intiface / Buttplug 试验路径已移除）
- 关于页与 README 标明基于 RikkaHub 与 AGPL-3.0
- `gradle.properties` 增加 `android.overridePathCheck=true`（支持中文路径下的 AGP 检查豁免）

---

## License

[AGPL-3.0](LICENSE) — Copyright 归 RikkaHub 原作者及本仓库贡献者。二次分发须开源相应修改并保留本声明。
