package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ocr.LanOcrClient
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.AiEditing
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.writeClipboardText
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun SettingModelPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_model_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = CustomColors.cardColorsOnSurfaceContainer.containerColor
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(HugeIcons.AiBrain01, null) },
                    label = { Text(stringResource(R.string.setting_model_page_tab_model)) }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(HugeIcons.AiEditing, null) },
                    label = { Text(stringResource(R.string.setting_model_page_tab_prompt)) }
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> ModelSettingsPage(settings = settings, vm = vm, contentPadding = contentPadding)
                1 -> PromptSettingsPage(settings = settings, vm = vm, contentPadding = contentPadding)
            }
        }
    }
}

@Composable
private fun ModelSettingsPage(settings: Settings, vm: SettingVM, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_chat_model),
                description = stringResource(R.string.setting_model_page_chat_model_desc),
                modelId = settings.chatModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(chatModelId = it.id)) },
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_fast_model),
                description = stringResource(R.string.setting_model_page_fast_model_desc),
                modelId = settings.fastModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(fastModelId = it.id)) },
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_title_model),
                description = stringResource(R.string.setting_model_page_title_model_desc),
                modelId = settings.titleModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(titleModelId = it.id)) },
                onClear = { vm.updateSettings(settings.copy(titleModelId = null)) },
            )
        }
        item {
            SuggestionModelSettingItem(
                settings = settings,
                vm = vm,
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_translate_model),
                description = stringResource(R.string.setting_model_page_translate_model_desc),
                modelId = settings.translateModeId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(translateModeId = it.id)) },
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_ocr_model),
                description = stringResource(R.string.setting_model_page_ocr_model_desc),
                modelId = settings.ocrModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(ocrModelId = it.id)) },
            )
        }
        item {
            OcrLanServiceSettingItem(
                settings = settings,
                vm = vm,
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_compress_model),
                description = stringResource(R.string.setting_model_page_compress_model_desc),
                modelId = settings.compressModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings(settings.copy(compressModelId = it.id)) },
            )
        }
    }
}

