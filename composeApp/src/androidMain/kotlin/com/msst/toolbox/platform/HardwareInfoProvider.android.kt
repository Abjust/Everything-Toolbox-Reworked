package com.msst.toolbox.platform

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.StatFs
import android.util.DisplayMetrics
import com.msst.toolbox.ToolboxApplication
import com.msst.toolbox.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidHardwareInfoProvider(private val context: Context) : HardwareInfoProvider {

    override suspend fun getHardwareInfo(): HardwareInfo = withContext(Dispatchers.IO) {
        HardwareInfo(
            cpu = getCpuInfo(),
            gpu = getGpuInfo(),
            screen = getScreenInfo(),
            memory = getMemoryInfo(),
            partitions = getDiskPartitions()
        )
    }

    private fun getCpuInfo(): CpuInfo {
        val model = readCpuModel()
        val arch = readCpuArch()
        val (physCores, logCores) = readCoreCounts()
        val maxFreq = readMaxFrequencyMHz()
        return CpuInfo(
            model = model,
            architecture = arch,
            physicalCores = physCores,
            logicalCores = logCores,
            maxFrequencyMHz = maxFreq
        )
    }

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
        // 3. /proc/cpuinfo Hardware / model name
        try {
            val lines = File("/proc/cpuinfo").readLines()
            lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
                ?.split(":")?.getOrElse(1) { "" }?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals("qcom", ignoreCase = true) }
                ?.let { return it }
            lines.firstOrNull { it.startsWith("model name", ignoreCase = true) }
                ?.split(":")?.getOrElse(1) { "" }?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
            lines.firstOrNull { it.startsWith("Processor", ignoreCase = true) }
                ?.split(":")?.getOrElse(1) { "" }?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        } catch (_: Exception) {}
        return Build.HARDWARE
    }

    private fun readCpuArch(): String {
        try {
            val lines = File("/proc/cpuinfo").readLines()
            lines.firstOrNull { it.startsWith("CPU architecture", ignoreCase = true) }
                ?.split(":")?.getOrElse(1) { "" }?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return "ARMv$it" }
        } catch (_: Exception) {}
        return Build.SUPPORTED_ABIS.firstOrNull() ?: ""
    }

    private fun readCoreCounts(): Pair<Int, Int> {
        val logical = Runtime.getRuntime().availableProcessors()
        // Try to find physical core count from CPU topology
        val physical = try {
            val cpuDir = File("/sys/devices/system/cpu")
            val cores = cpuDir.listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.size ?: logical
            cores
        } catch (_: Exception) { logical }
        return physical to logical
    }

    private fun readMaxFrequencyMHz(): Long {
        return try {
            val cpuDir = File("/sys/devices/system/cpu")
            cpuDir.listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }
                ?.mapNotNull { cpu ->
                    try {
                        File(cpu, "cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull()
                    } catch (_: Exception) { null }
                }
                ?.maxOrNull()
                ?.div(1000L) // kHz -> MHz
                ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun getGpuInfo(): GpuInfo? {
        // Try /sys/class/kgsl (Qualcomm Adreno)
        return try {
            val kgsl = File("/sys/class/kgsl/kgsl-3d0")
            if (kgsl.exists()) {
                val name = try { File(kgsl, "gpu_model").readText().trim() } catch (_: Exception) {
                    try { File(kgsl, "gpuinfo").readText().trim() } catch (_: Exception) { "" }
                }
                val vendor = "Qualcomm"
                if (name.isNotEmpty()) return GpuInfo(name = name, vendor = vendor, vramBytes = 0)
            }
            // Try Mali (ARM GPU)
            val mali = File("/sys/class/misc/mali0")
            if (mali.exists()) {
                return GpuInfo(name = "ARM Mali", vendor = "ARM", vramBytes = 0)
            }
            null
        } catch (_: Exception) { null }
    }

    private fun getScreenInfo(): ScreenInfo {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.displays.firstOrNull() ?: return ScreenInfo()
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            val refreshHz = display.refreshRate
            ScreenInfo(
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                refreshRateHz = refreshHz,
                ppiX = metrics.xdpi,
                ppiY = metrics.ydpi
            )
        } catch (_: Exception) { ScreenInfo() }
    }

    private fun getMemoryInfo(): MemoryInfo {
        return try {
            val lines = File("/proc/meminfo").readLines()
            fun readKb(key: String) = lines.firstOrNull { it.startsWith(key) }
                ?.split(":")?.getOrElse(1) { "0" }?.trim()
                ?.split("\\s+".toRegex())?.firstOrNull()?.toLongOrNull() ?: 0L
            val totalKb = readKb("MemTotal")
            val availKb = readKb("MemAvailable")
            val swapTotalKb = readKb("SwapTotal")
            val swapFreeKb = readKb("SwapFree")
            MemoryInfo(
                totalBytes = totalKb * 1024,
                availableBytes = availKb * 1024,
                swapTotalBytes = swapTotalKb * 1024,
                swapUsedBytes = (swapTotalKb - swapFreeKb) * 1024
            )
        } catch (_: Exception) { MemoryInfo() }
    }

    private fun getDiskPartitions(): List<DiskPartition> {
        return try {
            val mounts = File("/proc/mounts").readLines()
            val partitions = mutableListOf<DiskPartition>()
            for (line in mounts) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size < 2) continue
                val mount = parts[1]
                // Only show user-relevant mount points
                if (!mount.startsWith("/storage") && mount != "/data" && mount != "/sdcard" &&
                    !mount.startsWith("/mnt/media_rw")) continue
                try {
                    val stat = StatFs(mount)
                    val total = stat.blockCountLong * stat.blockSizeLong
                    val free = stat.availableBlocksLong * stat.blockSizeLong
                    if (total > 0) {
                        partitions.add(DiskPartition(mountPoint = mount, label = mount, totalBytes = total, freeBytes = free))
                    }
                } catch (_: Exception) {}
            }
            // Always include /data if not already
            if (partitions.none { it.mountPoint == "/data" }) {
                try {
                    val stat = StatFs("/data")
                    partitions.add(0, DiskPartition(mountPoint = "/data", label = "内部存储", totalBytes = stat.blockCountLong * stat.blockSizeLong, freeBytes = stat.availableBlocksLong * stat.blockSizeLong))
                } catch (_: Exception) {}
            }
            partitions
        } catch (_: Exception) { emptyList() }
    }
}

actual fun createHardwareInfoProvider(): HardwareInfoProvider =
    AndroidHardwareInfoProvider(ToolboxApplication.instance)