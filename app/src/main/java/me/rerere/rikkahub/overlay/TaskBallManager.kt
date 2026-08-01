package me.rerere.rikkahub.overlay

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.FxScopeType
import com.petterp.floatingx.compose.enableComposeSupport
import com.petterp.floatingx.listener.control.IFxAppControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.canDrawOverlays
import me.rerere.rikkahub.utils.openOverlayPermissionSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

private const val TAG = "TaskBall"
private const val FLOAT_TAG = "task_ball"

private val PHONE_CONTROL_TOOLS = setOf(
    "see_screen",
    "dump_ui",
    "ui_click",
    "ui_swipe",
    "ui_type",
    "ui_global",
    "open_app",
)

/**
 * 跨应用悬浮任务球：展示当前助手头像与手机控制任务状态。
 */
class TaskBallManager(
    private val context: Application,
    private val appScope: AppScope,
    private val eventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val agentRuntimeEventBus: me.rerere.rikkahub.data.agent.AgentRuntimeEventBus,
    private val agentManagerLazy: Lazy<me.rerere.rikkahub.data.agent.AgentManager>,
) : KoinComponent {
    private val agentManager: me.rerere.rikkahub.data.agent.AgentManager
        get() = agentManagerLazy.value

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(TaskBallState())
    val state: StateFlow<TaskBallState> = _state.asStateFlow()

    @Volatile
    private var control: IFxAppControl? = null

    init {
        appScope.launch(Dispatchers.Default) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.ChatGenerationUpdate -> onGenerationUpdate(event)
                    is AppEvent.ChatGenerationEnded -> {
                        val activeId = _state.value.conversationId
                        if (activeId == null || activeId == event.conversationId) {
                            // Keep ball visible if Runtime still busy
                            if (!agentManager.isDeviceBusy) hide()
                        }
                    }
                    else -> {}
                }
            }
        }
        appScope.launch(Dispatchers.Default) {
            agentRuntimeEventBus.events.collect { event ->
                onRuntimeEvent(event)
            }
        }
    }

    private fun onRuntimeEvent(event: me.rerere.rikkahub.data.agent.AgentRuntimeEvent) {
        val conversationId = event.conversationId?.let {
            runCatching { Uuid.parse(it) }.getOrNull()
        }
        when (event) {
            is me.rerere.rikkahub.data.agent.AgentRuntimeEvent.TaskQueued -> {
                show(statusText = "任务排队中…", conversationId = conversationId)
            }
            is me.rerere.rikkahub.data.agent.AgentRuntimeEvent.TaskStarted -> {
                show(statusText = "正在执行：${event.goal}", conversationId = conversationId)
            }
            is me.rerere.rikkahub.data.agent.AgentRuntimeEvent.StateUpdated -> {
                val agentState = agentManager.agentState.value
                    ?.takeIf { it.taskId == event.taskId }
                    ?: me.rerere.rikkahub.data.agent.AgentState(
                        taskId = event.taskId,
                        goal = "",
                        phase = event.phase,
                        currentPackage = event.currentApp,
                        currentActivity = event.currentActivity,
                        lastAction = event.lastAction,
                        lastActionResult = event.lastResult,
                    )
                show(
                    statusText = me.rerere.rikkahub.data.agent.status.AgentStatusFormatter.format(agentState),
                    conversationId = conversationId,
                )
            }
            is me.rerere.rikkahub.data.agent.AgentRuntimeEvent.TaskSucceeded -> {
                show(statusText = "已完成", conversationId = conversationId)
                mainHandler.postDelayed({ hide() }, 1200L)
            }
            is me.rerere.rikkahub.data.agent.AgentRuntimeEvent.TaskFailed,
            is me.rerere.rikkahub.data.agent.AgentRuntimeEvent.TaskCancelled,
            -> {
                show(statusText = "已停止", conversationId = conversationId)
                mainHandler.postDelayed({ hide() }, 1200L)
            }
            // Progress / PhaseChanged / ActionStarted / ActionFinished: UI ignores (debug only)
            else -> {}
        }
    }

    fun onPhoneToolStarted(toolName: String, conversationId: Uuid? = null) {
        val status = statusForTool(toolName) ?: return
        val id = conversationId ?: _state.value.conversationId
        show(statusText = status, conversationId = id)
    }

    fun show(
        statusText: String = "任务进行中",
        conversationId: Uuid? = null,
    ) {
        val assistant = settingsStore.settingsFlow.value.getCurrentAssistant()
        val next = TaskBallState(
            visible = true,
            expanded = _state.value.expanded,
            conversationId = conversationId ?: _state.value.conversationId,
            assistantName = assistant.name.ifBlank { "Solace" },
            avatar = assistant.avatar,
            statusText = statusText,
            overlayPermissionNeeded = !context.canDrawOverlays(),
        )
        _state.value = next
        mainHandler.post { ensureFloatInstalled() }
    }

    fun hide() {
        _state.update { it.copy(visible = false, expanded = false, statusText = "任务进行中") }
        mainHandler.post { control?.hide() }
    }

    fun toggleExpanded() {
        _state.update { it.copy(expanded = !it.expanded) }
    }

    fun collapse() {
        _state.update { it.copy(expanded = false) }
    }

    fun openApp() {
        val conversationId = _state.value.conversationId
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (conversationId != null) {
                putExtra("conversationId", conversationId.toString())
            }
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "openApp failed", it) }
        collapse()
    }

    fun stopTask() {
        agentManager.cancel("user_stop")
        val conversationId = _state.value.conversationId ?: run {
            hide()
            return
        }
        appScope.launch {
            runCatching {
                get<ChatService>().stopGeneration(conversationId)
            }.onFailure { Log.w(TAG, "stopGeneration failed", it) }
            hide()
        }
    }

    fun requestOverlayPermission() {
        context.openOverlayPermissionSettings()
    }

    private fun onGenerationUpdate(event: AppEvent.ChatGenerationUpdate) {
        val tools = event.lastMessage.parts.filterIsInstance<UIMessagePart.Tool>()
            .filter { it.toolName in PHONE_CONTROL_TOOLS }
        if (tools.isEmpty()) return

        val latest = tools.last()
        val status = if (latest.isExecuted) {
            statusForToolDone(latest.toolName)
        } else {
            statusForTool(latest.toolName) ?: "任务进行中"
        }
        show(statusText = status, conversationId = event.conversationId)
    }

    private fun ensureFloatInstalled() {
        if (!context.canDrawOverlays()) {
            Log.i(TAG, "overlay permission missing; SYSTEM_AUTO may fall back to in-app float")
        }

        val current = control
        if (current != null) {
            current.show()
            return
        }

        runCatching {
            control = FloatingX.install {
                setContext(context)
                setTag(FLOAT_TAG)
                enableComposeSupport()
                setScopeType(FxScopeType.SYSTEM_AUTO)
                setGravity(FxGravity.RIGHT_OR_CENTER)
                setOffsetXY(-16f, 0f)
                setEnableAnimation(true)
                setEnableEdgeAdsorption(true)
                setEdgeOffset(8f)
                setLayoutView(createComposeView())
            }.also { it.show() }
        }.onFailure {
            Log.e(TAG, "install floating task ball failed", it)
        }
    }

    private fun createComposeView(): View {
        return ComposeView(context).apply {
            setContent {
                RikkahubTheme {
                    val ballState by state.collectAsState()
                    if (ballState.visible) {
                        TaskBallContent(
                            state = ballState,
                            onToggleExpand = { toggleExpanded() },
                            onOpenApp = { openApp() },
                            onStop = { stopTask() },
                            onRequestPermission = { requestOverlayPermission() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun statusForTool(toolName: String): String? = when (toolName) {
            "see_screen" -> "正在查看屏幕…"
            "open_app" -> "正在打开应用…"
            "dump_ui" -> "正在查看界面…"
            "ui_click" -> "正在点击…"
            "ui_swipe" -> "正在滑动…"
            "ui_type" -> "正在输入…"
            "ui_global" -> "正在系统操作…"
            else -> null
        }

        fun statusForToolDone(toolName: String): String = when (toolName) {
            "see_screen" -> "已看清屏幕"
            "open_app" -> "已打开应用"
            "dump_ui" -> "已读取界面"
            "ui_click" -> "已完成点击"
            "ui_swipe" -> "已完成滑动"
            "ui_type" -> "已完成输入"
            "ui_global" -> "系统操作完成"
            else -> "任务进行中"
        }
    }
}
