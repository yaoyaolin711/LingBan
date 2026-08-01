package me.rerere.rikkahub.data.companion.policy

import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.companion.model.InteractionSuggestion
import me.rerere.rikkahub.data.device.ProactiveChatReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionProactivePolicyTest {

    @Test
    fun usageCare_messageOnly_returnsSoftNotify() {
        val action = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.WARM,
            actionLevel = CompanionActionLevel.MESSAGE_ONLY,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 35,
        )
        assertTrue(action is ProactiveAction.SoftNotify)
        val notify = action as ProactiveAction.SoftNotify
        assertTrue(notify.title.contains("抖音"))
        assertTrue(notify.content.contains("35"))
    }

    @Test
    fun usageCare_softTools_mild_returnsSoftNotify() {
        val action = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.CALM,
            actionLevel = CompanionActionLevel.SOFT_TOOLS,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 35,
        )
        assertTrue(action is ProactiveAction.SoftNotify)
    }

    @Test
    fun usageCare_softTools_concerned_returnsOpenSolace() {
        val action = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.CONCERNED,
            actionLevel = CompanionActionLevel.SOFT_TOOLS,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 35,
            hourOfDay = 14,
        )
        assertTrue(action is ProactiveAction.OpenSolace)
        val open = action as ProactiveAction.OpenSolace
        assertTrue(open.isUsageCare)
        assertTrue(open.useFullScreenIntent)
    }

    @Test
    fun usageCare_softTools_longSession_returnsOpenSolace() {
        val action = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.WARM,
            actionLevel = CompanionActionLevel.SOFT_TOOLS,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 70,
            hourOfDay = 15,
        )
        assertTrue(action is ProactiveAction.OpenSolace)
    }

    @Test
    fun usageCare_testMode_severeAt2Minutes() {
        val action = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.WARM,
            actionLevel = CompanionActionLevel.SOFT_TOOLS,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 2,
            hourOfDay = 15,
            severeContinuousMinutes = 2,
        )
        assertTrue(action is ProactiveAction.OpenSolace)
    }

    @Test
    fun usageCare_quietHours_prefersSoftNotify_unlessConcerned() {
        val mild = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.WARM,
            actionLevel = CompanionActionLevel.SOFT_TOOLS,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 70,
            hourOfDay = 3,
        )
        assertTrue(mild is ProactiveAction.SoftNotify)

        val concerned = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.CONCERNED,
            actionLevel = CompanionActionLevel.SOFT_TOOLS,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 70,
            hourOfDay = 3,
        )
        assertTrue(concerned is ProactiveAction.OpenSolace)
        assertFalse((concerned as ProactiveAction.OpenSolace).useFullScreenIntent)
    }

    @Test
    fun silenceConcerned_withSoftTools_returnsSoftNotify() {
        val action = CompanionProactivePolicy.decideProactiveChat(
            reason = ProactiveChatReason.SILENCE,
            emotion = CompanionEmotionState.CONCERNED,
            actionLevel = CompanionActionLevel.SOFT_TOOLS,
        )
        assertTrue(action is ProactiveAction.SoftNotify)
    }

    @Test
    fun silenceConcerned_messageOnly_returnsOpenSolace() {
        val action = CompanionProactivePolicy.decideProactiveChat(
            reason = ProactiveChatReason.SILENCE,
            emotion = CompanionEmotionState.CONCERNED,
            actionLevel = CompanionActionLevel.MESSAGE_ONLY,
            hourOfDay = 14,
        )
        assertTrue(action is ProactiveAction.OpenSolace)
    }

    @Test
    fun evening_softTools_returnsSoftNotify() {
        val action = CompanionProactivePolicy.decideProactiveChat(
            reason = ProactiveChatReason.EVENING,
            emotion = CompanionEmotionState.WARM,
            actionLevel = CompanionActionLevel.SOFT_TOOLS,
        )
        assertTrue(action is ProactiveAction.SoftNotify)
    }

    @Test
    fun morning_returnsOpenSolace() {
        val action = CompanionProactivePolicy.decideProactiveChat(
            reason = ProactiveChatReason.MORNING,
            emotion = CompanionEmotionState.PLAYFUL,
            actionLevel = CompanionActionLevel.MESSAGE_ONLY,
        )
        assertTrue(action is ProactiveAction.OpenSolace)
        val open = action as ProactiveAction.OpenSolace
        assertTrue(open.reason == ProactiveChatReason.MORNING)
        assertFalse(open.useFullScreenIntent)
    }

    @Test
    fun silence_quietHours_returnsSoftNotify() {
        val action = CompanionProactivePolicy.decideProactiveChat(
            reason = ProactiveChatReason.SILENCE,
            emotion = CompanionEmotionState.CALM,
            actionLevel = CompanionActionLevel.MESSAGE_ONLY,
            hourOfDay = 2,
        )
        assertTrue(action is ProactiveAction.SoftNotify)
    }

    @Test
    fun usageCare_deviceTools_severe_returnsDeviceTask() {
        val action = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.CONCERNED,
            actionLevel = CompanionActionLevel.DEVICE_TOOLS,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 40,
            hourOfDay = 14,
        )
        assertTrue(action is ProactiveAction.DeviceTask)
        val device = action as ProactiveAction.DeviceTask
        assertTrue(device.goal == CompanionDeviceGoals.GO_HOME)
        assertTrue(device.followUp is ProactiveAction.OpenSolace)
    }

    @Test
    fun usageCare_deviceTools_quietHours_noDeviceTask() {
        val action = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.CONCERNED,
            actionLevel = CompanionActionLevel.DEVICE_TOOLS,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 70,
            hourOfDay = 2,
        )
        assertTrue(action is ProactiveAction.OpenSolace)
    }

    @Test
    fun usageCare_deviceTools_mild_stillSoftNotify() {
        val action = CompanionProactivePolicy.decideUsageCare(
            emotion = CompanionEmotionState.CALM,
            actionLevel = CompanionActionLevel.DEVICE_TOOLS,
            appName = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            continuousMinutes = 35,
            hourOfDay = 14,
        )
        assertTrue(action is ProactiveAction.SoftNotify)
    }

    @Test
    fun deviceGoals_whitelist() {
        assertTrue(CompanionDeviceGoals.isAllowed("回到桌面"))
        assertFalse(CompanionDeviceGoals.isAllowed("打开支付宝"))
        assertFalse(CompanionDeviceGoals.isAllowed("点击支付"))
    }

    @Test
    fun decideFromSuggestion_emotionMiss_softNotify() {
        val suggestion = InteractionSuggestion(
            type = "emotion_miss",
            priority = 0.7f,
            messageContext = "想念",
            reason = "emotion_warm_miss",
            createdAtEpochMillis = 0L,
        )
        val action = CompanionProactivePolicy.decideFromSuggestion(
            suggestion = suggestion,
            emotion = CompanionEmotionState.WARM,
            actionLevel = CompanionActionLevel.SOFT_TOOLS,
            hourOfDay = 14,
        )
        assertTrue(action is ProactiveAction.SoftNotify)
        assertEquals(ProactiveChatReason.CHECK_IN, CompanionProactivePolicy.reasonFromSuggestion(suggestion))
    }

    @Test
    fun quietHours_crossMidnight() {
        assertTrue(CompanionProactivePolicy.isQuietHours(23, startHour = 22, endHour = 6))
        assertTrue(CompanionProactivePolicy.isQuietHours(3, startHour = 22, endHour = 6))
        assertFalse(CompanionProactivePolicy.isQuietHours(12, startHour = 22, endHour = 6))
    }
}
