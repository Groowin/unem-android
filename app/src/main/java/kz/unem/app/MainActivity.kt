package kz.unem.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import android.graphics.Bitmap
import android.net.ConnectivityManager
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

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val url = "https://groowin.github.io/aqsha-qayda/"
    private var pendingToken: String? = null
    private var pageReady = false

    inner class Bridge {
        @JavascriptInterface fun pending(): String = Store.pending(this@MainActivity)
        @JavascriptInterface fun ack(ids: String) { try { Store.ack(this@MainActivity, ids) } catch (e: Exception) {} }
        @JavascriptInterface fun listenerEnabled(): Boolean {
            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(this@MainActivity, BankListener::class.java).flattenToString()
            return flat.split(":").any { TextUtils.equals(it, me) }
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
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
