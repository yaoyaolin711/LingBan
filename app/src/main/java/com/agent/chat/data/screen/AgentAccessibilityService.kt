package com.agent.chat.data.screen

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 通过 AccessibilityService 读取屏幕上的文字内容。
 * 用户需要在系统设置 → 无障碍 → 灵伴 中手动开启。
 * 不需要 root，不需要电脑，纯手机实现。
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                ScreenContentStore.updateForegroundApp(pkg)
                captureScreenText()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                captureScreenText()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    private fun captureScreenText() {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        traverseNode(root, texts, maxDepth = 15)
        root.recycle()
        if (texts.isNotEmpty()) {
            ScreenContentStore.updateScreenText(texts)
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo, out: MutableList<String>, maxDepth: Int, depth: Int = 0) {
        if (depth > maxDepth) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text.length in 2..500) {
            out.add(text)
        }

        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank() && desc.length in 2..200 && desc != text) {
            out.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, out, maxDepth, depth + 1)
            child.recycle()
        }
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
