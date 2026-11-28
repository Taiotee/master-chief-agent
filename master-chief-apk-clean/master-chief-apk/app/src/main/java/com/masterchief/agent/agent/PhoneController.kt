package com.masterchief.agent.agent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast

/**
 * Executes on-device orders. Every method here is a real Android API call —
 * not a mock — but each one is bounded by what Android allows:
 *
 * - Opening apps: by package name / launcher intent. Works for installed apps.
 * - Calls / SMS: opens the dialer / SMS app with the number pre-filled, OR
 *   dials directly only with CALL_PHONE permission. The app never sends a
 *   text silently: it drafts it and the user taps Send (see README § Limits).
 * - Settings: Wi-Fi toggle needs CHANGE_WIFI_STATE (deprecated on Android 10+:
 *   the system may show a panel instead of toggling silently — handled below).
 *   Brightness / DND need WRITE_SETTINGS / notification-policy access, granted
 *   in system settings by the user.
 * - WhatsApp / Instagram / banking apps: NO direct control API exists.
 *   Chief opens them via intent and the user acts inside the app. Any code
 *   claiming to "control WhatsApp" is faking it — see docs/LIMITS.md.
 */
class PhoneController(private val context: Context) {

    fun openApp(appName: String): String {
        val pm = context.packageManager
        // Well-known packages first; otherwise search installed launchable apps by label.
        val known = mapOf(
            "whatsapp" to "com.whatsapp", "instagram" to "com.instagram.android",
            "tinder" to "com.tinder", "spotify" to "com.spotify.music",
            "gmail" to "com.google.android.gm", "maps" to "com.google.android.apps.maps",
            "youtube" to "com.google.android.youtube", "chrome" to "com.android.chrome",
            "camera" to "com.oppo.camera", "photos" to "com.google.android.apps.photos",
            "settings" to "com.android.settings"
        )
        val pkg = known[appName.lowercase().trim()]
            ?: pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .firstOrNull { app ->
                    pm.getApplicationLabel(app).toString().equals(appName, ignoreCase = true)
                }?.packageName
            ?: return "App not found on this phone, sir: $appName."
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return "Cannot launch $appName, sir."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Opening $appName, sir."
    }

    /** Open an app by its exact package name (used by the App Armory list). */
    fun openAppByPackage(pkg: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return "Cannot launch that app, sir."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Opening, sir."
    }

    fun dial(numberOrName: String): String {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numberOrName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Dialler open for $numberOrName, sir. Tap call to connect."
    }

    fun draftText(numberOrName: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$numberOrName")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Message drafted for $numberOrName, sir. Review and tap send — Chief never sends silently."
    }

    fun setAlarm(hour: Int, minute: Int, label: String = "Master Chief alarm"): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Alarm set for %02d:%02d, sir.".format(hour, minute)
    }

    fun setWifi(on: Boolean): String {
        // Android 10+ restricts direct Wi-Fi toggling by third-party apps.
        // The honest behaviour: try, and fall back to the system panel.
        return try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ok = wm.setWifiEnabled(on)
            if (ok) "Wi-Fi ${if (on) "on" else "off"}, sir."
            else openWifiPanel("Wi-Fi cannot be toggled directly on Android 10+, sir — panel opened.")
        } catch (e: SecurityException) {
            openWifiPanel("Wi-Fi needs permission, sir — panel opened.")
        }
    }

    private fun openWifiPanel(msg: String): String {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return msg
    }

    fun setBrightness(percent: Int): String {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Brightness needs Write Settings permission, sir. Grant it once and Chief will handle it."
        }
        Settings.System.putInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
            (percent * 255 / 100).coerceIn(10, 255)
        )
        return "Brightness $percent percent, sir."
    }

    fun setVolume(percent: Int): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, max * percent / 100, 0)
        return "Volume $percent percent, sir."
    }

    fun playMusic(query: String): String {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent); "Opening music for \"$query\", sir."
        }.getOrElse { "No music app available, sir." }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
