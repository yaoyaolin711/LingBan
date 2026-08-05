package me.rerere.rikkahub.ui.pages.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.AiSmartwatch
import me.rerere.hugeicons.stroke.Agreement01
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.HeartCheck
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.ImageUpload
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.GlassCard
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.SolaceTheme
import me.rerere.rikkahub.utils.plus

@Composable
fun ProfilePage() {
    val nav = LocalNavController.current
    val settings = LocalSettings.current
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val assistantName = settings.getCurrentAssistant().name.ifBlank { "Solace" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.profile_page_title),
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                        )
                        Text(
                            text = stringResource(R.string.profile_page_subtitle),
                            style = typography.bodySmall,
                            color = colors.secondaryText,
                        )
                    }
                },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("hero") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    contentPadding = 20.dp,
                ) {
                    Text(
                        text = stringResource(R.string.profile_page_hero_label),
                        style = typography.labelLarge,
                        color = colors.secondaryText,
                    )
                    Text(
                        text = assistantName,
                        style = typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = stringResource(R.string.profile_page_hero_hint),
                        style = typography.bodySmall,
                        color = colors.secondaryText,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item("companion") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.profile_group_companion)) },
                ) {
                    item(
                        onClick = { nav.navigate(Screen.Assistant) },
                        leadingContent = { Icon(HugeIcons.LookTop, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_assistant)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_assistant_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Extensions) },
                        leadingContent = { Icon(HugeIcons.Package, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_extensions)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_extensions_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Favorite) },
                        leadingContent = { Icon(HugeIcons.Favourite, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.favorite_page_title)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Translator) },
                        leadingContent = { Icon(HugeIcons.LanguageCircle, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.translator_page_title)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.ImageGen) },
                        leadingContent = { Icon(HugeIcons.Image03, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.imggen_page_title)) },
                    )
                }
            }

            item("models") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.profile_group_models)) },
                ) {
                    item(
                        onClick = { nav.navigate(Screen.SettingModels) },
                        leadingContent = { Icon(HugeIcons.AiMagic, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_default_model)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingProvider) },
                        leadingContent = { Icon(HugeIcons.Brain02, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_providers)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingSearch) },
                        leadingContent = { Icon(HugeIcons.GlobalSearch, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_search_service)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_search_service_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingSpeech) },
                        leadingContent = { Icon(HugeIcons.Megaphone01, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_tts_service)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingMcp) },
                        leadingContent = { Icon(HugeIcons.McpServer, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_mcp)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_mcp_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingWeb) },
                        leadingContent = { Icon(HugeIcons.ServerStack01, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_web_server)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_web_server_desc)) },
                    )
                }
            }

            item("data") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.profile_group_data)) },
                ) {
                    item(
                        onClick = { nav.navigate(Screen.Backup) },
                        leadingContent = { Icon(HugeIcons.Database02, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_data_backup)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_data_backup_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingFiles) },
                        leadingContent = { Icon(HugeIcons.ImageUpload, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_chat_storage)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingHealthConnect) },
                        leadingContent = { Icon(HugeIcons.AiSmartwatch, null, tint = colors.primary) },
                        headlineContent = { Text("穿戴设备 / Health Connect") },
                        supportingContent = { Text("只读同步步数、心率、睡眠给伴侣上下文") },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingIntimate) },
                        leadingContent = { Icon(HugeIcons.HeartCheck, null, tint = colors.primary) },
                        headlineContent = { Text("亲密互动") },
                        supportingContent = { Text("AI 伴侣场景扩展说明，包含规划方向、边界与收集建议") },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Stats) },
                        leadingContent = { Icon(HugeIcons.ChartColumn, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.stats_page_title)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Log) },
                        leadingContent = { Icon(HugeIcons.Bookshelf01, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_request_logs)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_request_logs_desc)) },
                    )
                }
            }

            item("more") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.profile_group_more)) },
                ) {
                    item(
                        onClick = { nav.navigate(Screen.SettingPreferences) },
                        leadingContent = { Icon(HugeIcons.Settings03, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingTheme) },
                        leadingContent = { Icon(HugeIcons.Sun01, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_theme_setting)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_theme_setting_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingAbout) },
                        leadingContent = { Icon(HugeIcons.Clapping01, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_about)) },
                        supportingContent = { Text("Solace · based on RikkaHub") },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingDonate) },
                        leadingContent = { Icon(HugeIcons.Book01, null, tint = colors.primary) },
                        headlineContent = { Text(stringResource(R.string.setting_page_donate)) },
                    )
                }
            }

            item("legal") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("法律信息") },
                ) {
                    item(
                        onClick = { nav.navigate(Screen.SettingDisclaimer) },
                        leadingContent = { Icon(HugeIcons.Agreement01, null, tint = colors.primary) },
                        headlineContent = { Text("免责声明") },
                        supportingContent = { Text("使用本应用即视为同意相关条款") },
                    )
                }
            }

            item("footer") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Solace",
                    style = typography.labelMedium,
                    color = colors.secondaryText.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
