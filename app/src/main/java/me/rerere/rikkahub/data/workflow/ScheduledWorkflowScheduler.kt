package me.rerere.rikkahub.data.workflow

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.ChatService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScheduledWorkflowScheduler(
    private val context: Context,
) {
    companion object {
        const val UNIQUE_WORK_NAME = "scheduled_workflow_dispatcher"
        const val POLL_INTERVAL_MINUTES = 15L
    }

    private val workManager by lazy { WorkManager.getInstance(context) }

    fun sync(hasEnabledRules: Boolean) {
        if (!hasEnabledRules) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ScheduledWorkflowWorker>(
            POLL_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

class ScheduledWorkflowWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val chatService: ChatService by inject()

    override suspend fun doWork(): Result {
        val settings = settingsStore.settingsFlow.value
        val dueRules = ScheduledWorkflowRules.resolveDueRules(
            rules = settings.scheduledWorkflows.map { it.normalized() },
            now = ZonedDateTime.now(ZoneId.systemDefault()),
        )
        dueRules.forEach { due ->
            val triggerKey = due.triggerKey
            val normalized = due.rule.normalized()
            val activeAssistants = normalized.activeAssistantIds()
            for (assistantId in activeAssistants) {
                chatService.runScheduledWorkflow(
                    ruleId = normalized.id,
                    workflowId = normalized.workflowId,
                    assistantId = assistantId,
                    ruleName = normalized.name,
                    triggerKey = triggerKey,
                )
            }
            settingsStore.update { current ->
                current.copy(
                    scheduledWorkflows = current.scheduledWorkflows.map { rule ->
                        if (rule.id == normalized.id) {
                            rule.copy(
                                lastTriggeredAtEpochMs = due.triggerAt.toInstant().toEpochMilli(),
                                lastTriggerKey = triggerKey,
                            )
                        } else {
                            rule
                        }
                    }
                )
            }
        }
        return Result.success()
    }
}
