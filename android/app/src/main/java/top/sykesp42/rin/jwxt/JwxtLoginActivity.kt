package top.sykesp42.rin.jwxt

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
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
    // Credentials as typed on the school page, kept only in memory until the
    // session verifies; then they land in the Keystore-encrypted store for the
    // next login's autofill.
    @Volatile private var rememberedUser = ""
    @Volatile private var rememberedPass = ""
    private var autofilled = false
    private var captured = false

    /** Bridge for the page-side hook: pushes what the user types. */
    private inner class RinCred {
        @JavascriptInterface
        fun onInput(user: String, pass: String) {
            rememberedUser = user.orEmpty().trim()
            rememberedPass = pass.orEmpty()
        }
    }

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
                text = "在学校页面登录后自动连接。账号密码只保存在本机加密存储里，用于下次自动填充；只读课表。"
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
        webView.addJavascriptInterface(RinCred(), "RinCred")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                refreshConnectState(url)
                injectCredentialHook()
                maybeAutofill()
                maybeCapture(url)
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

    /**
     * Fill the school login form from the Keystore-encrypted pair, so the
     * password never passes through adb, logs or the clipboard. The page's own
     * submit handler still encrypts it; the user still solves any slider.
     */
    private fun maybeAutofill() {
        if (autofilled) return
        attemptAutofill(0)
    }

    /** The form can be rendered after onPageFinished; retry a few times. */
    private fun attemptAutofill(attempt: Int) {
        if (autofilled || attempt > 5 || isFinishing) return
        val credential = runCatching {
            top.sykesp42.rin.data.AppContainer.get(this).jwxtCredentialStore.load()
        }.getOrNull()
        if (credential == null || credential.name.isBlank() || credential.value.isBlank()) return
        val user = org.json.JSONObject.quote(credential.name)
        val pass = org.json.JSONObject.quote(credential.value)
        val js = """
            (function(){
              var u=${'$'}user, p=${'$'}pass;
              var docs=[];
              try{ docs.push(document); }catch(e){}
              try{
                for (var fi=0; fi<window.frames.length; fi++){
                  try{ docs.push(window.frames[fi].document); }catch(e){}
                }
              }catch(e){}
              function fillOne(doc, sels, v){
                for (var i=0;i<sels.length;i++){
                  var el=null;
                  try{ el=doc.querySelector(sels[i]); }catch(e){}
                  if(!el) continue;
                  if(!el.value){
                    try{
                      el.focus(); el.value=v;
                      el.dispatchEvent(new Event('input',{bubbles:true}));
                      el.dispatchEvent(new Event('change',{bubbles:true}));
                      el.blur();
                    }catch(e){}
                  }
                  return true;
                }
                return false;
              }
              var userSels=['#username','input[name=username]','input[name=account]',
                            'input[name=xh]','input[placeholder*=学号]','input[type=text]'];
              var passSels=['#password','input[name=password]','input[name=pwd]',
                            'input[placeholder*=密码]','input[type=password]'];
              var a=false, b=false;
              for(var d=0; d<docs.length; d++){
                a = fillOne(docs[d], userSels, u) || a;
                b = fillOne(docs[d], passSels, p) || b;
              }
              return a&&b;
            })()
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            if (result == "true") {
                autofilled = true
                Toast.makeText(
                    this@JwxtLoginActivity,
                    "已填入本机保存的账号密码；完成滑块后登录",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                webView.postDelayed({ attemptAutofill(attempt + 1) }, 1_200L + attempt * 800L)
            }
        }
    }

    /** Injected on every page load: records typed credentials as the user TYPES. */
    private fun injectCredentialHook() {
        webView.evaluateJavascript(CRED_HOOK_JS, null)
    }

    /** After any page load, opportunistically capture when a session exists. */
    private fun maybeCapture(pageUrl: String) {
        if (pageUrl.contains("login")) return
        if (captured) return
        val raw = CookieManager.getInstance().getCookie(pageUrl).orEmpty()
        if (raw.isBlank()) return
        captured = true
        scope.launch {
            val base = candidateBases(pageUrl).firstOrNull { candidate ->
                withContext(Dispatchers.IO) {
                    runCatching { LocalJwxtApi(candidate, cookieHeader = { raw }).overview() }.isSuccess
                }
            }
            if (base != null) {
                // Remember the typed pair for the next login, Keystore-encrypted.
                if (rememberedUser.isNotBlank() && rememberedPass.isNotBlank()) {
                    runCatching {
                        top.sykesp42.rin.data.AppContainer.get(this@JwxtLoginActivity)
                            .jwxtCredentialStore.save(
                                top.sykesp42.rin.data.SessionCookie(name = rememberedUser, value = rememberedPass)
                            )
                    }
                }
                runCatching {
                    top.sykesp42.rin.data.AppContainer.get(this@JwxtLoginActivity)
                        .jwxtSessions.save(top.sykesp42.rin.data.SessionCookie("Cookie", raw))
                }
                Toast.makeText(this@JwxtLoginActivity, "教务已连接", Toast.LENGTH_SHORT).show()
                setResult(
                    RESULT_OK,
                    Intent().putExtra(EXTRA_COOKIE, raw).putExtra(EXTRA_BASE_URL, base)
                )
                finish()
            } else {
                captured = false
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

        /**
         * Injected on every page load: records typed 学号/密码 as the user
         * TYPES (submit-time reads see CAS-ciphertext), preferring user-ish
         * fields over captcha boxes. Both input orders push.
         */
        val CRED_HOOK_JS = """
            (function(){
              if (window.__rinCredHook) return; window.__rinCredHook = 1;
              function passLike(el){
                var t=(el.type||'').toLowerCase();
                if(t==='password') return true;
                var k=((el.name||'')+' '+(el.id||'')).toLowerCase();
                return t==='text' && /pass|pwd|(^|[^a-z])mm([^a-z]|${'$'})/.test(k);
              }
              function userish(el){
                var k=((el.name||'')+' '+(el.id||'')+' '+(el.placeholder||'')).toLowerCase();
                return /user|name|account|login|sno|xh|学号|账号|用户/.test(k);
              }
              var u='', anyU='', p='';
              function scan(){
                try{
                  var inputs=document.querySelectorAll('input');
                  for(var i=0;i<inputs.length;i++){
                    var el=inputs[i];
                    if(passLike(el)) continue;
                    var t=(el.type||'text').toLowerCase();
                    if(t!=='text'&&t!=='tel'&&t!=='') continue;
                    if(!el.value) continue;
                    if(userish(el)) u=el.value;
                  }
                }catch(e){}
              }
              function push(){
                try{
                  if(!p) return;
                  if(!u) scan();
                  var user=u||anyU;
                  if(user&&p) RinCred.onInput(user,p);
                }catch(e){}
              }
              function track(ev){
                var el=ev.target;
                if(!el || el.tagName!=='INPUT') return;
                if(passLike(el)){ p=el.value; push(); return; }
                var t=(el.type||'text').toLowerCase();
                if(t==='text'||t==='tel'||t===''){
                  if(userish(el)){ u=el.value; if(p) push(); }
                  else { anyU=el.value; if(p && !u) push(); }
                }
              }
              document.addEventListener('input', track, true);
              document.addEventListener('change', track, true);
            })()
        """.trimIndent()
        private const val WEBVPN_ENTRY = "https://webvpn2.gdupt.edu.cn"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Rin/1.0"

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
