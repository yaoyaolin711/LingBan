package com.agent.chat

import android.app.Application
import com.agent.chat.data.proactive.ProactiveNudgeWorker
import com.agent.chat.data.settings.ChatSettingsStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AgentChatApp : Application() {

    @Inject
    lateinit var chatSettingsStore: ChatSettingsStore

    override fun onCreate() {
        super.onCreate()
        ProactiveNudgeWorker.schedule(this, chatSettingsStore.get().proactiveEnabled)
    }
}
