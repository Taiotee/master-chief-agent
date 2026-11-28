package com.masterchief.agent.data

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Real device readouts, read from Android at runtime.
 * Nothing here is hardcoded or simulated: model, Android version, battery,
 * storage, RAM and network all come from the phone itself. If a value
 * cannot be read, the field says so (0 / "unknown") instead of inventing one.
 */
data class DeviceStatus(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdk: Int,
    val batteryPct: Int,
    val charging: Boolean,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val ramTotalBytes: Long,
    val ramAvailBytes: Long,
    val cpuCores: Int,
    val online: Boolean
)

data class AppEntry(val label: String, val packageName: String)

data class PermissionStatus(
    val photosGranted: Boolean,
    val notificationsGranted: Boolean,
    val notificationListenerEnabled: Boolean,
    val overlayGranted: Boolean,
    val usageAccessGranted: Boolean,
    val locationGranted: Boolean
)

object DeviceInfo {

    fun status(context: Context): DeviceStatus {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = runCatching {
            val filter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batt = context.registerReceiver(null, filter)
            val s = batt?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            s == BatteryManager.BATTERY_STATUS_CHARGING ||
                s == BatteryManager.BATTERY_STATUS_FULL
        }.getOrDefault(false)
        val stat = StatFs(Environment.getDataDirectory().path)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        return DeviceStatus(
            model = Build.MODEL ?: "unknown",
            manufacturer = Build.MANUFACTURER ?: "unknown",
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            sdk = Build.VERSION.SDK_INT,
            batteryPct = batteryPct,
            charging = charging,
            storageFreeBytes = stat.availableBytes,
            storageTotalBytes = stat.totalBytes,
            ramTotalBytes = mem.totalMem,
            ramAvailBytes = mem.availMem,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            online = online
        )
    }

    fun permissions(context: Context): PermissionStatus {
        val photosPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val listeners = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
        ).orEmpty()
        val listenerEnabled = listeners.contains(context.packageName)
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val usageAllowed = runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        return PermissionStatus(
            photosGranted = ContextCompat.checkSelfPermission(context, photosPerm) ==
                PackageManager.PERMISSION_GRANTED,
            notificationsGranted = notifGranted,
            notificationListenerEnabled = listenerEnabled,
            overlayGranted = Settings.canDrawOverlays(context),
            usageAccessGranted = usageAllowed,
            locationGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    /** Launchable apps actually installed on this phone, sorted by name. */
    fun installedApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(
                intent, PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return infos.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            val label = ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
            AppEntry(label, pkg)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}
