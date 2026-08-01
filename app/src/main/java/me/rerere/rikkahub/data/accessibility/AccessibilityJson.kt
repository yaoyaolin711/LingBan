package me.rerere.rikkahub.data.accessibility

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * JSON helpers for Agent-facing accessibility payloads.
 */
object AccessibilityJson {
    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun snapshotToJson(snapshot: UISnapshot): String = json.encodeToString(snapshot)

    fun observationToJson(observation: UIObservation): String = json.encodeToString(observation)

    /**
     * Agent-friendly snapshot shape aligned with the perception contract:
     * `{ page, packageName, timestamp, nodes: [rootTree], ... }`
     */
    fun snapshotForAgent(snapshot: UISnapshot): String = buildJsonObject {
        put("page", snapshot.page)
        put("packageName", snapshot.packageName)
        put("timestamp", snapshot.timestamp)
        put("windowTitle", snapshot.windowTitle)
        put("screen", "${snapshot.screenWidth}x${snapshot.screenHeight}")
        put("nodeCount", snapshot.nodeCount)
        put("truncated", snapshot.truncated)
        put("nodes", json.encodeToJsonElement(snapshot.nodes))
    }.toString()

    /**
     * Unified observation: `{ source, elements, page, ... }` (+ optional tree).
     */
    fun observationForAgent(
        observation: UIObservation,
        includeTree: Boolean = true,
    ): String = buildJsonObject {
        put("source", observation.source)
        put("page", observation.page)
        put("packageName", observation.packageName)
        put("timestamp", observation.timestamp)
        put("windowTitle", observation.windowTitle)
        put("screen", "${observation.screenWidth}x${observation.screenHeight}")
        put("truncated", observation.truncated)
        put("elements", json.encodeToJsonElement(observation.elements))
        if (includeTree && observation.tree != null) {
            put("tree", json.encodeToJsonElement(observation.tree))
        }
    }.toString()

    /**
     * Agent-friendly unified multimodal observation for Planner.
     */
    fun unifiedForAgent(observation: UnifiedObservation): String = buildJsonObject {
        put("page", observation.page)
        put("packageName", observation.packageName)
        put("timestamp", observation.timestamp)
        put("screen", "${observation.screenWidth}x${observation.screenHeight}")
        put("hasScreenshot", observation.hasScreenshot)
        observation.ocrEngine?.let { put("ocrEngine", it) }
        put("accessibilityElements", json.encodeToJsonElement(observation.accessibilityElements))
        put("ocrElements", json.encodeToJsonElement(observation.ocrElements))
        put("visualElements", json.encodeToJsonElement(observation.visualElements))
        put("fusedElements", json.encodeToJsonElement(observation.fusedElements))
    }.toString()

    fun mergeExtra(base: String, extra: JsonObjectBuilder.() -> Unit): String {
        val parsed = runCatching {
            json.parseToJsonElement(base) as? JsonObject
        }.getOrNull() ?: return base
        return buildJsonObject {
            parsed.forEach { (k, v) -> put(k, v) }
            extra()
        }.toString()
    }
}
