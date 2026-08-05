package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.groupchat.GroupChatMode
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatCreatePage() {
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val settingsStore: SettingsStore = koinInject()
    val chatService: ChatService = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf(setOf<Uuid>()) }
    var mode by remember { mutableStateOf(GroupChatMode.FREE_DISCUSSION) }
    var creating by remember { mutableStateOf(false) }
    val needTwo = stringResource(R.string.group_chat_need_two_members)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_chat_create_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.Cancel01, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.group_chat_create_pick_members),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(settings.assistants, key = { it.id }) { assistant ->
                    val checked = assistant.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - assistant.id else selected + assistant.id
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected = if (it) selected + assistant.id else selected - assistant.id
                            },
                        )
                        Text(
                            text = assistant.name.ifBlank {
                                stringResource(R.string.assistant_page_default_assistant)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.group_chat_mode_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == GroupChatMode.MENTION_FIRST,
                    onClick = { mode = GroupChatMode.MENTION_FIRST },
                    label = { Text(stringResource(R.string.group_chat_mode_mention_first)) },
                )
                FilterChip(
                    selected = mode == GroupChatMode.FREE_DISCUSSION,
                    onClick = { mode = GroupChatMode.FREE_DISCUSSION },
                    label = { Text(stringResource(R.string.group_chat_mode_free_discussion)) },
                )
            }

            Button(
                onClick = {
                    if (selected.size < 2) {
                        toaster.show(needTwo, type = ToastType.Warning)
                        return@Button
                    }
                    creating = true
                    scope.launch {
                        runCatching {
                            val id = chatService.createGroupConversation(
                                memberAssistantIds = selected.toList(),
                                mode = mode,
                            )
                            navigateToChatPage(navController, chatId = id)
                        }.onFailure {
                            toaster.show(it.message ?: "Failed", type = ToastType.Error)
                        }
                        creating = false
                    }
                },
                enabled = !creating && selected.size >= 2,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.group_chat_create_action))
            }
        }
    }
}
