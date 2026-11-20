package kz.unem.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Тек ақша белгісі бар (₸, тг, KZT, тенге) хабарламаларды сақтайды.
 * Басқа хабарламалар (чат, пошта) ешқайда сақталмайды.
 */
class BankListener : NotificationListenerService() {
    private val money = Regex("₸|тг\\b|тг\\.|kzt|тенге|тңг", RegexOption.IGNORE_CASE)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = big ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val all = "$title $text"
        if (!money.containsMatchIn(all)) return
        val id = sbn.packageName + ":" + sbn.postTime + ":" + all.hashCode()
        Store.add(this, id, sbn.packageName, title, text, sbn.postTime)
    }
}
