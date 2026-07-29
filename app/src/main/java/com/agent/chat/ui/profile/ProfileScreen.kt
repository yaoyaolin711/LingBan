package com.agent.chat.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.agent.chat.BuildConfig
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.AppThemeMode
import java.io.File

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onModelApiClick: () -> Unit,
    onMemoryClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPrivacy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    val colors = AgentThemeColors

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.setAvatarFromUri(uri)
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = "个人中心",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            ProfileHeader(
                name = uiState.userName,
                avatarPath = uiState.userAvatarPath,
                membership = uiState.membershipLabel,
                onEditClick = { showEditProfile = true },
            )

            Spacer(modifier = Modifier.height(28.dp))

            SettingsGroup {
                ProfileSettingsItem(
                    icon = Icons.Outlined.SmartToy,
                    iconTint = Color(0xFF5B8DEF),
                    title = "模型与 API",
                    description = when {
                        uiState.modelName != null && uiState.providerName != null ->
                            "${uiState.providerName} · ${uiState.modelName}"
                        uiState.modelName != null -> "当前：${uiState.modelName}"
                        else -> "配置密钥、接口地址与对话模型"
                    },
                    onClick = onModelApiClick,
                    showDivider = false,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsGroup {
                ProfileSettingsItem(
                    icon = Icons.Outlined.Memory,
                    iconTint = Color(0xFFFF9F0A),
                    title = "记忆管理",
                    description = "查看灵伴了解你的方式",
                    onClick = onMemoryClick,
                    showDivider = true,
                )
                ProfileSettingsItem(
                    icon = Icons.Outlined.Lock,
                    iconTint = Color(0xFF8E8E93),
                    title = "隐私设置",
                    description = "数据本地保存，由你掌控",
                    onClick = { showPrivacy = true },
                    showDivider = false,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsGroup {
                ProfileSettingsItem(
                    icon = Icons.Outlined.Palette,
                    iconTint = Color(0xFFAF52DE),
                    title = "主题",
                    description = if (uiState.themeMode == AppThemeMode.Dark) {
                        "深色模式"
                    } else {
                        "浅色模式"
                    },
                    onClick = null,
                    showDivider = true,
                    trailing = {
                        ThemeModeSegment(
                            mode = uiState.themeMode,
                            onSelect = viewModel::setThemeMode,
                        )
                    },
                )
                ProfileSettingsItem(
                    icon = if (uiState.themeMode == AppThemeMode.Dark) {
                        Icons.Outlined.DarkMode
                    } else {
                        Icons.Outlined.LightMode
                    },
                    iconTint = Color(0xFF64D2FF),
                    title = "外观切换",
                    description = "颜色将平滑过渡到所选主题",
                    onClick = {
                        viewModel.setThemeMode(
                            if (uiState.themeMode == AppThemeMode.Dark) {
                                AppThemeMode.Light
                            } else {
                                AppThemeMode.Dark
                            },
                        )
                    },
                    showDivider = false,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsGroup {
                ProfileSettingsItem(
                    icon = Icons.Outlined.Info,
                    iconTint = Color(0xFFE8823A),
                    title = "关于灵伴",
                    description = "版本 ${BuildConfig.VERSION_NAME}",
                    onClick = { showAbout = true },
                    showDivider = false,
                )
            }
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            initialName = uiState.userName.takeIf { it != "未命名用户" }.orEmpty(),
            avatarPath = uiState.userAvatarPath,
            onDismiss = { showEditProfile = false },
            onSaveNickname = { name ->
                viewModel.setNickname(name)
                showEditProfile = false
            },
            onPickAvatar = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onClearAvatar = viewModel::clearAvatar,
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            containerColor = colors.surface,
            title = {
                Text("隐私设置", color = colors.textPrimary, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "对话、记忆与 API Key 均保存在本机。",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "· API Key 使用系统加密存储\n" +
                            "· 记忆可随时删除或禁止 AI 使用\n" +
                            "· 不会上传你的聊天内容到灵伴服务器（应用本身无云端账号）",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) {
                    Text("知道了", color = colors.accent)
                }
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = colors.surface,
            title = {
                Text("关于灵伴", color = colors.textPrimary, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "灵伴 Agent Chat",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "一个拥有长期记忆与人格化交互的 AI 伙伴。在就好。",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("关闭", color = colors.accent)
                }
            },
        )
    }
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    avatarPath: String,
    onDismiss: () -> Unit,
    onSaveNickname: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
) {
    val colors = AgentThemeColors
    var nickname by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("编辑资料", color = colors.textPrimary, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    ProfileAvatar(
                        name = nickname.ifBlank { "灵" },
                        avatarPath = avatarPath,
                        size = 88.dp,
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                            .clickable(onClick = onPickAvatar),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = "更换头像",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (avatarPath.isNotBlank()) {
                    TextButton(onClick = onClearAvatar) {
                        Text("恢复默认头像", color = colors.textSecondary)
                    }
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(24) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("昵称") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveNickname(nickname.trim()) }) {
                Text("保存", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun ProfileHeader(
    name: String,
    avatarPath: String,
    membership: String,
    onEditClick: () -> Unit,
) {
    val colors = AgentThemeColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onEditClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            ProfileAvatar(
                name = name,
                avatarPath = avatarPath,
                size = 88.dp,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = "编辑资料",
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "点击编辑头像与昵称",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceSelected)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = membership,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun ProfileAvatar(
    name: String,
    avatarPath: String,
    size: androidx.compose.ui.unit.Dp,
) {
    val colors = AgentThemeColors
    val file = remember(avatarPath) {
        avatarPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceMuted)
            .border(1.dp, colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.take(1).ifBlank { "灵" },
                style = MaterialTheme.typography.headlineMedium,
                color = colors.accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    content: @Composable () -> Unit,
) {
    val colors = AgentThemeColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

@Composable
private fun ProfileSettingsItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    onClick: (() -> Unit)?,
    showDivider: Boolean,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AgentThemeColors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.55f),
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp)
                    .height(1.dp)
                    .background(colors.outline.copy(alpha = 0.65f)),
            )
        }
    }
}

@Composable
private fun ThemeModeSegment(
    mode: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
) {
    val colors = AgentThemeColors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceMuted)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ThemeChip(
            selected = mode == AppThemeMode.Light,
            label = "浅色",
            onClick = { onSelect(AppThemeMode.Light) },
        )
        ThemeChip(
            selected = mode == AppThemeMode.Dark,
            label = "深色",
            onClick = { onSelect(AppThemeMode.Dark) },
        )
    }
}

@Composable
private fun ThemeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val colors = AgentThemeColors
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.textPrimary else colors.textSecondary,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
