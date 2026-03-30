package com.msst.toolbox.platform

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.msst.toolbox.ToolboxApplication
import com.msst.toolbox.model.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidDeviceInfoProvider(private val context: Context) : DeviceInfoProvider {

    private fun readCpuModel(): String {
        // 1. Build.SOC_MODEL (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val soc = Build.SOC_MODEL
            if (soc.isNotEmpty() && soc != Build.UNKNOWN) return soc
        }
        // 2. SystemProperties reflection
        try {
            val sp = Class.forName("android.os.SystemProperties")
            val get = sp.getMethod("get", String::class.java, String::class.java)
            for (key in listOf("ro.soc.model", "ro.hardware.chipname", "ro.board.platform")) {
                val v = get.invoke(null, key, "") as String
                if (v.isNotEmpty()) return v
            }
        } catch (_: Exception) {}
        // 3. /proc/cpuinfo Hardware / model name fields
        return try {
            val lines = File("/proc/cpuinfo").readLines()
            lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
                ?.split(":")?.getOrElse(1) { "" }?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals("qcom", ignoreCase = true) }
                ?: lines.firstOrNull { it.startsWith("model name", ignoreCase = true) }
                    ?.split(":")?.getOrElse(1) { "" }?.trim()
                    ?.takeIf { it.isNotEmpty() }
                ?: lines.firstOrNull { it.startsWith("Processor", ignoreCase = true) }
                    ?.split(":")?.getOrElse(1) { "" }?.trim()
                    ?.takeIf { it.isNotEmpty() }
                ?: Build.HARDWARE
        } catch (_: Exception) {
            Build.HARDWARE
        }
    }

    private fun getScreenResolution(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = context.getSystemService(WindowManager::class.java)
                val bounds = wm.currentWindowMetrics.bounds
                "${bounds.width()}x${bounds.height()}"
            } else {
                @Suppress("DEPRECATION")
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                "${metrics.widthPixels}x${metrics.heightPixels}"
            }
        } catch (_: Exception) {
            "未知"
        }
    }

    override suspend fun getDeviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        DeviceInfo(
            brand = Build.BRAND.replaceFirstChar { it.uppercase() },
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            cpuModel = readCpuModel(),
            screenResolution = getScreenResolution()
        )
    }
}

actual fun createDeviceInfoProvider(): DeviceInfoProvider =
    AndroidDeviceInfoProvider(ToolboxApplication.instance)