package me.rerere.rikkahub.di

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import kotlinx.serialization.json.Json
import me.rerere.highlight.Highlighter
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.companion.CharacterManager
import me.rerere.rikkahub.data.companion.BehaviorPolicyManager
import me.rerere.rikkahub.data.companion.CompanionFacade
import me.rerere.rikkahub.data.companion.MemoryManager
import me.rerere.rikkahub.data.companion.PersonaManager
import me.rerere.rikkahub.data.companion.ProactiveTriggerManager
import me.rerere.rikkahub.data.companion.PromptBuilder
import me.rerere.rikkahub.data.companion.PromptCache
import me.rerere.rikkahub.data.companion.RelationshipManager
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.device.CompanionIntervention
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.accessibility.AccessibilityEventManager
import me.rerere.rikkahub.data.accessibility.AgentEventBus
import me.rerere.rikkahub.data.accessibility.ObservationCache
import me.rerere.rikkahub.data.accessibility.TieredPerceptionEngine
import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.data.agent.AccessibilityAgentActionExecutor
import me.rerere.rikkahub.data.agent.ActionScheduler
import me.rerere.rikkahub.data.agent.ActionVerifier
import me.rerere.rikkahub.data.agent.AgentActionExecutor
import me.rerere.rikkahub.data.agent.AgentPlanner
import me.rerere.rikkahub.data.agent.AgentRuntime
import me.rerere.rikkahub.data.agent.DefaultActionVerifier
import me.rerere.rikkahub.data.agent.LightweightTaskPlanner
import me.rerere.rikkahub.data.agent.LlmTaskPlanner
import me.rerere.rikkahub.data.agent.NoOpLlmTaskPlanner
import me.rerere.rikkahub.data.agent.asForegroundCacheSource
import me.rerere.rikkahub.data.companion.CompanionSoftActions
import me.rerere.rikkahub.data.companion.policy.CompanionEmotionResolver
import me.rerere.rikkahub.overlay.TaskBallManager
import me.rerere.rikkahub.overlay.pet.CompanionPetHost
import me.rerere.rikkahub.overlay.pet.CompanionPetRenderer
import me.rerere.rikkahub.overlay.pet.PetRenderer
import me.rerere.rikkahub.overlay.pet.PixelPetRenderer
import me.rerere.rikkahub.overlay.pet.SwitchingPetRenderer
import kotlinx.coroutines.Dispatchers
import androidx.room.Room
import me.rerere.rikkahub.service.ChatNotificationManager
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        Highlighter(get())
    }

    single {
        AppEventBus()
    }

    single {
        AgentEventBus()
    }

    single {
        me.rerere.rikkahub.data.agent.AgentRuntimeEventBus()
    }

    single {
        me.rerere.rikkahub.data.agent.capability.PhoneControlCore(context = get())
    }

    single<me.rerere.rikkahub.data.agent.capability.AgentCapability> {
        me.rerere.rikkahub.data.agent.capability.PhoneControlCapability(core = get())
    }

    single<me.rerere.rikkahub.data.agent.capability.vision.OcrCapability> {
        me.rerere.rikkahub.data.agent.capability.vision.DefaultOcrCapability(
            context = get(),
            settingsStore = get(),
            providerManager = get(),
        )
    }

    single<me.rerere.rikkahub.data.agent.capability.vision.VisionCapabilityRouter> {
        me.rerere.rikkahub.data.agent.capability.vision.DefaultVisionCapabilityRouter(
            ocr = get(),
        )
    }

    single {
        me.rerere.rikkahub.data.agent.trace.AgentTracer()
    }

    single {
        AccessibilityEventManager(eventBus = get())
    }

    single { ObservationCache() }

    single {
        val appContext = get<android.content.Context>()
        val settingsStore = get<me.rerere.rikkahub.data.datastore.SettingsStore>()
        val ocrCapability = get<me.rerere.rikkahub.data.agent.capability.vision.OcrCapability>()
        TieredPerceptionEngine(
            cache = get(),
            lightSnapshot = {
                me.rerere.rikkahub.service.SolaceAccessibilityService.instance
                    ?.captureUISnapshot(maxNodes = 48)
                    ?: UISnapshot(
                        page = "",
                        packageName = "",
                        timestamp = System.currentTimeMillis(),
                    )
            },
            fullSnapshot = {
                me.rerere.rikkahub.service.SolaceAccessibilityService.instance
                    ?.captureUISnapshot(maxNodes = 120)
                    ?: UISnapshot(
                        page = "",
                        packageName = "",
                        timestamp = System.currentTimeMillis(),
                    )
            },
            ocrProvider = { snap ->
                val service = me.rerere.rikkahub.service.SolaceAccessibilityService.instance
                    ?: return@TieredPerceptionEngine null
                val shot = service.captureScreenshotPng(maxWidth = 720).getOrNull()
                    ?: return@TieredPerceptionEngine null
                val tmp = java.io.File(appContext.cacheDir, "tiered_ocr.jpg")
                tmp.writeBytes(shot.jpegBytes)
                val result = ocrCapability.recognizeScreen(
                    imageFile = tmp,
                    settings = settingsStore.settingsFlow.value,
                    screenWidth = snap.screenWidth.coerceAtLeast(shot.width),
                    screenHeight = snap.screenHeight.coerceAtLeast(shot.height),
                )
                if (result == null || (result.text.isBlank() && result.blocks.isEmpty())) null
                else result.engine to result.blocks
            },
            visionProvider = null,
        )
    }

    single<LlmTaskPlanner> {
        NoOpLlmTaskPlanner()
    }

    single<AgentPlanner> {
        LightweightTaskPlanner(llm = get())
    }

    single<AgentActionExecutor> {
        AccessibilityAgentActionExecutor(capability = get())
    }

    single {
        ActionScheduler(
            executor = get(),
            parentScope = get<AppScope>(),
            workerDispatcher = Dispatchers.Default,
        )
    }

    single {
        Room.databaseBuilder(
            get(),
            me.rerere.rikkahub.data.agent.memory.AgentMemoryDatabase::class.java,
            "agent_memory",
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    single {
        get<me.rerere.rikkahub.data.agent.memory.AgentMemoryDatabase>().agentMemoryDao()
    }

    single<me.rerere.rikkahub.data.agent.memory.MemoryManager> {
        me.rerere.rikkahub.data.agent.memory.AgentMemoryManager(
            scope = get<AppScope>(),
            dao = get(),
        )
    }

    single<ActionVerifier> {
        DefaultActionVerifier()
    }

    single {
        me.rerere.rikkahub.data.agent.AgentStateManager(
            runtimeEventBus = get(),
        )
    }

    single {
        me.rerere.rikkahub.data.agent.ObservationCollector(
            foregroundSource = {
                get<AccessibilityEventManager>().asForegroundCacheSource()
            },
        )
    }

    single {
        AgentRuntime(
            planner = get(),
            executor = get(),
            verifier = get(),
            eventBus = get<AgentEventBus>(),
            appScope = get<AppScope>(),
            scheduler = get(),
            memory = get<me.rerere.rikkahub.data.agent.memory.MemoryManager>(),
            tracer = get(),
            runtimeEventBus = get(),
            stateManager = get(),
            observationCollector = get(),
        )
    }

    single {
        me.rerere.rikkahub.data.agent.AgentTaskQueue(
            runtime = get(),
            core = get(),
            eventBus = get(),
            appScope = get<AppScope>(),
        )
    }

    single {
        me.rerere.rikkahub.data.agent.AgentManager(
            runtime = get(),
            queue = get(),
            core = get(),
            eventBus = get(),
            stateManager = get(),
        )
    }

    single {
        CompanionIntervention(
            context = get(),
            conversationRepo = get(),
            settingsStore = get(),
            providerManager = get(),
            characterManager = get(),
        )
    }

    // Lazy: do NOT create AgentManager/Runtime graph during Application.onCreate.
    // Eager creation was a cold-start crash risk for signed/release installs.
    single {
        TaskBallManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
            agentRuntimeEventBus = get(),
            agentManagerLazy = lazy { get() },
        )
    }

    single {
        LocalTools(
            context = get(),
            eventBus = get(),
            ttsManager = get(),
            settingsStore = get(),
            companionIntervention = get(),
            taskBallManagerLazy = lazy { get() },
            phoneControlCore = get(),
        )
    }

    single { CharacterManager() }

    single { PersonaManager() }

    single { MemoryManager() }

    single { RelationshipManager() }

    single { BehaviorPolicyManager() }

    single { ProactiveTriggerManager() }

    single { PromptBuilder() }

    single { PromptCache() }

    single {
        CompanionEmotionResolver(
            stateStore = get(),
            conversationRepo = get(),
        )
    }

    single {
        CompanionFacade(
            stateStore = get(),
            characterManager = get(),
            personaManager = get(),
            memoryManager = get(),
            relationshipManager = get(),
            behaviorPolicyManager = get(),
            proactiveTriggerManager = get(),
            promptBuilder = get(),
            promptCache = get(),
        )
    }

    single<PetRenderer> {
        SwitchingPetRenderer(
            avatarRenderer = CompanionPetRenderer(),
            pixelRenderer = PixelPetRenderer(),
        )
    }

    single {
        CompanionPetHost(
            context = get(),
            appScope = get(),
            settingsStore = get(),
            emotionResolver = get(),
            renderer = get(),
        )
    }

    single {
        CompanionSoftActions(
            intervention = get(),
            petHost = get(),
            settingsStore = get(),
            agentManagerLazy = lazy { get() },
        )
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        runCatching {
            val context: android.content.Context = get()
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            Firebase.crashlytics
        }.getOrNull()
    }

    single {
        runCatching {
            val context: android.content.Context = get()
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            Firebase.analytics
        }.getOrNull()
    }

    single {
        SoundEffectPlayer(get())
    }

    // 生成通知与业务解耦：ChatService 只发事件，通知由这里消费；
    // createdAtStart 保证进程启动即订阅，否则后台生成的事件会因无订阅者而丢失
    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            conversationCompressHelper = get(),
            sessionOverviewHelper = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            workflowManager = get(),
            workspaceRepository = get(),
            folderRepository = get(),
            companionFacade = get(),
            agentManagerLazy = lazy { get() },
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}
