package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.service.SolaceAccessibilityService
import me.rerere.rikkahub.utils.openAccessibilitySettings

private fun a11yMissingPayload(): String = buildJsonObject {
    put("error", "NO_ACCESSIBILITY")
    put(
        "message",
        "Solace Accessibility Service is not enabled. Open system Accessibility settings and enable Solace, then retry."
    )
}.toString()

private fun requireService(context: Context): SolaceAccessibilityService? {
    val service = SolaceAccessibilityService.instance
    if (service == null) {
        context.openAccessibilitySettings()
    }
    return service
}

internal fun buildDumpUiTool(context: Context): Tool = Tool(
    name = "dump_ui",
    description = """
        Dump the current on-screen UI as a compact accessibility node list (text, description,
        viewId, clickable, bounds, center coordinates). Use this before clicking. Requires the
        Solace Accessibility Service to be enabled. Returns at most 'max_nodes' interactive/text nodes.
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
        val service = requireService(context) ?: return@Tool listOf(UIMessagePart.Text(a11yMissingPayload()))
        val max = it.jsonObject["max_nodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 80
        val nodes = service.dumpUi(max)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("count", nodes.size)
                    put("nodes", buildJsonArray {
                        nodes.forEach { n ->
                            add(
                                buildJsonObject {
                                    put("i", n.index)
                                    put("class", n.className)
                                    put("text", n.text)
                                    put("desc", n.contentDescription)
                                    put("id", n.viewId)
                                    put("clickable", n.clickable)
                                    put("editable", n.editable)
                                    put("bounds", n.bounds)
                                    put("x", n.centerX)
                                    put("y", n.centerY)
                                }
                            )
                        }
                    })
                }.toString()
            )
        )
    },
)

internal fun buildUiClickTool(context: Context): Tool = Tool(
    name = "ui_click",
    description = """
        Click a UI element on the user's phone. Prefer 'text' (visible label / contentDescription)
        or 'view_id'. You may also pass absolute screen coordinates 'x' and 'y' from dump_ui.
        Requires Accessibility Service. Ask the user before destructive actions.
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
    needsApproval = { true },
    execute = {
        val service = requireService(context) ?: return@Tool listOf(UIMessagePart.Text(a11yMissingPayload()))
        val params = it.jsonObject
        val text = params["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val viewId = params["view_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val x = params["x"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val y = params["y"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val exact = params["exact"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true

        val result = when {
            text.isNotEmpty() -> service.clickByText(text, partial = !exact)
            viewId.isNotEmpty() -> {
                val id = if (viewId.contains(':')) viewId else {
                    // try common package-qualified lookup via dump match
                    viewId
                }
                // Prefer full id if provided; otherwise search by short id via dump
                if (id.contains(':')) {
                    service.clickByViewId(id)
                } else {
                    val match = service.dumpUi(120).firstOrNull { n ->
                        n.viewId == id || n.viewId.endsWith("/$id")
                    }
                    if (match != null) service.clickAt(match.centerX, match.centerY)
                    else service.clickByViewId(id)
                }
            }
            x != null && y != null -> service.clickAt(x, y)
            else -> SolaceAccessibilityService.ActionResult(
                false,
                "Provide text, view_id, or x+y"
            )
        }
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

internal fun buildUiSwipeTool(context: Context): Tool = Tool(
    name = "ui_swipe",
    description = """
        Swipe on screen from (x1,y1) to (x2,y2). Useful for scrolling feeds. Coordinates from dump_ui.
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
    needsApproval = { true },
    execute = {
        val service = requireService(context) ?: return@Tool listOf(UIMessagePart.Text(a11yMissingPayload()))
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
        val result = service.swipe(x1, y1, x2, y2, duration)
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

internal fun buildUiTypeTool(context: Context): Tool = Tool(
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
    needsApproval = { true },
    execute = {
        val service = requireService(context) ?: return@Tool listOf(UIMessagePart.Text(a11yMissingPayload()))
        val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
        val append = it.jsonObject["append"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
        val result = service.typeText(text, append)
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

internal fun buildUiGlobalTool(context: Context): Tool = Tool(
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
    needsApproval = { true },
    execute = {
        val service = requireService(context) ?: return@Tool listOf(UIMessagePart.Text(a11yMissingPayload()))
        val action = it.jsonObject["action"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val result = service.globalAction(action)
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

internal fun buildOpenAppTool(context: Context): Tool = Tool(
    name = "open_app",
    description = """
        Launch an installed app by package name (preferred) or app label. Examples:
        package=com.ss.android.ugc.aweme for Douyin. Does not require Accessibility for launch,
        but further UI control still needs Accessibility.
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
            }
        )
    },
    needsApproval = { true },
    execute = {
        val params = it.jsonObject
        val pkg = params["package"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val name = params["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val pm = context.packageManager

        val blocked = setOf(
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
        )
        val targetPkg = when {
            pkg.isNotEmpty() -> pkg
            name.isNotEmpty() -> resolvePackageByLabel(pm, name)
            else -> null
        }
        if (targetPkg.isNullOrBlank()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("ok", false)
                        put("message", "Provide package or name")
                    }.toString()
                )
            )
        }
        if (targetPkg in blocked || targetPkg.startsWith("com.android.settings")) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("ok", false)
                        put("message", "Opening system settings / phone apps is blocked for safety")
                    }.toString()
                )
            )
        }
        val launch = pm.getLaunchIntentForPackage(targetPkg)
        if (launch == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("ok", false)
                        put("message", "No launcher activity for $targetPkg")
                    }.toString()
                )
            )
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return@Tool try {
            context.startActivity(launch)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("ok", true)
                        put("package", targetPkg)
                        put("message", "Launched $targetPkg")
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("ok", false)
                        put("message", e.message ?: "Launch failed")
                    }.toString()
                )
            )
        }
    },
)

private fun resolvePackageByLabel(pm: PackageManager, label: String): String? {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
    val exact = apps.firstOrNull {
        it.loadLabel(pm).toString().equals(label, ignoreCase = true)
    }
    if (exact != null) return exact.activityInfo.packageName
    return apps.firstOrNull {
        it.loadLabel(pm).toString().contains(label, ignoreCase = true)
    }?.activityInfo?.packageName
}

internal fun buildPhoneControlTools(context: Context): List<Tool> = listOf(
    buildDumpUiTool(context),
    buildUiClickTool(context),
    buildUiSwipeTool(context),
    buildUiTypeTool(context),
    buildUiGlobalTool(context),
    buildOpenAppTool(context),
)
