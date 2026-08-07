package me.rerere.rikkahub.ui.components.companion

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CheckmarkCircle01
import me.rerere.rikkahub.data.accessibility.AccessibilityKeepAlive
import me.rerere.rikkahub.data.device.CompanionAssistSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.canDrawOverlays
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import me.rerere.rikkahub.utils.isSolaceAccessibilityEnabled
import me.rerere.rikkahub.utils.openAccessibilitySettings
import me.rerere.rikkahub.utils.openOverlayPermissionSettings
import me.rerere.rikkahub.utils.openUsageAccessSettings

@Immutable
data class CompanionPermissionStep(
    val id: String,
    val title: String,
    val description: String,
    val required: Boolean,
    val isGranted: (Context) -> Boolean,
    val openSettings: (Context) -> Unit,
)

fun buildCompanionPermissionSteps(
    assistant: Assistant,
    companionAssist: CompanionAssistSetting,
): List<CompanionPermissionStep> {
    val steps = mutableListOf(
        CompanionPermissionStep(
            id = "overlay",
            title = "悬浮窗权限",
            description = "显示悬浮伴侣头像或像素桌宠",
            required = true,
            isGranted = { it.canDrawOverlays() },
            openSettings = { it.openOverlayPermissionSettings() },
        ),
    )
    if (companionAssist.monitorEnabled || assistant.proactiveChatEnabled) {
        steps.add(
            CompanionPermissionStep(
                id = "usage_stats",
                title = "使用情况访问",
                description = "后台使用监测与主动关怀",
                required = false,
                isGranted = { it.hasUsageStatsPermission() },
                openSettings = { it.openUsageAccessSettings() },
            ),
        )
    }
    steps.add(
        CompanionPermissionStep(
            id = "battery",
            title = "电池优化白名单",
            description = "避免系统杀后台导致伴侣消失",
            required = false,
            isGranted = { AccessibilityKeepAlive.isIgnoringBatteryOptimizations(it) },
            openSettings = { AccessibilityKeepAlive.requestIgnoreBatteryOptimizations(it) },
        ),
    )
    if (assistant.localTools.contains(LocalToolOption.PhoneControl)) {
        steps.add(
            CompanionPermissionStep(
                id = "accessibility",
                title = "无障碍服务",
                description = "Phone Control 读屏与点击操作",
                required = false,
                isGranted = { it.isSolaceAccessibilityEnabled() },
                openSettings = { it.openAccessibilitySettings() },
            ),
        )
    }
    return steps
}

fun companionRequiredPermissionsGranted(
    context: Context,
    assistant: Assistant,
    companionAssist: CompanionAssistSetting,
): Boolean = buildCompanionPermissionSteps(assistant, companionAssist)
    .filter { it.required }
    .all { it.isGranted(context) }

/**
 * 一站式伴侣权限清单：从系统设置返回后自动刷新状态。
 */
@Composable
fun CompanionPermissionGuideDialog(
    assistant: Assistant,
    companionAssist: CompanionAssistSetting,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val steps = remember(assistant, companionAssist, refreshKey) {
        buildCompanionPermissionSteps(assistant, companionAssist)
    }
    val allRequiredGranted = remember(steps, refreshKey) {
        steps.filter { it.required }.all { it.isGranted(context) }
    }
    val pendingRecommended = remember(steps, refreshKey) {
        steps.filter { !it.required && !it.isGranted(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("伴侣权限引导") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "开启陪伴模式后，建议完成以下权限设置，确保悬浮伴侣与主动关怀正常工作。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                steps.forEach { step ->
                    val granted = step.isGranted(context)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = if (granted) {
                                HugeIcons.CheckmarkCircle01
                            } else {
                                HugeIcons.CheckmarkCircle01
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(20.dp),
                            tint = if (granted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = step.title + if (step.required) "（必需）" else "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!granted) {
                                TextButton(
                                    onClick = { step.openSettings(context) },
                                    modifier = Modifier.padding(start = 0.dp),
                                ) {
                                    Text("去设置")
                                }
                            }
                        }
                    }
                }
                if (pendingRecommended.isNotEmpty() && allRequiredGranted) {
                    Text(
                        text = "必需权限已就绪。推荐项可稍后在助手「本地工具」中继续配置。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (allRequiredGranted) "完成" else "稍后设置")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}
