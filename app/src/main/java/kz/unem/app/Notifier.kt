package kz.unem.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Notification
import org.json.JSONObject

/** Төлем ұсталғанда Үнемнің өз хабарламасы: сома, дүкен, болжам санат + түзету батырмалары. */
object Notifier {
    const val CH = "unem_pay"
    const val ACT = "kz.unem.app.CORRECT"
    val NAMES = linkedMapOf(
        "food" to "Тамақ", "transport" to "Көлік", "shop" to "Сатып алу", "subs" to "Жазылымдар",
        "telecom" to "Байланыс", "health" to "Денсаулық", "home" to "Үй", "fun" to "Көңіл-көтеру",
        "transfer" to "Аударым", "edu" to "Білім", "other" to "Басқа"
    )
    private val amt = Regex("(\\d[\\d\\s\\u00a0.,]*\\d|\\d)\\s*(₸|тг\\.?|kzt|тенге|тңг)", RegexOption.IGNORE_CASE)
    private val skipBefore = Regex("баланс|остаток|доступно|қалдық|лимит|комисс", RegexOption.IGNORE_CASE)
    private val incomeRe = Regex("пополнени|поступлени|поступил|зачислен|возврат|кіріс|толықтыр|получил|получен|перевод от|вам перевели|входящ|түсті|келді|жалақы|зарплат", RegexOption.IGNORE_CASE)
    private val plusAmt = Regex("(?:^|[\\s:(])\\+\\s*\\d")
    private val phoneTopup = Regex("пополнени[а-яё]*[\\s:.,\\-]*(?:мобильн|телефон|номер|связ|интернет|beeline|билайн|activ|актив|altel|алтел|tele2|теле2)", RegexOption.IGNORE_CASE)
    private val merchRe = Regex("(?:покупка|оплата|платеж|платёж|purchase|төлем|сатып алу)[:\\s]+(.{2,40}?)(?:\\.|,|;|\\sдоступно|\\sбаланс|\\sостаток|$)", RegexOption.IGNORE_CASE)
    private val KW = listOf(
        "subs" to Regex("netflix|spotify|youtube|apple\\.com|itunes|icloud|chatgpt|openai|подписк|жазылым|okko|kinopoisk", RegexOption.IGNORE_CASE),
        "telecom" to Regex("beeline|билайн|kcell|activ\\b|tele2|altel|мобильн|байланыс|интернет", RegexOption.IGNORE_CASE),
        "transport" to Regex("yandex ?go|яндекс|indrive|такси|taxi|бензин|азс|helios|sinooil|парковк|автобус|onay", RegexOption.IGNORE_CASE),
        "food" to Regex("magnum|small|galmart|market|маркет|продукт|кафе|cafe|coffee|кофе|pizza|пицц|burger|restaurant|ресторан|kfc|dodo|(?<![а-яәіңғүұқөһё])еда|food|хлеб|пекарн|sushi|суши|шашлык", RegexOption.IGNORE_CASE),
        "health" to Regex("аптека|pharm|фарм|дәріхана|клиника|больница|стоматолог", RegexOption.IGNORE_CASE),
        "shop" to Regex("zara|lcw|wildberries|ozon|aliexpress|temu|mechta|sulpak|технодом|одежд|kaspi магазин", RegexOption.IGNORE_CASE),
        "transfer" to Regex("перевод|аударым|transfer", RegexOption.IGNORE_CASE)
    )

    fun key(s: String): String = s.lowercase().replace(Regex("[^\\p{L}0-9 ]"), "").replace(Regex("\\s+"), " ").trim()

    private val recent = ArrayList<Pair<Long, Long>>() // amount to ts (Wallet + банк екі хабарлама бермеуі үшін)

    private val tailRe = Regex("(доступно|баланс|остаток|қалдық|қолжетімді|available)[\\s\\S]*$", RegexOption.IGNORE_CASE)
    private val bankOnly = Regex("^\\s*(kaspi\\s*(gold|pay|red|bank)?|freedom\\s*(bank)?|halyk|bcc|jusan)\\s*$", RegexOption.IGNORE_CASE)

