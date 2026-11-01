package top.sykesp42.rin.jwxt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.sykesp42.rin.data.LocalJwxtApi

/**
 * Read-only JWXT login. The user signs in on the school's own web page; the
 * activity only captures the resulting cookie and hands it back as
 * [EXTRA_COOKIE]. No password is read, stored or posted by this app.
 *
 * Deliberately read-only: it captures the session and nothing else. No
 * sign-in automation, no server of our own, no account storage.
 */
class JwxtLoginActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var connectButton: Button
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var verifying = false
    /** Direct JWXT failed once; the WebVPN portal took over. */
    private var fellBackToWebVpn = false
    // The portal renders "教务系统H5" as a plain tile; after the portal login
    // completes we open it once so the user lands on JWXT directly instead of
    // having to find the tile themselves.
    private var autoOpenedJwxt = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configured = intent.getStringExtra(EXTRA_BASE_URL).orEmpty().trim().ifBlank { DEFAULT_BASE }
        // Go straight to the school's own JWXT login. Campus Wi-Fi (or an
        // existing VPN) reaches it directly; only when that fails do we fall
        // back to the WebVPN portal, which wraps the same page.
        val startUrl = configured

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_CANVAS)
            fitsSystemWindows = true
        }

        // Compact hint strip: what this page does and does not touch.
        root.addView(
            TextView(this).apply {
                text = "在学校页面登录后点「完成连接」。只存加密凭据，不存密码，只读课表。"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
        )

        webView = WebView(this)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = USER_AGENT
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                refreshConnectState(url)
                maybeOpenJwxt(view, url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (!request.isForMainFrame) return
                if (fellBackToWebVpn) return
                // Direct campus access failed (mobile data / off campus):
                // retry once through the WebVPN portal, which wraps the page.
                fellBackToWebVpn = true
                Toast.makeText(
                    this@JwxtLoginActivity,
                    "直连教务失败，改用 WebVPN 入口",
                    Toast.LENGTH_SHORT
                ).show()
                view.post { load(WEBVPN_ENTRY) }
            }
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(COLOR_SURFACE)
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }
        val cancel = Button(this).apply {
            text = "取消"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_TEXT)
            background = roundedStroke(COLOR_SURFACE_RAISED, dp(12))
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { finish() }
        }
        connectButton = Button(this).apply {
            text = "完成连接"
            isEnabled = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_PRIMARY_TEXT)
            background = roundedFill(COLOR_PRIMARY_BG, dp(14))
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { connect() }
        }
        actionBar.addView(
            cancel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(10) }
        )
        actionBar.addView(
            connectButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(actionBar)
        setContentView(root)
        load(startUrl)
    }

    private fun load(url: String) {
        val target = when {
            url.startsWith("http") -> url
            url.isNotBlank() -> "https://$url"
            else -> return
        }
        webView.loadUrl(target)
    }

    /** A cookie on the current page is necessary but not sufficient to connect. */
    private fun refreshConnectState(url: String) {
        val header = CookieManager.getInstance().getCookie(url).orEmpty()
        connectButton.isEnabled = header.isNotBlank()
    }

    /**
     * Portal login just finished: open the 教务 tile automatically, inside a
     * WebVPN-wrapped session. The portal is a single-page app, so the tile may
     * not exist yet when onPageFinished fires — retry a few times before
     * giving up. Silent no-op otherwise; the user can still tap it manually.
     */
    private fun maybeOpenJwxt(view: WebView, url: String) {
        val lower = url.lowercase()
        if (!lower.contains("webvpn")) return
        if (lower.contains("login") || lower.contains("authserver")) return
        if (lower.contains("jwxt") || lower.contains("jwgl")) return
        if (autoOpenedJwxt) return
        autoOpenedJwxt = true
        tryClickJwxt(view, attempt = 0)
    }

    private fun tryClickJwxt(view: WebView, attempt: Int) {
        if (isFinishing) return
        view.evaluateJavascript(DEBUG_LINKS_JS) { links ->
            android.util.Log.i(TAG, "portal links attempt=$attempt -> $links")
        }
        view.evaluateJavascript(CLICK_JWXT_JS) { result ->
            android.util.Log.i(TAG, "auto-open jwxt attempt=$attempt result=$result")
            val clicked = result?.contains("true") == true
            if (!clicked) {
                if (attempt < JWXT_CLICK_RETRIES) {
                    view.postDelayed({ tryClickJwxt(view, attempt + 1) }, 1200L)
                } else {
                    autoOpenedJwxt = false
                }
            }
        }
    }

    /**
     * Verify the captured cookie against the read-only timetable before
     * returning, so the user does not land on a "已连接" state that cannot
     * actually read anything.
     */
    private fun connect() {
        if (verifying) return
        val pageUrl = webView.url ?: return
        val header = CookieManager.getInstance().getCookie(pageUrl).orEmpty()
        if (header.isBlank()) {
            Toast.makeText(this, "还没看到登录凭据，先完成登录", Toast.LENGTH_SHORT).show()
            return
        }
        verifying = true
        connectButton.isEnabled = false
        connectButton.text = "验证中…"
        scope.launch {
            val verified = candidateBases(pageUrl).firstOrNull { base ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        LocalJwxtApi(base, cookieHeader = { header }).overview()
                    }.isSuccess
                }
            }
            if (verified != null) {
                setResult(
                    RESULT_OK,
                    Intent().putExtra(EXTRA_COOKIE, header).putExtra(EXTRA_BASE_URL, verified)
                )
                finish()
            } else {
                verifying = false
                connectButton.isEnabled = true
                connectButton.text = "完成连接"
                Toast.makeText(this@JwxtLoginActivity, "验证失败：确认已登录并停在教务首页", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Direct host, or the WebVPN-wrapped form (…/https/<token>/…). */
    private fun candidateBases(pageUrl: String): List<String> {
        val result = LinkedHashSet<String>()
        runCatching { URI(pageUrl) }.getOrNull()?.let { uri ->
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return@let
            result.add("$scheme://$host")
            val path = uri.path.orEmpty()
            val marker = "/https/"
            val markerAt = path.indexOf(marker)
            if (markerAt >= 0) {
                val rest = path.substring(markerAt + marker.length)
                val token = rest.substringBefore('/')
                if (token.isNotBlank()) result.add("$scheme://$host$marker$token")
            }
        }
        return result.toList()
    }

    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
            .toInt()

    private fun roundedFill(color: Int, radiusPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
        }

    private fun roundedStroke(color: Int, radiusPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
            setStroke(dp(1), COLOR_BORDER)
        }

    companion object {
        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_COOKIE = "cookie"

        fun intent(context: Context, baseUrl: String): Intent =
            Intent(context, JwxtLoginActivity::class.java).putExtra(EXTRA_BASE_URL, baseUrl)

        private const val DEFAULT_BASE = "https://jwxt.gdupt.edu.cn"
        private const val WEBVPN_ENTRY = "https://webvpn2.gdupt.edu.cn"

        /**
         * Finds the portal's 教务 tile whether it is an anchor or a clickable
         * div, then clicks it (bubbling up a few levels because SPA tiles put
         * the handler on an ancestor). Returns "true" when something was
         * clicked.
         */
        private const val CLICK_JWXT_JS = """
            (function () {
              try {
                var all = Array.prototype.slice.call(document.querySelectorAll('a,div,li,span,p'));
                var hit = null;
                for (var i = 0; i < all.length; i++) {
                  var el = all[i];
                  var text = (el.innerText || '').trim();
                  if (!text || text.length > 24) continue;
                  if (text.indexOf('教务') < 0) continue;
                  hit = el;
                  if (el.tagName === 'A') break;
                }
                if (!hit) return 'false';
                var a = hit.closest ? hit.closest('a') : null;
                if (a && a.href) { location.href = a.href; return 'true'; }
                var node = hit;
                for (var d = 0; d < 4 && node; d++) { node.click(); node = node.parentElement; }
                return 'true';
              } catch (e) { return 'false'; }
            })()
        """

        private const val TAG = "RinJwxt"
        private const val JWXT_CLICK_RETRIES = 5

        /** Debug helper: what does the portal actually link the 教务 tile to? */
        private const val DEBUG_LINKS_JS = """
            (function () {
              try {
                var out = [];
                var links = document.querySelectorAll('a');
                for (var i = 0; i < links.length && out.length < 5; i++) {
                  var href = links[i].href || '';
                  if (href.indexOf('/https/') < 0) continue;
                  var text = (links[i].innerText || '').trim().replace(/\s+/g, ' ');
                  out.push(text.slice(0, 14) + ' => ' + href.slice(0, 120));
                }
                return out.join(' || ') || 'none';
              } catch (e) { return 'err'; }
            })()
        """

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Rin/1.0"

        // Rin dark tokens (ui/theme), duplicated so this plain Activity needs no Compose.
        private val COLOR_CANVAS = 0xFF0B0E11.toInt()
        private val COLOR_SURFACE = 0xFF11161A.toInt()
        private val COLOR_SURFACE_RAISED = 0xFF181E23.toInt()
        private val COLOR_TEXT = 0xFFEAF1EE.toInt()
        private val COLOR_TEXT_SECONDARY = 0xFF95A39E.toInt()
        private val COLOR_BORDER = 0x1AEAF1EE.toInt()
        private val COLOR_PRIMARY_BG = 0xFFEAF1EE.toInt()
        private val COLOR_PRIMARY_TEXT = 0xFF0B0E11.toInt()
    }
}
