package me.rerere.rikkahub.ui.pages.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Briefcase01
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.ContactBook
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.MessageUser01
import me.rerere.hugeicons.stroke.Note01
import me.rerere.hugeicons.stroke.User02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.GlassCard
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.SolaceTheme
import org.koin.compose.koinInject

@Composable
fun UserProfileMemoryPage() {
    val settingsStore: SettingsStore = koinInject()
    val settings = LocalSettings.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = SolaceTheme.colorScheme

    var draft by remember(settings.globalUserProfile) {
        mutableStateOf(settings.globalUserProfile)
    }
    val dirty = draft != settings.globalUserProfile
    val savedMessage = stringResource(R.string.user_profile_memory_saved)

    fun save() {
        scope.launch {
            settingsStore.update { it.copy(globalUserProfile = draft) }
            toaster.show(savedMessage)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.user_profile_memory_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 20.dp,
            ) {
                RowWithIcon(
                    icon = HugeIcons.ContactBook,
                    title = stringResource(R.string.user_profile_memory_title),
                    subtitle = stringResource(R.string.user_profile_memory_intro),
                )
            }

            ProfileField(
                label = stringResource(R.string.user_profile_memory_name),
                value = draft.displayName,
                onValueChange = { draft = draft.copy(displayName = it) },
                placeholder = stringResource(R.string.user_profile_memory_name_hint),
                icon = HugeIcons.User02,
            )
            ProfileField(
                label = stringResource(R.string.user_profile_memory_birthday),
                value = draft.birthday,
                onValueChange = { draft = draft.copy(birthday = it) },
                placeholder = stringResource(R.string.user_profile_memory_birthday_hint),
                icon = HugeIcons.Calendar03,
            )
            ProfileField(
                label = stringResource(R.string.user_profile_memory_occupation),
                value = draft.occupation,
                onValueChange = { draft = draft.copy(occupation = it) },
                placeholder = stringResource(R.string.user_profile_memory_occupation_hint),
                icon = HugeIcons.Briefcase01,
            )
            ProfileField(
                label = stringResource(R.string.user_profile_memory_personality),
                value = draft.personality,
                onValueChange = { draft = draft.copy(personality = it) },
                placeholder = stringResource(R.string.user_profile_memory_personality_hint),
                icon = HugeIcons.Brain02,
                minLines = 2,
            )
            ProfileField(
                label = stringResource(R.string.user_profile_memory_addressing),
                value = draft.preferredAddressing,
                onValueChange = { draft = draft.copy(preferredAddressing = it) },
                placeholder = stringResource(R.string.user_profile_memory_addressing_hint),
                icon = HugeIcons.MessageUser01,
            )
            ProfileField(
                label = stringResource(R.string.user_profile_memory_locale),
                value = draft.locale,
                onValueChange = { draft = draft.copy(locale = it) },
                placeholder = stringResource(R.string.user_profile_memory_locale_hint),
                icon = HugeIcons.LanguageCircle,
            )
            ProfileField(
                label = stringResource(R.string.user_profile_memory_extra),
                value = draft.extraNotes,
                onValueChange = { draft = draft.copy(extraNotes = it) },
                placeholder = stringResource(R.string.user_profile_memory_extra_hint),
                icon = HugeIcons.Note01,
                minLines = 3,
            )

            Button(
                onClick = ::save,
                enabled = dirty,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.user_profile_memory_save))
            }

            Text(
                text = stringResource(R.string.user_profile_memory_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowWithIcon(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            Text(
                text = subtitle,
                style = typography.bodySmall,
                color = colors.secondaryText,
            )
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    minLines: Int = 1,
) {
    FormItem(
        label = {
            Text(label, fontWeight = FontWeight.Medium)
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            minLines = minLines,
            maxLines = if (minLines > 1) minLines + 2 else 1,
        )
    }
}
