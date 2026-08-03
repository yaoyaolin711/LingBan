package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.uuid.Uuid

/**
 * 聊天列表跟底状态：
 * - 默认跟底；生成中持续跟底
 * - 用户上翻 → 本轮停止跟底；手动回到底部 → 恢复
 * - 新一轮生成（loading false→true）→ 重新跟底
 * - 打开输入框（IME 升起）→ 跟底并钉住末尾
 */
@Composable
fun rememberChatFollowBottom(
    conversationId: Uuid,
    loading: Boolean,
): ChatFollowBottomController {
    var followBottom by remember(conversationId) { mutableStateOf(true) }
    var programmaticScroll by remember(conversationId) { mutableStateOf(false) }
    var prevLoading by remember(conversationId) { mutableStateOf(false) }

    LaunchedEffect(conversationId, loading) {
        if (loading && !prevLoading) {
            followBottom = true
        }
        prevLoading = loading
    }

    return ChatFollowBottomController(
        followBottom = followBottom,
        setFollowBottom = { followBottom = it },
        programmaticScroll = programmaticScroll,
        setProgrammaticScroll = { programmaticScroll = it },
    )
}

class ChatFollowBottomController(
    val followBottom: Boolean,
    val setFollowBottom: (Boolean) -> Unit,
    val programmaticScroll: Boolean,
    val setProgrammaticScroll: (Boolean) -> Unit,
)

/**
 * 统一跟底：流式更新 / 新消息 / 键盘升起。
 * 用户手势上翻会断开本轮跟底（见 [ChatUserScrollDetachEffect]）。
 */
@Composable
fun ChatFollowBottomEffect(
    lazyListState: LazyListState,
    controller: ChatFollowBottomController,
    enabled: Boolean,
    loading: Boolean,
    messageCount: Int,
    streamKey: Any?,
) {
    val follow by rememberUpdatedState(controller.followBottom)
    val enabledState by rememberUpdatedState(enabled)

    // 流式 / 条数变化时跟底
    LaunchedEffect(enabledState, follow, loading, messageCount, streamKey) {
        if (!enabledState || !follow || messageCount <= 0) return@LaunchedEffect
        controller.setProgrammaticScroll(true)
        try {
            scrollChatListToBottom(lazyListState, animated = !loading)
        } finally {
            controller.setProgrammaticScroll(false)
        }
    }

    // 键盘升起：默认跟底并钉住末尾
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    var previousImeHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(lazyListState, enabledState) {
        snapshotFlow { ime.getBottom(density) }
            .distinctUntilChanged()
            .collect { keyboardHeight ->
                val growing = keyboardHeight > previousImeHeight
                previousImeHeight = keyboardHeight
                if (!enabledState || !growing || keyboardHeight <= 0) return@collect
                controller.setFollowBottom(true)
                controller.setProgrammaticScroll(true)
                try {
                    scrollChatListToBottom(lazyListState, animated = false)
                } finally {
                    controller.setProgrammaticScroll(false)
                }
            }
    }
}

/** 用户上翻断开本轮跟底；滚回底部则恢复。 */
@Composable
fun ChatUserScrollDetachEffect(
    lazyListState: LazyListState,
    controller: ChatFollowBottomController,
) {
    val programmatic by rememberUpdatedState(controller.programmaticScroll)
    LaunchedEffect(lazyListState) {
        var wasInProgress = false
        snapshotFlow {
            Triple(
                lazyListState.isScrollInProgress,
                lazyListState.canScrollForward,
                programmatic,
            )
        }.collect { (inProgress, canForward, isProgrammatic) ->
            if (isProgrammatic) {
                wasInProgress = inProgress
                return@collect
            }
            if (inProgress && canForward) {
                controller.setFollowBottom(false)
            } else if (!inProgress && wasInProgress && !canForward) {
                controller.setFollowBottom(true)
            }
            wasInProgress = inProgress
        }
    }
}

suspend fun scrollChatListToBottom(
    state: LazyListState,
    animated: Boolean,
) {
    repeat(4) {
        val target = state.layoutInfo.totalItemsCount - 1
        if (target >= 0) {
            if (animated) {
                state.animateScrollToItem(target)
            } else {
                state.scrollToItem(target)
            }
            return
        }
        kotlinx.coroutines.delay(16)
    }
}

/**
 * 兼容旧调用：仅键盘升起时钉底（无跟底状态机时使用）。
 */
@Composable
fun ImeLazyListAutoScroller(
    lazyListState: LazyListState,
) {
    val ime = WindowInsets.ime
    val localDensity = LocalDensity.current
    var previousImeHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(lazyListState) {
        snapshotFlow { ime.getBottom(localDensity) }
            .distinctUntilChanged()
            .collect { keyboardHeight ->
                val growing = keyboardHeight > previousImeHeight
                previousImeHeight = keyboardHeight
                if (!growing || keyboardHeight <= 0) return@collect
                scrollChatListToBottom(lazyListState, animated = false)
            }
    }
}
