package com.agent.chat.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.toolSettings
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    fun hasPermissions(vararg perms: String) = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val hasLocationPerm = hasPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val hasCalendarPerm = hasPermissions(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )
    val hasSmsPerm = hasPermissions(Manifest.permission.READ_SMS)

    fun onUpdate(transform: (com.agent.chat.data.settings.ToolSettings) -> com.agent.chat.data.settings.ToolSettings) {
        viewModel.updateToolSettings(transform)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI 能力授权", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "控制 AI 可以主动使用的能力",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- 基础能力 ---
            PermissionGroup(
                title = "基础能力",
                description = "常规工具，无需系统权限即可使用。开启后 AI 可在对话及主动关心中自动调用。",
            ) {
                PermSwitch("记忆读写", "跨对话记住你的偏好与关系", settings.memoryEnabled) {
                    onUpdate { it.copy(memoryEnabled = !it.memoryEnabled) }
                }
                PermSwitch("当前时间", "判断时段、是否该吃饭睡觉等", settings.timeEnabled) {
                    onUpdate { it.copy(timeEnabled = !it.timeEnabled) }
                }
                PermSwitch("电量", "低电量时主动关心你", settings.batteryEnabled) {
                    onUpdate { it.copy(batteryEnabled = !it.batteryEnabled) }
                }
                PermSwitch("设备信息", "了解你的设备型号等基础信息", settings.deviceEnabled) {
                    onUpdate { it.copy(deviceEnabled = !it.deviceEnabled) }
                }
                PermSwitch("闹钟", "帮你设置系统闹钟", settings.alarmEnabled) {
                    onUpdate { it.copy(alarmEnabled = !it.alarmEnabled) }
                }
            }

            // --- 感知能力 ---
            PermissionGroup(
                title = "感知能力",
                description = "让 AI 了解你的环境和习惯，主动发消息时会参考这些信息，像真人一样关心你。",
            ) {
                PermSwitch("屏幕状态", "感知亮屏/息屏/锁屏", settings.screenStateEnabled) {
                    onUpdate { it.copy(screenStateEnabled = !it.screenStateEnabled) }
                }
                PermSwitch("音乐控制", "查看/控制当前播放的音乐", settings.musicEnabled) {
                    val turningOn = !settings.musicEnabled
                    onUpdate { it.copy(musicEnabled = !it.musicEnabled) }
                    if (turningOn) {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                PermSwitch(
                    title = "通知感知",
                    subtitle = "感知你收到的通知（不含正文），需授予通知访问权限",
                    checked = settings.notificationEnabled,
                    systemPermGranted = null,
                ) {
                    val turningOn = !settings.notificationEnabled
                    onUpdate { it.copy(notificationEnabled = !it.notificationEnabled) }
                    if (turningOn) {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                PermSwitch(
                    title = "App 使用情况",
                    subtitle = "了解你最近用了哪些 App，需授予特殊权限",
                    checked = settings.appUsageEnabled,
                    systemPermGranted = null,
                ) {
                    val turningOn = !settings.appUsageEnabled
                    onUpdate { it.copy(appUsageEnabled = !it.appUsageEnabled) }
                    if (turningOn) {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                PermSwitch(
                    title = "屏幕内容感知",
                    subtitle = "读取当前屏幕文字内容，需开启无障碍服务",
                    checked = settings.screenContentEnabled,
                    systemPermGranted = null,
                ) {
                    val turningOn = !settings.screenContentEnabled
                    onUpdate { it.copy(screenContentEnabled = !it.screenContentEnabled) }
                    if (turningOn) {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }

            // --- 敏感权限 ---
            PermissionGroup(
                title = "敏感权限",
                description = "涉及隐私数据，默认关闭。开启后需授予对应的系统权限，AI 才能实际使用。",
            ) {
                PermSwitch(
                    title = "日历",
                    subtitle = "读写系统日程，可主动提醒你即将到来的安排",
                    checked = settings.calendarEnabled,
                    systemPermGranted = if (settings.calendarEnabled) hasCalendarPerm else null,
                ) {
                    val turningOn = !settings.calendarEnabled
                    onUpdate { it.copy(calendarEnabled = !it.calendarEnabled) }
                    if (turningOn && !hasCalendarPerm) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR,
                            ),
                        )
                    }
                }
                PermSwitch(
                    title = "定位",
                    subtitle = "获取你的大致位置，用于到家问候、天气提醒等",
                    checked = settings.locationEnabled,
                    systemPermGranted = if (settings.locationEnabled) hasLocationPerm else null,
                ) {
                    val turningOn = !settings.locationEnabled
                    onUpdate { it.copy(locationEnabled = !it.locationEnabled) }
                    if (turningOn && !hasLocationPerm) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                    }
                }
                PermSwitch(
                    title = "短信读取",
                    subtitle = "读取最近短信摘要（如验证码提醒等）",
                    checked = settings.smsEnabled,
                    systemPermGranted = if (settings.smsEnabled) hasSmsPerm else null,
                ) {
                    val turningOn = !settings.smsEnabled
                    onUpdate { it.copy(smsEnabled = !it.smsEnabled) }
                    if (turningOn && !hasSmsPerm) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS))
                    }
                }
            }

            TextButton(
                onClick = {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            ) {
                Text("查看系统应用权限详情")
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PermissionGroup(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun PermSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    systemPermGranted: Boolean? = null,
    onToggle: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
        if (checked && systemPermGranted == false) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "已开启但系统权限未授予，请在系统设置中授权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
