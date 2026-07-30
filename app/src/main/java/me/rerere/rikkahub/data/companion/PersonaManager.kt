package me.rerere.rikkahub.data.companion

import me.rerere.rikkahub.data.companion.model.CompanionPersona
import me.rerere.rikkahub.data.datastore.Settings

class PersonaManager {
    fun getPersona(settings: Settings): CompanionPersona? {
        val nickname = settings.displaySetting.userNickname.trim()
        if (nickname.isBlank()) return null
        return CompanionPersona(
            displayName = nickname,
            description = "The user's preferred display name is $nickname.",
            title = "User Persona",
        )
    }
}
