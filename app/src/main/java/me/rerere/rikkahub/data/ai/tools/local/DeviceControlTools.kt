package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.accessibility.AccessibilityJson
import me.rerere.rikkahub.data.accessibility.UIObservation
import me.rerere.rikkahub.data.accessibility.toElement
import me.rerere.rikkahub.data.agent.capability.PhoneControlCore
import me.rerere.rikkahub.data.agent.capability.vision.supportsVisionInput
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.ocr.ScreenOcrHelper
import me.rerere.rikkahub.overlay.TaskBallManager
import me.rerere.rikkahub.service.SolaceAccessibilityService
import org.koin.core.context.GlobalContext
import java.io.File
import java.net.URI

private fun a11yMissingPayload(): String = buildJsonObject {
    put("error", "NO_ACCESSIBILITY")
    put(
        "message",
        "Solace Accessibility Service is not enabled. Open system Accessibility settings and enable Solace, then retry."
    )
}.toString()

private fun deviceBusyPayload(): String = buildJsonObject {
    put("error", PhoneControlCore.ERROR_DEVICE_BUSY)
    put(
        "message",
        "AgentRuntime is controlling the device. Retry after the task finishes."
    )
}.toString()

private fun requireCore(
    core: PhoneControlCore,
    openSettingsIfMissing: Boolean = true,
): PhoneControlCore? {
    core.guardToolsAllowed()?.let { return null }
    if (!core.isAccessibilityAvailable()) {
        if (openSettingsIfMissing) core.serviceOrNull(openSettingsIfMissing = true)
        return null
    }
    return core
}

/** Distinguishes busy vs missing a11y for tool responses. */
private fun toolGateError(core: PhoneControlCore): String {
    if (core.isRuntimeExclusive()) return deviceBusyPayload()
    return a11yMissingPayload()
}

private fun nodesJson(nodes: List<SolaceAccessibilityService.UiNode>) = buildJsonArray {
    nodes.forEach { n ->
        add(
            buildJsonObject {
                put("i", n.index)
                put("nodeId", n.nodeId)
                put("class", n.className)
                put("text", n.text)
                put("desc", n.contentDescription)
                put("id", n.viewId)
                put("clickable", n.clickable)
                put("enabled", n.enabled)
                put("editable", n.editable)
                put("scrollable", n.scrollable)
                put("checkable", n.checkable)
                put("checked", n.checked)
                put("focused", n.focused)
                put("selected", n.selected)
                put("bounds", n.bounds)
                put("x", n.centerX)
                put("y", n.centerY)
                n.parentNodeId?.let { put("parentNodeId", it) }
            }
        )
    }
}

private fun snapshotJson(
    snapshot: SolaceAccessibilityService.UiSnapshot,
    extra: (JsonObjectBuilder.() -> Unit)? = null,
): String = buildJsonObject {
    put("source", UIObservation.SOURCE_ACCESSIBILITY)
    put("page", snapshot.page)
    put("package", snapshot.packageName)
    put("packageName", snapshot.packageName)
    put("timestamp", snapshot.timestamp)
    put("title", snapshot.windowTitle)
    put("windowTitle", snapshot.windowTitle)
    put("screen", "${snapshot.screenWidth}x${snapshot.screenHeight}")
    put("count", snapshot.nodes.size)
    put("truncated", snapshot.truncated)
    if (snapshot.nodes.isEmpty()) {
        put(
            "hint",
            "Accessibility tree is empty (canvas/WebView/game?). Call see_screen for a screenshot, or wait and retry."
        )
    }
    put("nodes", nodesJson(snapshot.nodes))
    val observation = if (snapshot.tree != null) {
        UIObservation(
            source = UIObservation.SOURCE_ACCESSIBILITY,
            elements = snapshot.tree.flatten().map { it.toElement() },
            page = snapshot.page,
            packageName = snapshot.packageName,
            timestamp = snapshot.timestamp,
            windowTitle = snapshot.windowTitle,
            screenWidth = snapshot.screenWidth,
            screenHeight = snapshot.screenHeight,
            tree = snapshot.tree,
            truncated = snapshot.truncated,
        )
    } else {
        UIObservation(
            source = UIObservation.SOURCE_ACCESSIBILITY,
            elements = emptyList(),
            page = snapshot.page,
            packageName = snapshot.packageName,
            timestamp = snapshot.timestamp,
            windowTitle = snapshot.windowTitle,
            screenWidth = snapshot.screenWidth,
            screenHeight = snapshot.screenHeight,
            truncated = snapshot.truncated,
        )
    }
    put("elements", AccessibilityJson.json.encodeToJsonElement(observation.elements))
    snapshot.tree?.let { put("tree", AccessibilityJson.json.encodeToJsonElement(it)) }
    extra?.invoke(this)
}.toString()

