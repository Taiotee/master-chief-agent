package com.masterchief.agent.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * A live notification seen on this phone.
 * Only title + text + app package — no message bodies are stored anywhere
 * else, and nothing is sent off the phone.
 */
data class LiveNotification(
    val id: String,
    val app: String,
    val title: String,
    val text: String,
    val epochMs: Long
)

/**
 * In-memory hub: the service posts here, the Comms/SITREP screens read it.
 * Android only delivers notifications here AFTER the user enables Master
 * Chief in Settings > Notifications > Device & app notifications. The app
 * cannot enable itself, and cannot read notifications silently. If the
 * listener is off, this list is simply empty and the UI says so.
 */
object NotificationHub {
    private val _notifications = MutableStateFlow<List<LiveNotification>>(emptyList())
    val notifications: StateFlow<List<LiveNotification>> = _notifications

    fun post(n: LiveNotification) {
        _notifications.update { (listOf(n) + it).take(50) }
    }

    fun remove(id: String) {
        _notifications.update { list -> list.filter { it.id != id } }
    }
}

class MasterChiefNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val title = sbn.notification.extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        NotificationHub.post(
            LiveNotification(sbn.key, sbn.packageName, title, text, sbn.postTime)
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationHub.remove(sbn.key)
    }
}
