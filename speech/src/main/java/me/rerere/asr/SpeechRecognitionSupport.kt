package me.rerere.asr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 探测设备上可用的系统语音识别（RecognitionService）。
 *
 * 国内多数无 GMS 机型上 [SpeechRecognizer.isRecognitionAvailable] 会返回 false，
 * 或返回 true 但实际启动时报 ERROR_INSUFFICIENT_PERMISSIONS（与麦克风权限无关）。
 */
object SpeechRecognitionSupport {
    private const val TAG = "SpeechRecognition"

    /** 面向用户的不可用说明（中文）。 */
    const val UNAVAILABLE_MESSAGE =
        "本机没有可用的系统语音识别引擎。常见于未安装 Google 语音服务的国产机。" +
            "请到「个人中心 → 语音」改用硅基流动等云端 ASR，或安装 Google / 系统语音识别组件。"

    /** 有麦克风权限但仍被系统引擎拒绝时的说明。 */
    const val ENGINE_PERMISSION_MESSAGE =
        "系统语音识别引擎拒绝访问（通常不是麦克风权限问题，而是设备未提供可用的识别服务）。" +
            "请到「个人中心 → 语音」改用硅基流动等云端 ASR，或安装 Google 语音识别。"

    fun isAvailable(context: Context): Boolean {
        if (SpeechRecognizer.isRecognitionAvailable(context)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            return true
        }
        return listRecognitionServices(context).isNotEmpty()
    }

    fun listRecognitionServices(context: Context): List<ResolveInfo> {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            context.packageManager.queryIntentServices(intent, flags)
        } catch (e: Exception) {
            Log.w(TAG, "query RecognitionService failed", e)
            emptyList()
        }
    }

    fun serviceComponentNames(context: Context): List<ComponentName> {
        return listRecognitionServices(context).mapNotNull { info ->
            val serviceInfo = info.serviceInfo ?: return@mapNotNull null
            ComponentName(serviceInfo.packageName, serviceInfo.name)
        }
    }

    fun describeAvailability(context: Context): String {
        val services = listRecognitionServices(context)
        val online = SpeechRecognizer.isRecognitionAvailable(context)
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        return buildString {
            append("online=$online onDevice=$onDevice services=${services.size}")
            services.forEach { info ->
                val si = info.serviceInfo ?: return@forEach
                append(" [").append(si.packageName).append('/').append(si.name).append(']')
            }
        }
    }

    fun isHardFailure(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return message.contains("不可用") ||
            message.contains("not available", ignoreCase = true) ||
            message.contains("拒绝访问") ||
            message.contains("Microphone permission", ignoreCase = true) ||
            message.contains("麦克风权限") ||
            message.contains("改用硅基流动")
    }
}
