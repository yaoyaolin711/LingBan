package com.agent.chat.ui.home

import java.util.Calendar

object HomeGreeting {
    fun forHour(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
        val period = when (hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            in 18..21 -> "晚上好"
            else -> "夜深了"
        }
        val prompt = when (hour) {
            in 5..10 -> "今天想一起开启什么？"
            in 11..13 -> "要不要歇一会儿，聊两句？"
            in 14..17 -> "今天想一起完成什么？"
            in 18..21 -> "今天过得怎么样？"
            else -> "我在这里陪着你。"
        }
        return "$period，$prompt"
    }
}
