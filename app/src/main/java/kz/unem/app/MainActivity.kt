package kz.unem.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.ComponentName
import android.text.TextUtils
import android.os.PowerManager
import android.service.notification.NotificationListenerService

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val url = "https://groowin.github.io/aqsha-qayda/"
    private var pendingToken: String? = null
    private var pageReady = false

    inner class Bridge {
        @JavascriptInterface fun pending(): String = Store.pending(this@MainActivity)
        @JavascriptInterface fun setBars(bg: String, darkIcons: Boolean) {
            runOnUiThread {
                try {
                    val c = android.graphics.Color.parseColor(bg)
                    window.statusBarColor = c
                    window.navigationBarColor = c
                    var f = window.decorView.systemUiVisibility
                    f = if (darkIcons) f or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else f and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    if (android.os.Build.VERSION.SDK_INT >= 26) f = if (darkIcons) f or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else f and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    window.decorView.systemUiVisibility = f
                } catch (e: Exception) {}
            }
        }
        @JavascriptInterface fun setRules(json: String) { Store.setRules(this@MainActivity, json) }
        @JavascriptInterface fun corrections(): String = Store.corr(this@MainActivity)
        @JavascriptInterface fun ackCorr(n: Int) { Store.ackCorr(this@MainActivity, n) }
        @JavascriptInterface fun log(): String = Store.log(this@MainActivity)
        @JavascriptInterface fun ack(ids: String) { try { Store.ack(this@MainActivity, ids) } catch (e: Exception) {} }
        @JavascriptInterface fun listenerEnabled(): Boolean {
            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(this@MainActivity, BankListener::class.java).flattenToString()
            return flat.split(":").any { TextUtils.equals(it, me) }
        }
        @JavascriptInterface fun http(id: String, method: String, url: String, auth: String, body: String) {
            // Groq браузерден CORS-ты қабылдамайды, сондықтан сұрауды native жібереді
            Thread {
                var code = -1
                var resp = ""
                try {
                    val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    c.requestMethod = method
                    c.connectTimeout = 15000
                    c.readTimeout = 30000
                    c.setRequestProperty("Content-Type", "application/json")
                    if (auth.isNotEmpty()) c.setRequestProperty("Authorization", auth)
                    if (method == "POST") {
                        c.doOutput = true
                        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                    code = c.responseCode
                    val st = if (code in 200..299) c.inputStream else c.errorStream
                    resp = st?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) { resp = e.message ?: "" }
                val js = "window.__nf&&window.__nf(" + JSONObject.quote(id) + "," + code + "," + JSONObject.quote(resp) + ")"
                runOnUiThread { web.evaluateJavascript(js, null) }
            }.start()
        }
        @JavascriptInterface fun batteryOk(): Boolean = try {
            (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        } catch (e: Exception) { true }
        @JavascriptInterface fun askBattery() {
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e2: Exception) {}
                }
            }
        }
        @JavascriptInterface fun share(text: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Үнем көшірмесі")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(i, "Үнем көшірмесін жіберу"))
            }
        }
        @JavascriptInterface fun googleSignIn() {
            runOnUiThread {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url + "auth.html"))) } catch (e: Exception) {}
            }
        }
        @JavascriptInterface fun openSettings() {
            runOnUiThread {
                try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
                catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try { NotificationListenerService.requestRebind(ComponentName(this, BankListener::class.java)) } catch (e: Exception) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
        }
        web.addJavascriptInterface(Bridge(), "UnemNative")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.url.host == "groowin.github.io") return false
                startActivity(Intent(Intent.ACTION_VIEW, r.url)); return true
            }
            override fun onPageStarted(v: WebView, u: String?, f: Bitmap?) { pageReady = false }
            override fun onPageFinished(v: WebView, u: String?) { pageReady = true; deliverToken() }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                // Желі жоқ болса кэштен ашуға тырысамыз
                if (r.isForMainFrame) { v.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK; v.loadUrl(url) }
            }
        }
        web.loadUrl(url)
        handleIntent(intent)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        handleIntent(i)
    }

    private fun handleIntent(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme == "unem") {
            val tok = d.fragment
            if (!tok.isNullOrEmpty()) { pendingToken = tok; deliverToken() }
        }
    }

    private fun deliverToken() {
        val t = pendingToken ?: return
        if (!pageReady) return
        pendingToken = null
        web.evaluateJavascript("window.unemAuth && window.unemAuth(" + JSONObject.quote(t) + ")", null)
    }

    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
