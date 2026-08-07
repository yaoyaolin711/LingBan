# 局域网 Qwen3-TTS 新手教程

这份教程适合「手机是主力，电脑挂一个语音合成服务」的用户。

## 你将获得什么

- 不买云端 TTS API Key，也能用高质量 Qwen3 音色
- 同一 WiFi 下，手机把文字发给家里电脑合成语音
- 可用于聊天朗读和语音通话（助手声音 → Local → Qwen3 局域网音色）

---

## 准备条件

1. 一台带 **NVIDIA GPU** 的 Windows / Linux 电脑（建议显存 ≥ 6GB；不够可改用 0.6B）
2. 手机和电脑连接同一个 WiFi
3. 电脑安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（启用 GPU 支持）
4. 首次启动会下载约数 GB 模型权重，需要稳定网络

> 没有独显？可尝试本地 Python 安装 `qwen-tts` 并用 CPU 跑，但延迟会明显偏高，不太适合语音通话。

---

## 第一步：启动 TTS 服务（电脑上操作）

### 方法 A：双击启动（推荐）

1. 打开项目目录中的 `tools/qwen3-tts-lan`
2. 双击 `start.bat`（macOS/Linux 执行 `./start.sh`）
3. 等待终端显示 Done；首次还需等模型下载完成

查看日志：

```bash
docker logs -f qwen3-tts-lan
```

看到 `Model ready` 或 `/health` 返回 `"status":"ok"` 即可。

### 方法 B：命令行

在 `tools/qwen3-tts-lan` 目录执行：

```bash
docker compose up -d --build
```

### 方法 C：本机 Python（不用 Docker）

```bash
cd tools/qwen3-tts-lan
pip install -r requirements.txt
python -m uvicorn server.main:app --host 0.0.0.0 --port 8877
```

### 显存不够时

编辑 `docker-compose.yml`，把：

```yaml
- MODEL_SIZE=1.7b
```

改成：

```yaml
- MODEL_SIZE=0.6b
```

然后重新 `docker compose up -d --build`。

---

## 第二步：查电脑局域网 IP

Windows 打开 PowerShell：

```powershell
ipconfig
```

找到 `IPv4 地址`，例如：`192.168.1.100`

---

## 第三步：在 App 里配置

进入：

`设置 → 语音 → 局域网 Qwen3 TTS`

1. 开启 **使用局域网 Qwen3 TTS**
2. 服务地址填：`http://192.168.1.100:8877`
3. 点击 **测试连接**
4. 成功后可点 **试听**

建议同时开启：

- **失败后回退到系统 TTS**

这样电脑离线时，聊天朗读 / 语音通话不会直接失败。

### 用于语音通话

助手 → 声音设置 → Local 档位 → 选择「Qwen3 局域网」预设音色（如 Vivian）。

### 用于聊天朗读

语音设置里添加 / 选择类型为 **Qwen3 局域网** 的 TTS 提供商，或把全局 TTS 选中该提供商。

---

## API 说明（给自己写脚本的用户）

### `GET /health`

```json
{
  "status": "ok",
  "speakers": ["Vivian", "Serena", "..."],
  "sample_rate": 24000
}
```

### `POST /v1/tts/speech`

```json
{
  "input": "你好，我是你的 AI 伴侣。",
  "mode": "custom_voice",
  "speaker": "Vivian",
  "language": "Auto",
  "response_format": "wav"
}
```

返回音频二进制（`audio/wav` 或 PCM）。

---

## 常见问题

### 1) 提示连接失败

按顺序排查：

1. 电脑 Docker / 服务是否在运行（`docker ps`）
2. 手机和电脑是否同一 WiFi
3. Windows 防火墙是否拦截了 **8877** 端口
4. IP 是否变化（重启路由器后可能变）
5. 模型是否还在下载（看 `docker logs`）

### 2) 手机上用 4G/5G 可以吗？

不行。这是局域网方案，只能同一网络访问。

### 3) 电脑关机会怎样？

局域网 TTS 不可用。若开启了「失败后回退到系统 TTS」，App 会自动切回系统引擎。

### 4) 和云端 Qwen TTS 有什么区别？

| | 局域网 Qwen3 | 云端 Qwen (DashScope) |
|--|-------------|----------------------|
| 费用 | 免费（电费） | 按量付费 |
| 音质 | 很高 | 很高 |
| 外出 | 不可用 | 可用 |
| 硬件 | 需要 GPU 电脑 | 不需要 |

---

## 停止服务

```bash
docker compose down
```

---

## 一句话总结

把电脑当成「你家的 TTS 小服务器」：开机 + 同网 + 填 IP，就能免费用上 Qwen3 高质量音色。
