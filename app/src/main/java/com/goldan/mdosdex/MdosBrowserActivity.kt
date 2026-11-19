package com.goldan.mdosdex

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.net.URLEncoder

class MdosBrowserActivity : Activity() {
    companion object { const val EXTRA_URL = "mdos.browser.url" }
    private lateinit var browser: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var pageState: TextView
    private val navy = Color.rgb(16, 24, 39)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6)); setBackgroundColor(navy)
        }
        fun toolButton(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
            background = rounded(Color.rgb(36, 52, 74), 9); minWidth = dp(42)
            setPadding(dp(5), 0, dp(5), 0); setOnClickListener { action() }
        }
        val back = toolButton("‹") { if (browser.canGoBack()) browser.goBack() }
        val forward = toolButton("›") { if (browser.canGoForward()) browser.goForward() }
        val reload = toolButton("⟳") { browser.reload() }
        address = EditText(this).apply {
            hint = "نشانی سایت یا جستجو در Google"; textSize = 14f; setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO; setTextColor(Color.WHITE); setHintTextColor(0xFFBAC5D3.toInt())
            background = rounded(Color.rgb(36, 52, 74), 10); setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        val go = toolButton("برو") { navigate(address.text.toString()) }
        val home = toolButton("⌂") { browser.loadUrl("https://www.google.com") }
        listOf(back, forward, reload).forEach { toolbar.addView(it, LinearLayout.LayoutParams(dp(42), dp(44)).apply { rightMargin = dp(4) }) }
        toolbar.addView(address, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        toolbar.addView(go, LinearLayout.LayoutParams(dp(54), dp(44)).apply { rightMargin = dp(4) })
        toolbar.addView(home, LinearLayout.LayoutParams(dp(42), dp(44)))
        root.addView(toolbar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        pageState = TextView(this).apply {
            text = "مرورگر داخلی M-DOS · جستجو با Google"; textSize = 11f
            setTextColor(0xFF526174.toInt()); setPadding(dp(10), dp(3), dp(10), dp(3)); maxLines = 1
        }
        root.addView(pageState, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)))
        browser = WebView(this)
        browser.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            useWideViewPort = true; loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true; setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false; allowContentAccess = true; allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT; builtInZoomControls = true; displayZoomControls = false
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(browser, true) }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") return false
                return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true }
                catch (_: Exception) {
                    Toast.makeText(this@MdosBrowserActivity, "این پیوند در دستگاه پشتیبانی نمی‌شود.", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageState.text = "در حال بارگذاری صفحه…"
                if (!address.hasFocus()) address.setText(url.orEmpty())
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!address.hasFocus()) address.setText(url.orEmpty())
                pageState.text = view?.title?.takeIf { it.isNotBlank() } ?: url.orEmpty()
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) pageState.text = "بارگذاری ناموفق: " + (error?.description ?: "خطای شبکه")
            }
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) pageState.text = title
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigate(address.text.toString()); true
            } else false
        }
        navigate(intent.getStringExtra(EXTRA_URL).orEmpty().ifBlank { "https://www.google.com" })
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        val target = when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            !value.contains(Regex("\\\\s")) && value.contains('.') &&
                value.matches(Regex("[\\\\p{L}0-9.-]+(:[0-9]+)?(/[^\\\\s]*)?")) -> "https://$value"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8")
        }
        address.setText(target); browser.loadUrl(target)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() { if (::browser.isInitialized && browser.canGoBack()) browser.goBack() else finish() }
    override fun onDestroy() {
        if (::browser.isInitialized) {
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.stopLoading(); browser.loadUrl("about:blank"); browser.destroy()
        }
        super.onDestroy()
    }
}