internal fun buildDumpUiTool(core: PhoneControlCore): Tool = Tool(
    name = "dump_ui",
    description = """
        Dump the current on-screen UI as a compact accessibility node list (package, title,
        text/desc/id/clickable/bounds/x,y). ALWAYS call this or see_screen before clicking.
        If count=0, call see_screen. Requires Solace Accessibility Service.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("max_nodes", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max nodes to return, default 80, max 120")
                })
            }
        )
    },
    execute = {
        val gated = requireCore(core) ?: return@Tool listOf(UIMessagePart.Text(toolGateError(core)))
        val max = it.jsonObject["max_nodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 80
        val snapshot = gated.dumpUiSnapshot(max)
            ?: return@Tool listOf(UIMessagePart.Text(a11yMissingPayload()))
        listOf(UIMessagePart.Text(snapshotJson(snapshot)))
    },
)

internal fun buildSeeScreenTool(core: PhoneControlCore): Tool = Tool(
    name = "see_screen",
    description = """
        Capture the current phone screen and return accessibility nodes + optional screenshot/OCR.
        OCR mode (ocr_mode):
        - auto (default): always run on-device ML Kit Chinese+English OCR for clickable ocr_blocks,
          and also return a screenshot when available (vision models can use both).
        - force: same as auto (explicit local OCR).
        - skip: never OCR; screenshot only for multimodal models.
        Prefer this after open_app / ui_click, or when dump_ui is empty.
        Click via nodes text/view_id/x,y or ocr_blocks x,y. Requires Accessibility. Screenshot needs Android 11+.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("max_nodes", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max UI nodes, default 60, max 120")
                })
                put("max_width", buildJsonObject {
                    put("type", "integer")
                    put("description", "Screenshot max width in px, default 720")
                })
                put("wait_ms", buildJsonObject {
                    put("type", "integer")
                    put("description", "Optional wait for UI settle before capture, 0-3000ms")
                })
                put("ocr_mode", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "auto | force | skip. auto/force always run local OCR for click coords."
                    )
                })
            }
        )
    },
    execute = {
        val gated = requireCore(core) ?: return@Tool listOf(UIMessagePart.Text(toolGateError(core)))
        val params = it.jsonObject
        val maxNodes = params["max_nodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 60
        val maxWidth = params["max_width"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 720
        val waitMs = params["wait_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val ocrMode = params["ocr_mode"]?.jsonPrimitive?.contentOrNull ?: "auto"

        if (waitMs > 0) {
            gated.waitForUi(timeoutMs = waitMs.coerceIn(100L, 3000L))
        }

        val snapshot = gated.dumpUiSnapshot(maxNodes)
            ?: return@Tool listOf(UIMessagePart.Text(a11yMissingPayload()))
        val parts = mutableListOf<UIMessagePart>()

        val shot = gated.captureScreenshotPng(maxWidth = maxWidth)
        val capture = shot?.getOrNull()
        if (capture != null) {
            val koin = GlobalContext.get()
            val filesManager = koin.get<FilesManager>()
            val settings = koin.get<SettingsStore>().settingsFlow.value
            val uris = filesManager.createChatFilesByByteArrays(listOf(capture.jpegBytes))
            val imageUrl = uris.first().toString()
            val imageFile = runCatching {
                File(URI(imageUrl))
            }.getOrElse {
                File(imageUrl.removePrefix("file://"))
            }

            val runOcr = ScreenOcrHelper.shouldRunLocalOcr(settings, ocrMode)
            val chatModel = ScreenOcrHelper.currentChatModel(settings)
            val visionChannel = chatModel?.supportsVisionInput() == true

            val ocrResult = if (runOcr) {
                try {
                    ScreenOcrHelper.recognizeScreen(
                        imageFile = imageFile,
                        settings = settings,
                        screenWidth = snapshot.screenWidth,
                        screenHeight = snapshot.screenHeight,
                    )
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            parts += UIMessagePart.Image(url = imageUrl)
            parts += UIMessagePart.Text(
                snapshotJson(snapshot) {
                    put("ok", true)
                    put("screenshot", true)
                    put("image_size", "${capture.width}x${capture.height}")
                    put("vision_channel", visionChannel && !runOcr)
                    put("ocr_mode", ocrMode)
                    put("ocr_ran", runOcr)
                    if (ocrResult != null && (ocrResult.text.isNotBlank() || ocrResult.blocks.isNotEmpty())) {
                        put("ocr", true)
                        put("ocr_engine", ocrResult.engine)
                        if (ocrResult.text.isNotBlank()) {
                            put("ocr_text", ocrResult.text.take(4000))
                        }
                        if (ocrResult.blocks.isNotEmpty()) {
                            put(
                                "ocr_blocks",
                                buildJsonArray {
                                    ocrResult.blocks.forEach { b ->
                                        add(
                                            buildJsonObject {
                                                put("text", b.text)
                                                put("x", b.x)
                                                put("y", b.y)
                                                put("bounds", b.bounds)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                        put(
                            "guidance",
                            "Prefer accessibility nodes when available. Else click via ocr_blocks text/x/y. Vision models may also use the screenshot."
                        )
                    } else if (runOcr) {
                        put("ocr", false)
                        put(
                            "ocr_hint",
                            "Local OCR returned empty. Use accessibility nodes, or pass ocr_mode=force after enabling LAN OCR."
                        )
                        put(
                            "guidance",
                            "Use nodes to click. If empty, retry see_screen or ask the user."
                        )
                    } else {
                        put("ocr", false)
                        put(
                            "guidance",
                            "Vision channel: inspect the screenshot image directly, and use nodes x/y or text for ui_click. Pass ocr_mode=force to also run local OCR."
                        )
                    }
                }
            )
        } else {
            val err = shot?.exceptionOrNull()
            parts += UIMessagePart.Text(
                snapshotJson(snapshot) {
                    put("ok", snapshot.nodes.isNotEmpty())
                    put("screenshot", false)
                    put("screenshot_error", err?.message ?: "screenshot failed")
                    put(
                        "guidance",
                        "Screenshot unavailable; use accessibility nodes if present, else ask user or retry see_screen."
                    )
                }
            )
        }
        parts
    },
)

internal fun buildUiClickTool(core: PhoneControlCore): Tool = Tool(
    name = "ui_click",
    description = """
        Click a UI element on the user's phone. Prefer 'text' (visible label / contentDescription)
        or 'view_id'. You may also pass absolute screen coordinates 'x' and 'y' from dump_ui / see_screen.
        After clicking, prefer see_screen or dump_ui to verify. Requires Accessibility Service.
        Ask the user before destructive actions.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Visible text or contentDescription to click (partial match OK)")
                })
                put("view_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Resource id short name or full id from dump_ui")
                })
                put("x", buildJsonObject {
                    put("type", "integer")
                    put("description", "Screen X coordinate")
                })
                put("y", buildJsonObject {
                    put("type", "integer")
                    put("description", "Screen Y coordinate")
                })
                put("exact", buildJsonObject {
                    put("type", "boolean")
                    put("description", "If true, text must match exactly. Default false (partial).")
                })
            }
        )
    },
    // PhoneControl is an explicit opt-in; requiring per-click approval breaks multi-step tasks.
    needsApproval = { false },
    execute = {
        val gated = requireCore(core) ?: return@Tool listOf(UIMessagePart.Text(toolGateError(core)))
        val params = it.jsonObject
        val text = params["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val viewId = params["view_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val x = params["x"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val y = params["y"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val exact = params["exact"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true

        val result = gated.click(
            text = text.ifEmpty { null },
            viewId = viewId.ifEmpty { null },
            x = x,
            y = y,
            exact = exact,
        )
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("ok", result.ok)
                    put("message", result.message)
                    put("hint", "Call see_screen or dump_ui to verify the new UI state.")
                }.toString()
            )
        )
    },
)

internal fun buildUiSwipeTool(core: PhoneControlCore): Tool = Tool(
    name = "ui_swipe",
    description = """
        Swipe on screen from (x1,y1) to (x2,y2). Useful for scrolling feeds. Coordinates from dump_ui / see_screen.
        Requires Accessibility Service.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("x1", buildJsonObject { put("type", "integer") })
                put("y1", buildJsonObject { put("type", "integer") })
                put("x2", buildJsonObject { put("type", "integer") })
                put("y2", buildJsonObject { put("type", "integer") })
                put("duration_ms", buildJsonObject {
                    put("type", "integer")
                    put("description", "Swipe duration in ms, default 300")
                })
            },
            required = listOf("x1", "y1", "x2", "y2"),
        )
    },
    needsApproval = { false },
    execute = {
        val gated = requireCore(core) ?: return@Tool listOf(UIMessagePart.Text(toolGateError(core)))
        val p = it.jsonObject
        val x1 = p["x1"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val y1 = p["y1"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val x2 = p["x2"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val y2 = p["y2"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val duration = p["duration_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 300L
        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("ok", false)
                        put("message", "x1,y1,x2,y2 required")
                    }.toString()
                )
            )
        }
        val result = gated.swipe(x1, y1, x2, y2, duration)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("ok", result.ok)
                    put("message", result.message)
                }.toString()
            )
        )
    },
)

internal fun buildUiTypeTool(core: PhoneControlCore): Tool = Tool(
    name = "ui_type",
    description = """
        Type text into the currently focused editable field. Click an input field first with ui_click.
        Requires Accessibility Service.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to type")
                })
                put("append", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Append instead of replace. Default false.")
                })
            },
            required = listOf("text"),
        )
    },
    needsApproval = { false },
    execute = {
        val gated = requireCore(core) ?: return@Tool listOf(UIMessagePart.Text(toolGateError(core)))
        val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
        val append = it.jsonObject["append"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
        val result = gated.typeText(text, append)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("ok", result.ok)
                    put("message", result.message)
                }.toString()
            )
        )
    },
)

internal fun buildUiGlobalTool(core: PhoneControlCore): Tool = Tool(
    name = "ui_global",
    description = """
        Perform a global system action: back, home, recents, notifications, quick_settings, power.
        Requires Accessibility Service.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "One of: back, home, recents, notifications, quick_settings, power"
                    )
                })
            },
            required = listOf("action"),
        )
    },
    needsApproval = { false },
    execute = {
        val gated = requireCore(core) ?: return@Tool listOf(UIMessagePart.Text(toolGateError(core)))
        val action = it.jsonObject["action"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val result = gated.globalAction(action)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("ok", result.ok)
                    put("message", result.message)
                }.toString()
            )
        )
    },
)

