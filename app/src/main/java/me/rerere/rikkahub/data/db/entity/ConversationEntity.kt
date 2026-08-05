package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("assistant_id", defaultValue = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
    val assistantId: String,
    @ColumnInfo("title")
    val title: String,
    @ColumnInfo("nodes")
    val nodes: String,
    @ColumnInfo("create_at")
    val createAt: Long,
    @ColumnInfo("update_at")
    val updateAt: Long,
    @ColumnInfo("suggestions", defaultValue = "[]")
    val chatSuggestions: String,
    @ColumnInfo("is_pinned", defaultValue = "0")
    val isPinned: Boolean,
    @ColumnInfo("custom_system_prompt", defaultValue = "")
    val customSystemPrompt: String = "",
    @ColumnInfo("mode_injection_ids", defaultValue = "[]")
    val modeInjectionIds: String = "[]",
    @ColumnInfo("lorebook_ids", defaultValue = "[]")
    val lorebookIds: String = "[]",
    @ColumnInfo("workspace_cwd", defaultValue = "")
    val workspaceCwd: String = "",
    @ColumnInfo("folder_id", defaultValue = "")
    val folderId: String = "",
    @ColumnInfo("rolling_summary", defaultValue = "")
    val rollingSummary: String = "",
    @ColumnInfo("rolling_summary_covered_count", defaultValue = "0")
    val rollingSummaryCoveredCount: Int = 0,
    @ColumnInfo("session_overview", defaultValue = "")
    val sessionOverview: String = "",
    @ColumnInfo("carryover_overview", defaultValue = "")
    val carryoverOverview: String = "",
    @ColumnInfo("is_group", defaultValue = "0")
    val isGroup: Boolean = false,
    @ColumnInfo("group_members", defaultValue = "[]")
    val groupMembers: String = "[]",
    @ColumnInfo("group_mode", defaultValue = "mention_first")
    val groupMode: String = "mention_first",
    @ColumnInfo("floor_state", defaultValue = "{}")
    val floorState: String = "{}",
)