    // Хабарламадан тек сатушы атауын алады (сома, баланс, карта мәтінін қиып тастайды)
    fun cleanMerch(title: String, text: String): String {
        var s = ((if (bankOnly.matches(title)) "" else title) + " " + text)
            .replace(tailRe, " ").replace(amt, " ")
            .replace(Regex("списано с\\s*\\S+\\s*\\*+\\d{3,4}", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+с\\s+карты(?=\\s|$)[\\s\\S]*$", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim()
        s = s.replace(Regex("^\\s*(покупка|оплата|платеж|платёж|purchase|төлем|сатып алу)\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(?:IP|ИП|ТОО|TOO|LLP|AO|АО)\\s+"), "")
            .replace(Regex("^[\\s,.;:·\\-–—|]+|[\\s,.;:·\\-–—|]+$"), "")
        return s.take(40)
    }

    fun parse(title: String, text: String): Triple<Long, String, Boolean>? {
        val all = "$title $text"
        var found: Long? = null
        for (m in amt.findAll(all)) {
            val pre = all.substring(maxOf(0, m.range.first - 16), m.range.first)
            if (skipBefore.containsMatchIn(pre)) continue
            val digits = m.groupValues[1].replace(Regex("[\\s\\u00a0]"), "").replace(',', '.')
            val v = digits.toDoubleOrNull() ?: continue
            found = Math.round(v); break
        }
        val sum = found ?: return null
        if (sum <= 0) return null
        val income = (incomeRe.containsMatchIn(all) || plusAmt.containsMatchIn(all)) && !phoneTopup.containsMatchIn(all)
        var merch = cleanMerch(title, text)
        if (merch.isEmpty()) merch = merchRe.find(all)?.groupValues?.get(1)?.trim() ?: ""
        if (merch.isEmpty()) merch = text.replace(amt, "").replace(Regex("\\s+"), " ").trim().take(40)
        return Triple(sum, merch, income)
    }

    fun guess(ctx: Context, merch: String, all: String): String {
        val k = key(merch)
        try {
            val rules = JSONObject(Store.rules(ctx))
            if (k.isNotEmpty() && rules.has(k)) return rules.getString(k)
            val it = rules.keys()
            while (it.hasNext()) { val rk = it.next(); if (rk.length >= 4 && k.contains(rk)) return rules.getString(rk) }
        } catch (e: Exception) {}
        for ((c, re) in KW) if (re.containsMatchIn(all)) return c
        return "other"
    }

    fun money(v: Long): String = String.format("%,d", v).replace(',', ' ') + " ₸"

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CH) == null)
                nm.createNotificationChannel(NotificationChannel(CH, "Төлемдер", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun notify(ctx: Context, nid: String, title: String, text: String, ts: Long) {
        if (!Store.notifyOn(ctx)) return
        try {
            val p = parse(title, text) ?: return
            val (sum, merch, income) = p
            if (income) return
            synchronized(recent) {
                recent.removeAll { ts - it.second > 5 * 60_000 || it.second - ts > 5 * 60_000 }
                if (recent.any { it.first == sum }) return
                recent.add(sum to ts)
            }
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return
            channel(ctx)
            val cat = guess(ctx, merch, "$title $text")
            val h = nid.hashCode()
            val open = PendingIntent.getActivity(ctx, h, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val b = Notification.Builder(ctx, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("−" + money(sum) + (if (merch.isNotEmpty()) " · $merch" else ""))
                .setContentText("Санат: " + (NAMES[cat] ?: "Басқа") + " · дұрыс болмаса, өзгертіңіз")
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS).setTimeoutAfter(6 * 3600_000L)
            var n = 0
            for ((c, name) in NAMES) {
                if (c == cat || c == "other" && cat != "other") continue
                if (n >= 2) break
                val i = Intent(ctx, CorrectionReceiver::class.java).setAction(ACT).putExtra("nid", nid).putExtra("cat", c).putExtra("sum", sum).putExtra("merch", merch)
                b.addAction(0, name, PendingIntent.getBroadcast(ctx, h * 31 + c.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                n++
            }
            if (Build.VERSION.SDK_INT >= 24) {
                val ri = RemoteInput.Builder("reply").setLabel("Не үшін? (мысалы: шашлык)").build()
                val ri2 = Intent(ctx, CorrectionReceiver::class.java).setAction(ACT).putExtra("nid", nid).putExtra("sum", sum).putExtra("merch", merch)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(ctx, h * 31 + 7, ri2, flags)
                b.addAction(Notification.Action.Builder(0, "Жауап", pi).addRemoteInput(ri).build())
            }
            nm.notify(h, b.build())
        } catch (e: SecurityException) {} catch (e: Exception) {}
    }

    fun confirm(ctx: Context, nid: String, sum: Long, merch: String, cat: String, reply: String = "") {
        try {
            channel(ctx)
            val n = Notification.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(if (reply.isNotEmpty()) "✓ " + reply else "✓ " + (NAMES[cat] ?: "Басқа"))
                .setContentText("−" + money(sum) + (if (merch.isNotEmpty()) " · $merch" else "") + " — санат сақталды, Үнем үйренді")
                .setAutoCancel(true).setTimeoutAfter(8000).setOnlyAlertOnce(true).build()
            ctx.getSystemService(NotificationManager::class.java).notify(nid.hashCode(), n)
        } catch (e: Exception) {}
    }
}
