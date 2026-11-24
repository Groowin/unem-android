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

    override fun onListenerConnected() {
        // Қосылғанда шторкада тұрған хабарламаларды да қарап шығу (өткізіп алғандарды қайтарады)
        try { activeNotifications?.forEach { handle(it) } } catch (e: Exception) {}
    }

    override fun onListenerDisconnected() {
        // Жүйе байланысты үзсе, қайта қосылуды сұрау
        try { requestRebind(android.content.ComponentName(this, BankListener::class.java)) } catch (e: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    private fun handle(sbn: StatusBarNotification) {
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
        if (Store.add(this, id, sbn.packageName, title, text, sbn.postTime)) Notifier.notify(this, id, title, text, sbn.postTime)
    }
}
