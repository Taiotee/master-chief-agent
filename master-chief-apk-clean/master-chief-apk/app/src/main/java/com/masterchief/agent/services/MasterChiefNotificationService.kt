package com.masterchief.agent.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification filtering.
 *
 * Android only delivers notifications here AFTER the user enables Master
 * Chief in Settings > Notifications > Device & app notifications. The app
 * cannot enable itself, and cannot read notifications silently.
 *
 * What it does: forwards each notification to the ViewModel, which scores
 * it (priority vs noise, using the same idea as the Muse hub: security
 * alerts and direct messages = priority; bulk promos = noise) and shows
 * only priority ones in the Comms screen. Reply/dismiss go through
 * NotificationListenerService APIs, with the user's tap.
 *
 * What it does NOT do: hide notifications system-wide, or reply on its
 * own. Android does not allow that without the user acting.
 */
class MasterChiefNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Hand off to the app via a broadcast / repository in the full build.
        // Kept minimal here: the Comms screen reads active notifications
        // through getActiveNotifications() when it is open.
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }
}
