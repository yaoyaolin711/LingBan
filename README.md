# Solace

Android 原生 AI 伴侣客户端。**基于 [RikkaHub](https://github.com/re-ovo/rikkahub) 二次开发**：保留 RikkaHub 完整底层能力（多 Provider、MCP、Workspace、搜索、备份、Web、消息分支等），并以 Solace 伴侣向信息架构与暖色视觉重新包装操作界面。

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
| 最低系统 | Android 8.0（API 26），compile/target SDK 37 |
| 版本 | 1.0.0（`applicationId`: `com.agent.chat`） |

### 模块

| 模块 | 说明 |
|------|------|
| `app` | 主应用、Solace UI 皮囊、业务编排 |
| `ai` | Provider 抽象（OpenAI / Google / Anthropic 兼容） |
| `search` | 联网搜索（Exa、Tavily、Zhipu、Brave 等） |
| `speech` | TTS / ASR |
| `document` | PDF / DOCX 等文档解析 |
| `workspace` | proot Linux 工作区与工具 |
| `web` / `web-ui` | 内嵌 Web 服务与前端 |
| `highlight` / `common` / `material3` | 高亮、公共工具、配色工具 |

---

## Solace 皮囊（信息架构）

启动页为 **Home 中枢**（暖色 Orb），不再默认落在纯聊天页：

- **Home**：新对话 / 最近会话 / 伙伴 / 历史 / 记忆 / 个人中心
- **Chat**：完整 RikkaHub 对话（流式、分支、附件、工具、搜索、MCP、Workspace…）
- **Agent Center**：助手（Assistant）列表与详情全套配置
- **Profile**：聚合 Provider、模型、搜索、语音、MCP、备份、主题、扩展、统计、翻译、生图、收藏、关于等入口

底层业务语义与 RikkaHub 一致；Kotlin 包名仍为 `me.rerere.rikkahub`，对外品牌与图标为 Solace。

默认主题：**Solace Warm**（暖底 `#FFF7F1` + 主色 `#E8823A`）。

---

## 功能一览（RikkaHub 全集）

- Material You / 多主题（含 Solace 暖橘）与深色模式
- Workspace：基于 proot 的 Linux Agent 环境
- 多 AI Provider：自定义 API / URL / 模型（OpenAI、Google、Anthropic 兼容）
- 多模态输入（图片、文档、PDF、Docx 等）
- Web 访问（多端）
- MCP 支持
- Markdown（代码高亮、LaTeX、表格、Mermaid）
- 消息分支
- 联网搜索（Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity 等）
- Prompt 变量、助手自定义、记忆、AI 翻译
- 二维码导入导出 Provider、自定义 HTTP 头/体
- SillyTavern 角色卡导入
- 备份（本地 / WebDAV / S3）、TTS / ASR、统计与收藏

### 后续计划（本轮未做）

旧 Solace 独有能力将作为增量：场景化主动关心、无障碍读屏、短信/通知感知、口语拆气泡节奏、关系/表达档位等。

---

## 构建与运行

环境：Android Studio、JDK 17+、Android SDK。

1. 在 `app/` 放置 `google-services.json`（Firebase；仓库可提供本地占位，正式分发请换成自有配置）。
2. （可选）完整 Web UI：在 `web-ui/` 执行 `pnpm install && pnpm run build`。若未安装 pnpm，构建会跳过并使用占位静态页。
3. **路径注意（Windows）**：若工程路径含非 ASCII 字符（如中文目录），`workspace` 模块的 CMake/NDK 会自动跳过；完整 Workspace 原生能力请将工程放在纯英文路径下再构建。另需 `git submodule update --init --recursive` 拉取 `material3/material-color-utilities`。
4. 构建：

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

用 Android Studio 打开本仓库根目录，Sync Gradle 后运行 `app` 模块。

首次使用建议：Home → Profile → 服务商 / 默认模型，配置 API 后回到 Home 开启对话。

---

## 相对上游的主要改动

- 工程以 RikkaHub 多模块为根；旧单模块 Solace 归档于 `_legacy_solace/`（仅供参考，不参与编译）
- `applicationId` → `com.agent.chat`；应用名与启动图标 → Solace
- 新增 Home / Profile 导航皮囊；默认主题 Solace Warm
- 关于页与 README 标明基于 RikkaHub 与 AGPL-3.0
- `gradle.properties` 增加 `android.overridePathCheck=true`（支持中文路径下的 AGP 检查豁免）

---

## License

[AGPL-3.0](LICENSE) — Copyright 归 RikkaHub 原作者及本仓库贡献者。二次分发须开源相应修改并保留本声明。