internal fun buildOpenAppTool(core: PhoneControlCore): Tool = Tool(
    name = "open_app",
    description = """
        Launch an installed app by package name (preferred) or app label. Waits briefly for the UI
        to settle and returns a compact dump_ui snapshot. Then call see_screen before clicking.
        Examples: package=com.ss.android.ugc.aweme for Douyin. Launch itself does not require
        Accessibility, but settle/dump does.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("package", buildJsonObject {
                    put("type", "string")
                    put("description", "Android package name")
                })
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "App display name to search (slower, fuzzy)")
                })
                put("wait_ms", buildJsonObject {
                    put("type", "integer")
                    put("description", "Wait for UI settle after launch, default 2000, max 5000")
                })
            }
        )
    },
    needsApproval = { false },
    execute = {
        core.guardToolsAllowed()?.let {
            return@Tool listOf(UIMessagePart.Text(deviceBusyPayload()))
        }
        val params = it.jsonObject
        val pkg = params["package"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val name = params["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val waitMs = params["wait_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 2000L

        val result = core.openApp(
            packageName = pkg.ifEmpty { null },
            appName = name.ifEmpty { null },
            waitMs = waitMs,
        )
        listOf(
            UIMessagePart.Text(
                when {
                    !result.ok -> buildJsonObject {
                        put("ok", false)
                        put("message", result.message)
                    }.toString()
                    result.snapshot != null -> snapshotJson(result.snapshot) {
                        put("ok", true)
                        put("launched", result.packageName)
                        put(
                            "message",
                            "Launched ${result.packageName}. Next: call see_screen before clicking."
                        )
                    }
                    else -> buildJsonObject {
                        put("ok", true)
                        put("package", result.packageName)
                        put(
                            "message",
                            "Launched ${result.packageName}. Enable Accessibility then call see_screen."
                        )
                    }.toString()
                }
            )
        )
    },
)

internal fun buildPhoneControlTools(
    core: PhoneControlCore,
    taskBallManager: TaskBallManager? = null,
): List<Tool> {
    val tools = listOf(
        buildSeeScreenTool(core),
        buildDumpUiTool(core),
        buildUiClickTool(core),
        buildUiSwipeTool(core),
        buildUiTypeTool(core),
        buildUiGlobalTool(core),
        buildOpenAppTool(core),
    )
    if (taskBallManager == null) return tools
    return tools.map { tool ->
        val originalExecute = tool.execute
        tool.copy(
            execute = { args ->
                taskBallManager.onPhoneToolStarted(tool.name)
                originalExecute(args)
            }
        )
    }
}
