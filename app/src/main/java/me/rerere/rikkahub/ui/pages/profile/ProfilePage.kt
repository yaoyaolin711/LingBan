package me.rerere.rikkahub.ui.pages.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.ImageUpload
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus

@Composable
fun ProfilePage() {
    val nav = LocalNavController.current
    val settings = LocalSettings.current
    val scheme = MaterialTheme.colorScheme
    val assistantName = settings.getCurrentAssistant().name.ifBlank { "Solace" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.profile_page_title),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.profile_page_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = scheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("companion") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.profile_group_companion)) },
                ) {
                    item(
                        onClick = { nav.navigate(Screen.Assistant) },
                        leadingContent = { Icon(HugeIcons.LookTop, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_assistant)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_assistant_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Extensions) },
                        leadingContent = { Icon(HugeIcons.Package, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_extensions)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_extensions_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Favorite) },
                        leadingContent = { Icon(HugeIcons.Favourite, null) },
                        headlineContent = { Text(stringResource(R.string.favorite_page_title)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Translator) },
                        leadingContent = { Icon(HugeIcons.LanguageCircle, null) },
                        headlineContent = { Text(stringResource(R.string.translator_page_title)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.ImageGen) },
                        leadingContent = { Icon(HugeIcons.Image03, null) },
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
                        leadingContent = { Icon(HugeIcons.AiMagic, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_default_model)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingProvider) },
                        leadingContent = { Icon(HugeIcons.Brain02, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_providers)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingSearch) },
                        leadingContent = { Icon(HugeIcons.GlobalSearch, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_search_service)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_search_service_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingSpeech) },
                        leadingContent = { Icon(HugeIcons.Megaphone01, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_tts_service)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingMcp) },
                        leadingContent = { Icon(HugeIcons.McpServer, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_mcp)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_mcp_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingWeb) },
                        leadingContent = { Icon(HugeIcons.ServerStack01, null) },
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
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_data_backup)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_data_backup_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingFiles) },
                        leadingContent = { Icon(HugeIcons.ImageUpload, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_chat_storage)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Stats) },
                        leadingContent = { Icon(HugeIcons.ChartColumn, null) },
                        headlineContent = { Text(stringResource(R.string.stats_page_title)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Log) },
                        leadingContent = { Icon(HugeIcons.Bookshelf01, null) },
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
                        leadingContent = { Icon(HugeIcons.Settings03, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_preferences)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_preferences_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingTheme) },
                        leadingContent = { Icon(HugeIcons.Sun01, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_theme_setting)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_theme_setting_desc)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.Setting) },
                        leadingContent = { Icon(HugeIcons.Share04, null) },
                        headlineContent = { Text(stringResource(R.string.settings)) },
                        supportingContent = { Text(stringResource(R.string.profile_page_subtitle)) },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingAbout) },
                        leadingContent = { Icon(HugeIcons.Clapping01, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_about)) },
                        supportingContent = { Text("Solace · based on RikkaHub") },
                    )
                    item(
                        onClick = { nav.navigate(Screen.SettingDonate) },
                        leadingContent = { Icon(HugeIcons.Book01, null) },
                        headlineContent = { Text(stringResource(R.string.setting_page_donate)) },
                    )
                }
            }

            item("assistantHint") {
                Text(
                    text = assistantName,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
