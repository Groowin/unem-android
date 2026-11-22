package kz.unem.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ұсталған хабарламалар қосымша жабық болса да жоғалмайтындай SharedPreferences-те тұрады. */
object Store {
    private const val P = "unem"
    private const val K = "pending"
    private const val MAX = 500

    @Synchronized
    fun add(ctx: Context, id: String, pkg: String, title: String, text: String, ts: Long) {
        val sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString(K, "[]"))
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("id") == id) return
        arr.put(JSONObject().put("id", id).put("pkg", pkg).put("title", title).put("text", text).put("ts", ts))
        val out = JSONArray()
        val from = maxOf(0, arr.length() - MAX)
        for (i in from until arr.length()) out.put(arr.get(i))
        sp.edit().putString(K, out.toString()).apply()
    }

    @Synchronized
    fun pending(ctx: Context): String =
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString(K, "[]") ?: "[]"

    @Synchronized
    fun ack(ctx: Context, idsJson: String) {
        val done = HashSet<String>()
        val d = JSONArray(idsJson)
        for (i in 0 until d.length()) done.add(d.getString(i))
        val sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString(K, "[]"))
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!done.contains(o.optString("id"))) out.put(o)
        }
        sp.edit().putString(K, out.toString()).apply()
    }

    private const val LK = "log"

    /** Соңғы 40 ұсталған/өткізілген хабарлама (диагностика үшін, тек осы телефонда). */
    @Synchronized
    fun log(ctx: Context, pkg: String, title: String, text: String, ts: Long, ok: Boolean) {
        val sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString(LK, "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("pkg") == pkg && o.optLong("ts") == ts && o.optString("text") == text) return
        }
        arr.put(JSONObject().put("pkg", pkg).put("title", title).put("text", text).put("ts", ts).put("ok", ok))
        val out = JSONArray()
        val from = maxOf(0, arr.length() - 40)
        for (i in from until arr.length()) out.put(arr.get(i))
        sp.edit().putString(LK, out.toString()).apply()
    }

    @Synchronized
    fun log(ctx: Context): String =
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString(LK, "[]") ?: "[]"
}
