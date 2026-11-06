package dev.hubops.data.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.os.SystemClock
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import dev.hubops.core.model.DeviceInfo

/** Diagnóstico só de leitura, usando APIs públicas (sem privilégios). */
class DeviceInfoService(private val context: Context) {

    fun read(): DeviceInfo {
        val stat = StatFs(context.filesDir.path)
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryStatus == BatteryManager.BATTERY_STATUS_FULL
        return DeviceInfo(
            androidRelease = Build.VERSION.RELEASE ?: "?",
            sdk = Build.VERSION.SDK_INT,
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            batteryPercent = batteryPercent(batteryIntent),
            batteryCharging = charging,
            availableRamBytes = memory.availMem,
            totalRamBytes = memory.totalMem,
            freeBytes = stat.availableBytes,
            totalBytes = stat.totalBytes,
            uptimeMillis = SystemClock.elapsedRealtime(),
            termuxInstalled = isInstalled("com.termux"),
            shizukuInstalled = isInstalled("moe.shizuku.privileged.api"),
        )
    }

    private fun batteryPercent(intent: Intent?): Int {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