@Composable
private fun OcrLanServiceSettingItem(
    settings: Settings,
    vm: SettingVM,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var isTesting by remember { mutableStateOf(false) }
    var lastDiagnostics by remember { mutableStateOf("") }

    val tutorialUrl = "https://github.com/re-ovo/rikkahub/blob/main/docs/ocr/lan-paddleocr.md"
    val baseUrl = settings.ocrLanServiceUrl
    val isValidBaseUrl = baseUrl.isBlank() || baseUrl.toHttpUrlOrNull() != null

    CardGroup(title = { Text(stringResource(R.string.setting_model_page_ocr_lan_service)) }) {
        item(
            headlineContent = { Text(stringResource(R.string.setting_model_page_ocr_lan_enable)) },
            supportingContent = { Text(stringResource(R.string.setting_model_page_ocr_lan_enable_desc)) },
            trailingContent = {
                Switch(
                    checked = settings.ocrUseLanService,
                    onCheckedChange = {
                        vm.updateSettings(settings.copy(ocrUseLanService = it))
                    }
                )
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.setting_model_page_ocr_lan_url)) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setting_model_page_ocr_lan_url_desc))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            vm.updateSettings(settings.copy(ocrLanServiceUrl = it.trim()))
                        },
                        singleLine = true,
                        isError = !isValidBaseUrl,
                        enabled = settings.ocrUseLanService,
                    )
                }
            }
        )
        item(
            headlineContent = { Text(stringResource(R.string.setting_model_page_ocr_lan_fallback)) },
            supportingContent = { Text(stringResource(R.string.setting_model_page_ocr_lan_fallback_desc)) },
            trailingContent = {
                Switch(
                    checked = settings.ocrLanFallbackToModel,
                    onCheckedChange = {
                        vm.updateSettings(settings.copy(ocrLanFallbackToModel = it))
                    },
                    enabled = settings.ocrUseLanService,
                )
            }
        )
        item(
            headlineContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!settings.ocrUseLanService) {
                                toaster.show(
                                    message = context.getString(R.string.setting_model_page_ocr_lan_test_enable_first),
                                    type = ToastType.Warning
                                )
                                return@Button
                            }
                            if (baseUrl.isBlank() || !isValidBaseUrl) {
                                toaster.show(
                                    message = context.getString(R.string.setting_model_page_ocr_lan_test_invalid_url),
                                    type = ToastType.Error
                                )
                                return@Button
                            }
                            scope.launch {
                                isTesting = true
                                val result = LanOcrClient.healthCheck(baseUrl)
                                isTesting = false
                                if (result.isSuccess) {
                                    lastDiagnostics = ""
                                    toaster.show(
                                        message = context.getString(R.string.setting_model_page_ocr_lan_test_success),
                                        type = ToastType.Success
                                    )
                                } else {
                                    val error = result.exceptionOrNull()
                                    val errorType = LanOcrClient.classifyHealthError(error)
                                    val hint = LanOcrClient.healthHint(errorType)
                                    lastDiagnostics = LanOcrClient.buildDiagnostics(baseUrl, error)
                                    toaster.show(
                                        message = context.getString(
                                            R.string.setting_model_page_ocr_lan_test_failed,
                                            hint
                                        ),
                                        type = ToastType.Error
                                    )
                                }
                            }
                        },
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.setting_model_page_ocr_lan_test_button))
                        }
                    }
                    Button(onClick = { context.openUrl(tutorialUrl) }) {
                        Text(stringResource(R.string.setting_model_page_ocr_lan_guide_button))
                    }
                    Button(
                        onClick = {
                            context.writeClipboardText(lastDiagnostics)
                            toaster.show(
                                message = context.getString(R.string.setting_model_page_ocr_lan_copy_diagnostics_done),
                                type = ToastType.Success
                            )
                        },
                        enabled = lastDiagnostics.isNotBlank() && !isTesting
                    ) {
                        Text(stringResource(R.string.setting_model_page_ocr_lan_copy_diagnostics))
                    }
                }
            },
            supportingContent = { Text(stringResource(R.string.setting_model_page_ocr_lan_guide_desc)) },
        )
    }
}

@Composable
private fun SuggestionModelSettingItem(
    settings: Settings,
    vm: SettingVM,
) {
    val title = stringResource(R.string.setting_model_page_suggestion_model)
    val state = rememberModelListState(
        modelId = settings.suggestionModelId,
        providers = settings.providers,
        type = ModelType.CHAT,
    )

    Column {
        CardGroup(title = { Text(title) }) {
            item(
                headlineContent = { Text(stringResource(R.string.setting_model_page_enable_suggestion)) },
                trailingContent = {
                    Switch(
                        checked = settings.enableSuggestion,
                        onCheckedChange = {
                            vm.updateSettings(settings.copy(enableSuggestion = it))
                        }
                    )
                },
            )
            if (settings.enableSuggestion) {
                item(
                    onClick = { state.open() },
                    headlineContent = { Text(title) },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = state.currentModel?.displayName
                                    ?: stringResource(R.string.model_list_select_model),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.currentModel != null) {
                                IconButton(
                                    onClick = { vm.updateSettings(settings.copy(suggestionModelId = null)) },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            } else {
                                Icon(
                                    HugeIcons.ArrowRight01,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
        Text(
            text = stringResource(R.string.setting_model_page_suggestion_model_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }

    ModelListSheet(state = state, onSelect = { vm.updateSettings(settings.copy(suggestionModelId = it.id)) })
}

@Composable
private fun ModelSettingItem(
    title: String,
    description: String,
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    onSelect: (Model) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val state = rememberModelListState(
        modelId = modelId,
        providers = providers,
        type = ModelType.CHAT,
    )

    Column {
        CardGroup(title = { Text(title) }) {
            item(
                onClick = { state.open() },
                headlineContent = { Text(title) },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = state.currentModel?.displayName
                                ?: stringResource(R.string.model_list_select_model),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (onClear != null && state.currentModel != null) {
                            IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                                Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        } else {
                            Icon(
                                HugeIcons.ArrowRight01,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }

    ModelListSheet(state = state, onSelect = onSelect)
}
