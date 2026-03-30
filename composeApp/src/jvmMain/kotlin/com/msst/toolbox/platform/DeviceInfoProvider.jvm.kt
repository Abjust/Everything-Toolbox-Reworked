package com.msst.toolbox.platform

import com.msst.toolbox.model.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oshi.SystemInfo
import java.awt.Toolkit

class JvmDeviceInfoProvider : DeviceInfoProvider {

    private val si = SystemInfo()

    override suspend fun getDeviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        val cs = si.hardware.computerSystem
        val processor = si.hardware.processor

        val screenSize = try {
            val size = Toolkit.getDefaultToolkit().screenSize
            "${size.width}x${size.height}"
        } catch (_: Exception) {
            "未知"
        }

        val brand = cs.manufacturer
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            ?: System.getProperty("os.name", "Unknown")

        val model = cs.model
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            ?: "未知机型"

        val osName = System.getProperty("os.name", "Unknown")
        val osVersion = System.getProperty("os.version", "")
        val osArch = System.getProperty("os.arch", "")

        DeviceInfo(
            brand = brand,
            model = model,
            osVersion = "$osName $osVersion ($osArch)",
            cpuModel = processor.processorIdentifier.name.trim(),
            screenResolution = screenSize
        )
    }
}

actual fun createDeviceInfoProvider(): DeviceInfoProvider = JvmDeviceInfoProvider()