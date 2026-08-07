package me.rerere.tts.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class TTSProviderSetting {
    abstract val id: Uuid
    abstract val name: String

    abstract fun copyProvider(
        id: Uuid = this.id,
        name: String = this.name,
    ): TTSProviderSetting

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override var id: Uuid = Uuid.random(),
        override var name: String = "OpenAI TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.openai.com/v1",
        val model: String = "gpt-4o-mini-tts",
        val voice: String = "alloy"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("gemini")
    data class Gemini(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Gemini TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        val model: String = "gemini-2.5-flash-preview-tts",
        val voiceName: String = "Kore"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("system")
    data class SystemTTS(
        override var id: Uuid = Uuid.random(),
        override var name: String = "System TTS",
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("minimax")
    data class MiniMax(
        override var id: Uuid = Uuid.random(),
        override var name: String = "MiniMax TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.minimaxi.com/v1",
        val model: String = "speech-2.6-turbo",
        val voiceId: String = "female-shaonv",
        val speed: Float = 1.0f
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("qwen")
    data class Qwen(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Qwen TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://dashscope.aliyuncs.com/api/v1",
        val model: String = "qwen3-tts-flash",
        val voice: String = "Cherry",
        val languageType: String = "Auto"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("groq")
    data class Groq(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Groq TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.groq.com/openai/v1",
        val model: String = "canopylabs/orpheus-v1-english",
        val voice: String = "austin"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("xai")
    data class XAI(
        override var id: Uuid = Uuid.random(),
        override var name: String = "xAI TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.x.ai/v1",
        val voiceId: String = "eve",
        val language: String = "auto"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("mimo")
    // 默认值仅用于快捷起步 可在设置页任意修改
    data class MiMo(
        override var id: Uuid = Uuid.random(),
        override var name: String = "MiMo TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.xiaomimimo.com/v1",
        val model: String = "mimo-v2.5-tts",
        val voice: String = "mimo_default"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("elevenlabs")
    data class ElevenLabs(
        override var id: Uuid = Uuid.random(),
        override var name: String = "ElevenLabs TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.elevenlabs.io",
        val model: String = "eleven_multilingual_v2",
        val voiceId: String = "JBFqnCBsd6RMkjVDRZzb",
        val stability: Float = 0.5f,
        val similarityBoost: Float = 0.75f,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * 阶跃星辰 Step TTS (step-tts-mini / step-tts-vivid / stepaudio-2.5-tts)。
     *
     * 与 Step ASR 共用同一个 baseUrl 与鉴权方式 (Authorization: Bearer sk-xxx),
     * 走 OpenAI 兼容的 [POST /v1/audio/speech] 非流式接口, 服务端一次性返回完整音频
     * 二进制 (默认 mp3, 也可选 wav/pcm/opus/flac)。客户端把整段音频包成一个 AudioChunk
     * 发出, 由 TtsSynthesizer 统一收集后交给播放器。
     *
     * 仅 stepaudio-2.5-tts 模型支持 [instruction] 字段 (全局语境, ≤200 字符), 其它模型
     * (step-tts-mini / step-tts-vivid / step-tts-2) 会忽略该字段, 留空时不下发。
     *
     * 官方文档:
     * - 模型总览: https://platform.stepfun.com/docs/zh/guides/models/stepaudio-2.5-tts
     * - 开发指南: https://platform.stepfun.com/docs/zh/guides/developer/tts
     */
    @Serializable
    @SerialName("step")
    data class Step(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Step TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.stepfun.com",
        // step-tts-mini | step-tts-vivid | stepaudio-2.5-tts | step-tts-2
        val model: String = "step-tts-mini",
        // 完整 voice-id 列表见开发指南; 默认值与官方 SDK 一致
        val voice: String = "elegantgentle-female",
        // mp3 | wav | pcm | opus | flac; 注意 StepFun API 使用 camelCase 字段名
        val responseFormat: String = "mp3",
        // 0.5 - 2.0, 1.0 为正常语速
        val speed: Float = 1.0f,
        // 0.1 - 2.0, 1.0 为正常音量
        val volume: Float = 1.0f,
        // 8000 | 16000 | 22050 | 24000
        val sampleRate: Int = 24000,
        // 仅 stepaudio-2.5-tts 生效; ≤200 字符, 留空时不下发
        val instruction: String = "",
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("fish-audio")
    data class FishAudio(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Fish Audio TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.fish.audio",
        val model: String = "s2.1-pro",
        val referenceId: String = "",
        val temperature: Float = 0.7f,
        val speed: Float = 1.0f,
        val format: String = "mp3",
        val topP: Float = 0.7f,
        val chunkLength: Int = 300,
        val normalize: Boolean = true,
        val latency: String = "normal",
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * 模思 Mossland / MOSI Studio TTS。
     *
     * 在 [studio.mosi.cn](https://studio.mosi.cn) 创建 API Key 与音色；
     * API 请求发往 [api.mosi.cn](https://api.mosi.cn)：`POST /v1/audio/speech`。
     */
    @Serializable
    @SerialName("mossland")
    data class Mossland(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Mossland TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.mosi.cn",
        val model: String = "moss-tts",
        val voiceId: String = "",
        /** Preferred container / response_format: mp3 | wav | pcm（官方默认 mp3） */
        val format: String = "mp3",
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * 硅基流动 SiliconFlow TTS（OpenAI 兼容 `/audio/speech`）。
     *
     * 官方文档: https://docs.siliconflow.com/cn/userguide/capabilities/text-to-speech
     * 控制台模型广场筛选「语音」标签获取当前可用模型与音色。
     */
    @Serializable
    @SerialName("siliconflow")
    data class SiliconFlow(
        override var id: Uuid = Uuid.random(),
        override var name: String = "硅基流动 TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.siliconflow.cn/v1",
        val model: String = "FunAudioLLM/CosyVoice2-0.5B",
        /** 系统预置音色需带模型前缀，例如 FunAudioLLM/CosyVoice2-0.5B:alex */
        val voice: String = "FunAudioLLM/CosyVoice2-0.5B:alex",
        /** mp3 | opus | wav | pcm */
        val responseFormat: String = "mp3",
        /** 0.25 - 4.0，默认 1.0 */
        val speed: Float = 1.0f,
        /** 增益 dB，-10 ~ 10，默认 0 */
        val gain: Float = 0.0f,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * 局域网 Qwen3-TTS（用户 PC 本地部署）。
     *
     * 协议见 docs/tts/lan-qwen3-tts.md：
     * - GET  {baseUrl}/health
     * - POST {baseUrl}/v1/tts/speech
     */
    @Serializable
    @SerialName("qwen3-local")
    data class Qwen3Local(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Qwen3 局域网",
        /** e.g. http://192.168.1.100:8877 */
        val baseUrl: String = "",
        /** custom_voice | voice_design */
        val mode: String = "custom_voice",
        val speaker: String = "Vivian",
        val language: String = "Auto",
        /** Style instruct for CustomVoice (optional). */
        val instruct: String = "",
        /** Natural-language voice description for VoiceDesign mode. */
        val voiceDescription: String = "",
        /** wav | pcm */
        val responseFormat: String = "wav",
        val speed: Float = 1.0f,
        val fallbackToSystem: Boolean = true,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * 火山引擎 / 豆包语音合成（HTTP v1 一次性合成）。
     *
     * 控制台获取 AppID + Access Token；音色填 voice_type（官方音色或复刻 speaker id）。
     * 文档: https://www.volcengine.com/docs/6561/79823
     */
    @Serializable
    @SerialName("volcengine")
    data class Volcengine(
        override var id: Uuid = Uuid.random(),
        override var name: String = "火山引擎 TTS",
        val appId: String = "",
        val accessToken: String = "",
        val baseUrl: String = "https://openspeech.bytedance.com/api/v1/tts",
        /** 业务集群，标准音色默认 volcano_tts */
        val cluster: String = "volcano_tts",
        /** 音色类型 / 复刻 speaker id */
        val voiceType: String = "zh_female_wanqudashu_moon_bigtts",
        /** mp3 | wav | pcm | ogg_opus */
        val encoding: String = "mp3",
        val speedRatio: Float = 1.0f,
        val sampleRate: Int = 24000,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Gemini::class,
                SystemTTS::class,
                MiniMax::class,
                Qwen::class,
                Groq::class,
                XAI::class,
                MiMo::class,
                ElevenLabs::class,
                Step::class,
                FishAudio::class,
                Mossland::class,
                SiliconFlow::class,
                Volcengine::class,
                Qwen3Local::class,
            )
        }
    }
}
