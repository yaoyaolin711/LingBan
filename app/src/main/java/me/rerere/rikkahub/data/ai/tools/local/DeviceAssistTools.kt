package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.device.CompanionIntervention
import me.rerere.rikkahub.data.device.DeviceShellExecutor
import me.rerere.rikkahub.data.device.UsageStatsQuery
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.utils.hasUsageStatsPermission

internal fun buildGetForegroundAppTool(context: Context, eventBus: AppEventBus): Tool = Tool(
    name = "get_foreground_app",
    description = """
        Get the app currently in the foreground on the user's phone, including package name,
        display name, and how long it has been continuously in the foreground (minutes/ms).
        Requires Usage access permission. Returns null fields if the home launcher or Solace
        itself is in front, or if no foreground app can be determined.
    """.trimIndent().replace("\n", " "),
    parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
    execute = {
        if (!context.hasUsageStatsPermission()) {
            eventBus.emit(AppEvent.OpenUsageAccessSettings)
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "NO_PERMISSION")
                        put("message", "Usage access permission is not granted.")
                    }.toString()
                )
            )
        }
        val info = UsageStatsQuery.getForegroundApp(context)
        val payload = if (info == null) {
            buildJsonObject {
                put("package", "")
                put("app_name", "")
                put("continuous_ms", 0)
                put("continuous_minutes", 0)
                put("message", "No third-party foreground app detected (launcher or Solace).")
            }
        } else {
            buildJsonObject {
                put("package", info.packageName)
                put("app_name", info.appName)
                put("continuous_ms", info.continuousMs)
                put("continuous_minutes", info.continuousMinutes)
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

internal fun buildGetAppSessionTool(context: Context, eventBus: AppEventBus): Tool = Tool(
    name = "get_app_session",
    description = """
        Query today's cumulative foreground time and current continuous session for a package.
        Useful for digital-wellbeing checks (e.g. Douyin / TikTok package com.ss.android.ugc.aweme).
        Requires Usage access permission.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("package", buildJsonObject {
                    put("type", "string")
                    put("description", "Android package name, e.g. com.ss.android.ugc.aweme")
                })
            },
            required = listOf("package"),
        )
    },
    execute = {
        if (!context.hasUsageStatsPermission()) {
            eventBus.emit(AppEvent.OpenUsageAccessSettings)
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "NO_PERMISSION")
                        put("message", "Usage access permission is not granted.")
                    }.toString()
                )
            )
        }
        val pkg = it.jsonObject["package"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (pkg.isEmpty()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "INVALID_ARGS")
                        put("message", "package is required")
                    }.toString()
                )
            )
        }
        val session = UsageStatsQuery.getAppSession(context, pkg)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("package", session.packageName)
                    put("app_name", session.appName)
                    put("today_total_ms", session.todayTotalMs)
                    put("today_total_minutes", session.todayTotalMs / 60_000)
                    put("is_foreground", session.isForeground)
                    put("continuous_ms", session.continuousMs)
                    put("continuous_minutes", session.continuousMs / 60_000)
                }.toString()
            )
        )
    },
)

internal fun buildOpenSolaceTool(intervention: CompanionIntervention): Tool = Tool(
    name = "open_solace",
    description = """
        Bring the Solace app to the foreground and append a care/reminder message to the recent chat.
        Use this when the user has been using another app for too long and should take a break,
        or when you need to interrupt and talk to the user on-device. Optional 'message' is the
        text shown as an assistant message; if omitted a short default reminder is used.
        Does not require tool approval when Device Assist is enabled.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("message", buildJsonObject {
                    put("type", "string")
                    put("description", "Care / reminder text to show in Solace chat")
                })
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "Conversation title. Default: 使用关怀")
                })
            }
        )
    },
    needsApproval = { false },
    execute = {
        val params = it.jsonObject
        val message = params["message"]?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { text -> text.isNotEmpty() }
            ?: "先停一下吧。你已经刷了挺久了，休息一会儿眼睛和身体都会更舒服。我在这里陪你。"
        val title = params["title"]?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { text -> text.isNotEmpty() }
            ?: "使用关怀"
        val conversationId = intervention.openSolaceWithMessage(message = message, title = title)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("ok", true)
                    put("conversation_id", conversationId.toString())
                    put("message", message)
                }.toString()
            )
        )
    },
)

internal fun buildNotifyUserTool(intervention: CompanionIntervention): Tool = Tool(
    name = "notify_user",
    description = """
        Send a gentle system notification to the user without forcing Solace to the foreground.
        Prefer this for soft reminders; use open_solace when you must interrupt and talk.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "Notification title")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "Notification body")
                })
            },
            required = listOf("content"),
        )
    },
    needsApproval = { false },
    execute = {
        val params = it.jsonObject
        val title = params["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { "Solace" }
        val content = params["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (content.isEmpty()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "INVALID_ARGS")
                        put("message", "content is required")
                    }.toString()
                )
            )
        }
        intervention.notifyUser(title, content)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("ok", true)
                    put("title", title)
                    put("content", content)
                }.toString()
            )
        )
    },
)

internal fun buildDeviceShellTool(settingsStore: SettingsStore): Tool = Tool(
    name = "device_shell",
    description = """
        Advanced: run a whitelisted shell command via Shizuku (e.g. 'am force-stop PACKAGE',
        'input keyevent KEYCODE_HOME'). Requires companionAssist.enableAdvancedShell=true and
        a running Shizuku authorization. Dangerous; always ask the user before force-stopping apps.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Whitelisted shell command")
                })
            },
            required = listOf("command"),
        )
    },
    needsApproval = { true },
    execute = {
        if (!settingsStore.settingsFlow.value.companionAssist.enableAdvancedShell) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "DISABLED")
                        put(
                            "message",
                            "Advanced shell is disabled. Enable it in Device Assist settings and authorize Shizuku."
                        )
                    }.toString()
                )
            )
        }
        val command = it.jsonObject["command"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val result = DeviceShellExecutor.execute(command)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("exit_code", result.exitCode)
                    put("stdout", result.stdout)
                    put("stderr", result.stderr)
                    result.error?.let { err -> put("error", err) }
                    put("shizuku_available", DeviceShellExecutor.isShizukuAvailable())
                    put("shizuku_permission", DeviceShellExecutor.hasShizukuPermission())
                }.toString()
            )
        )
    },
)
