package com.agent.chat.data.ai.tool.impl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.objectSchema
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class LocationTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val name = "get_location"
    override val description = "获取设备最近一次已知的粗略位置（经纬度）。缺定位权限时返回授权引导。不要向用户暴露坐标细节，可转成口语关心。"
    override val parametersSchema: Map<String, Any> = objectSchema(properties = emptyMap())

    @SuppressLint("MissingPermission")
    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) {
                return@withContext ToolResult(
                    false,
                    "缺少定位权限。请在系统设置中为本应用开启位置权限后再试。",
                )
            }
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            var best: Location? = null
            for (provider in providers) {
                if (!lm.isProviderEnabled(provider) && provider != LocationManager.PASSIVE_PROVIDER) {
                    continue
                }
                val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
                if (best == null || (loc.time > best.time)) {
                    best = loc
                }
            }
            if (best == null) {
                return@withContext ToolResult(false, "暂时拿不到位置，可能是定位服务未开启")
            }
            val ageMin = ((System.currentTimeMillis() - best.time) / 60_000L).coerceAtLeast(0)
            ToolResult(
                true,
                "已获取位置",
                JSONObject()
                    .put("latitude", best.latitude)
                    .put("longitude", best.longitude)
                    .put("accuracy_m", best.accuracy)
                    .put("age_minutes", ageMin)
                    .put("note", if (ageMin > 30) "这是较旧的缓存位置，可能不准确" else "较新"),
            )
        }

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
