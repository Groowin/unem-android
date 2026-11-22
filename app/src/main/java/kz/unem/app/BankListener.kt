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
    private val bankPkg = Regex("kaspi|ffin|freedom|bcc|halyk|homebank|walletnfcrel|jusan|eurasian|forte|bank", RegexOption.IGNORE_CASE)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = big ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val all = "$title $text"
        val isMoney = money.containsMatchIn(all)
        // Банк қосымшасынан келген кез келген хабарлама журналға жазылады (диагностика)
        if (isMoney || bankPkg.containsMatchIn(sbn.packageName)) Store.log(this, sbn.packageName, title, text, sbn.postTime, isMoney)
        if (!isMoney) return
        val id = sbn.packageName + ":" + sbn.postTime + ":" + all.hashCode()
        Store.add(this, id, sbn.packageName, title, text, sbn.postTime)
    }
}
