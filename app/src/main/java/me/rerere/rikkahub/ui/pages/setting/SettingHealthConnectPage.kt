package me.rerere.rikkahub.ui.pages.setting

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.health.HealthConnectAvailability
import me.rerere.rikkahub.data.health.HealthConnectRepository
import me.rerere.rikkahub.data.health.HealthConnectSetting
import me.rerere.rikkahub.data.health.HealthDailySummary
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

@Composable
fun SettingHealthConnectPage() {
    val settingsStore: SettingsStore = koinInject()
    val healthRepo: HealthConnectRepository = koinInject()
    val settings = LocalSettings.current
    val hc = settings.healthConnect
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current

    var availability by remember { mutableStateOf(healthRepo.getAvailability()) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var summary by remember { mutableStateOf<HealthDailySummary?>(null) }
    var summaryText by remember { mutableStateOf("尚未读取") }
    var loading by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = healthRepo.permissionContract(),
    ) { result ->
        granted = result
        scope.launch {
            refreshPreview(
                healthRepo = healthRepo,
                setting = settingsStore.settingsFlow.value.healthConnect,
                onAvailability = { availability = it },
                onGranted = { granted = it },
                onSummary = { summary = it; summaryText = healthRepo.formatSummaryForUi(it) },
                onLoading = { loading = it },
            )
        }
    }

    fun updateHc(transform: (HealthConnectSetting) -> HealthConnectSetting) {
        scope.launch {
            settingsStore.update { it.copy(healthConnect = transform(it.healthConnect)) }
        }
    }

    suspend fun refresh() {
        refreshPreview(
            healthRepo = healthRepo,
            setting = settings.healthConnect,
            onAvailability = { availability = it },
            onGranted = { granted = it },
            onSummary = { summary = it; summaryText = healthRepo.formatSummaryForUi(it) },
            onLoading = { loading = it },
            forceRefresh = true,
        )
    }

    LaunchedEffect(hc.enabled, hc.includeSteps, hc.includeHeartRate, hc.includeSleep, hc.includeActivityExtras) {
        refresh()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { refresh() }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("穿戴设备 / Health Connect") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text("状态") },
                        supportingContent = {
                            Text(
                                when (availability) {
                                    HealthConnectAvailability.AVAILABLE -> "Health Connect 可用"
                                    HealthConnectAvailability.UPDATE_REQUIRED -> "需要更新 Health Connect"
                                    HealthConnectAvailability.NOT_INSTALLED -> "未安装 Health Connect（Android 13 及以下需从商店安装）"
                                    HealthConnectAvailability.UNAVAILABLE -> "当前设备不支持 Health Connect"
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("注入伴侣上下文") },
                        supportingContent = {
                            Text("开启后，聊天时会把今日步数/心率/睡眠摘要交给伴侣（只读，非医疗建议）")
                        },
                        trailingContent = {
                            Switch(
                                checked = hc.enabled,
                                onCheckedChange = { enabled ->
                                    updateHc { it.copy(enabled = enabled) }
                                },
                                enabled = availability == HealthConnectAvailability.AVAILABLE,
                            )
                        },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("授权与数据源") },
                ) {
                    item(
                        headlineContent = { Text("授权 Health Connect") },
                        supportingContent = {
                            val required = healthRepo.requiredPermissions(hc)
                            val ok = required.isNotEmpty() && required.all { it in granted }
                            Text(
                                if (ok) "已授予所需读取权限"
                                else "需要步数 / 心率 / 睡眠等读取权限。请先在小米运动健康等 App 中把数据同步到 Health Connect。"
                            )
                        },
                        trailingContent = {
                            Button(
                                onClick = {
                                    if (availability != HealthConnectAvailability.AVAILABLE) {
                                        toaster.show("请先安装或更新 Health Connect")
                                        return@Button
                                    }
                                    permissionLauncher.launch(healthRepo.requiredPermissions(hc))
                                },
                                enabled = availability == HealthConnectAvailability.AVAILABLE,
                            ) {
                                Text("授权")
                            }
                        },
                    )
                    if (availability == HealthConnectAvailability.NOT_INSTALLED ||
                        availability == HealthConnectAvailability.UPDATE_REQUIRED
                    ) {
                        item(
                            headlineContent = { Text("安装 / 更新 Health Connect") },
                            supportingContent = { Text("打开 Play 商店安装官方 Health Connect") },
                            trailingContent = {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(healthRepo.providerInstallIntent())
                                        } catch (_: ActivityNotFoundException) {
                                            toaster.show("无法打开应用商店")
                                        }
                                    }
                                ) { Text("去安装") }
                            },
                        )
                    }
                    item(
                        headlineContent = { Text("打开 Health Connect") },
                        supportingContent = { Text("检查哪些 App 正在写入数据（如小米运动健康）") },
                        trailingContent = {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(healthRepo.openHealthConnectSettingsIntent())
                                    } catch (_: ActivityNotFoundException) {
                                        toaster.show("无法打开 Health Connect 设置")
                                    }
                                },
                                enabled = availability == HealthConnectAvailability.AVAILABLE ||
                                    availability == HealthConnectAvailability.UPDATE_REQUIRED,
                            ) { Text("打开") }
                        },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("读取范围") },
                ) {
                    item(
                        headlineContent = { Text("步数") },
                        trailingContent = {
                            Switch(
                                checked = hc.includeSteps,
                                onCheckedChange = { v -> updateHc { it.copy(includeSteps = v) } },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("心率") },
                        trailingContent = {
                            Switch(
                                checked = hc.includeHeartRate,
                                onCheckedChange = { v -> updateHc { it.copy(includeHeartRate = v) } },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("睡眠") },
                        trailingContent = {
                            Switch(
                                checked = hc.includeSleep,
                                onCheckedChange = { v -> updateHc { it.copy(includeSleep = v) } },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("距离 / 卡路里") },
                        supportingContent = { Text("有数据时一并摘要") },
                        trailingContent = {
                            Switch(
                                checked = hc.includeActivityExtras,
                                onCheckedChange = { v -> updateHc { it.copy(includeActivityExtras = v) } },
                            )
                        },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("数据预览") },
                ) {
                    item(
                        headlineContent = { Text(if (loading) "读取中…" else "今日摘要") },
                        supportingContent = { Text(summaryText) },
                        trailingContent = {
                            if (loading) {
                                CircularProgressIndicator()
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            healthRepo.clearCache()
                                            refresh()
                                        }
                                    },
                                    enabled = availability == HealthConnectAvailability.AVAILABLE,
                                ) { Text("刷新") }
                            }
                        },
                    )
                    if (summary?.missingPermissions?.isNotEmpty() == true) {
                        item(
                            headlineContent = { Text("权限不完整") },
                            supportingContent = { Text("点上方「授权」补齐读取权限后再刷新") },
                        )
                    }
                }
            }

            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text("小米手环测试提示") },
                        supportingContent = {
                            Text(
                                "1. 打开「小米运动健康」或 Zepp Life，确认手环已同步\n" +
                                    "2. 在该 App 设置里开启同步到 Health Connect\n" +
                                    "3. 回到本页授权并刷新预览\n" +
                                    "若预览仍为空，先在系统 Health Connect 里确认已有步数/睡眠条目"
                            )
                        },
                    )
                }
            }
        }
    }
}

private suspend fun refreshPreview(
    healthRepo: HealthConnectRepository,
    setting: HealthConnectSetting,
    onAvailability: (HealthConnectAvailability) -> Unit,
    onGranted: (Set<String>) -> Unit,
    onSummary: (HealthDailySummary?) -> Unit,
    onLoading: (Boolean) -> Unit,
    forceRefresh: Boolean = false,
) {
    onLoading(true)
    try {
        onAvailability(healthRepo.getAvailability())
        onGranted(healthRepo.getGrantedPermissions())
        val summary = if (setting.enabled || forceRefresh) {
            healthRepo.readDailySummary(
                setting = setting.copy(enabled = true),
                forceRefresh = forceRefresh,
            )
        } else {
            null
        }
        onSummary(summary)
    } finally {
        onLoading(false)
    }
}
