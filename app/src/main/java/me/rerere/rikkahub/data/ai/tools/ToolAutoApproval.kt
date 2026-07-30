package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.Tool

/** Tools that commonly need one-shot approval; users can permanently auto-approve them. */
val CommonAutoApprovableTools = listOf(
    "open_app",
    "ui_click",
    "ui_swipe",
    "ui_type",
    "ui_global",
    "notify_user",
    "get_location",
    "calendar_create",
    "device_shell",
)

fun Tool.withAutoApprovalBypass(autoApproved: Set<String>): Tool {
    if (name !in autoApproved) return this
    return copy(needsApproval = { false })
}

fun List<Tool>.withAutoApprovalBypass(autoApproved: Set<String>): List<Tool> =
    if (autoApproved.isEmpty()) this else map { it.withAutoApprovalBypass(autoApproved) }
