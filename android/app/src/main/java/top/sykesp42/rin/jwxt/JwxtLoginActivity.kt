package top.sykesp42.rin.jwxt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.sykesp42.rin.data.AppContainer
import top.sykesp42.rin.data.LocalJwxtApi
import top.sykesp42.rin.data.SessionCookie

/**
 * Embedded JWXT login, styled after the app's dark design tokens.
 *
 * Layout follows the product rule "primary action in the thumb zone": the
 * address bar stays a compact top strip (inset-safe), the WebView fills the
 * middle, and the primary 连接 action is a full-width bottom button that
 * never collides with the system status bar or its overlays.
 */
class JwxtLoginActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var connectButton: Button
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var verifying = false
    private var captured = false
    private var attendanceMode = false
    private var waAttempts = 0
    private var autofilled = false

    // Typed credentials reported by the injected input hook. Volatile: the
    // JavascriptInterface callback runs on the WebView's JavaBridge thread.
    @Volatile private var rememberedUser = ""
    @Volatile private var rememberedPass = ""

    /** JS bridge: the injected credential hook reports typed values here. */
    private class CredBridge(private val sink: (String, String) -> Unit) {
        @android.webkit.JavascriptInterface
        fun onInput(user: String, pass: String) {
            if (user.isNotBlank() && pass.isNotBlank() && user.length <= 64 && pass.length <= 64) {
                sink(user, pass)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        attendanceMode = intent.getBooleanExtra(EXTRA_ATTENDANCE, false)
        val container = AppContainer.get(this)
        val configured = kotlinx.coroutines.runBlocking {
            container.settings.current().jwxtBaseUrl
        }.ifBlank { DEFAULT_BASE }
        // Campus JWXT is unreachable from carrier networks; the WebVPN portal
        // is the universal entry (cookies survive, so an existing session
        // resumes straight into the portal). Attendance mode goes straight to
        // the school's attendance portal and captures its SSO token.
        val startUrl = if (attendanceMode) {
            ATTENDANCE_URL
        } else if (configured == DEFAULT_BASE) {
            WEBVPN_ENTRY
        } else {
            configured
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_CANVAS)
            fitsSystemWindows = true
        }

        // -- address strip (secondary action; compact, inset-safe) ----------
        val addressStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(COLOR_SURFACE)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val bar = EditText(this).apply {
            hint = "地址 · 校外填 WebVPN 入口"
            setText(startUrl)
            maxLines = 1
            setSingleLine(true)
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_TEXT_SECONDARY)
            background = roundedStroke(COLOR_SURFACE_RAISED, dp(9))
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        val go = Button(this).apply {
            text = "前往"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT)
            background = roundedStroke(COLOR_SURFACE_RAISED, dp(9))
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { load(bar.text.toString().trim()) }
        }
        addressStrip.addView(
            bar,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        addressStrip.addView(go)
        root.addView(addressStrip)

        // -- web content ----------------------------------------------------
        webView = WebView(this)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = USER_AGENT
        }
        webView.addJavascriptInterface(
            CredBridge { user, pass ->
                rememberedUser = user
                rememberedPass = pass
            },
            "RinCred"
        )
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                if (attendanceMode) {
                    captureWaToken()
                } else {
                    injectCredentialHook()
                    maybeCapture(finishedUrl)
                    maybeAutofill(finishedUrl)
                }
            }
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // -- primary action bar (thumb zone) ---------------------------------
        val actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_SURFACE)
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }
        actionBar.addView(
            TextView(this).apply {
                text = if (attendanceMode) {
                    "登录考勤系统后点下方按钮完成连接；用于读取本人官方考勤记录。"
                } else {
                    "登录到教务页面后点下方按钮完成连接；校外先在地址栏进 WebVPN 登录。"
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(COLOR_TEXT_SECONDARY)
            }
        )
        connectButton = Button(this).apply {
            text = if (attendanceMode) "连接考勤系统" else "连接教务会话"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_PRIMARY_TEXT)
            background = roundedFill(COLOR_PRIMARY_BG, dp(14))
            setPadding(0, dp(13), 0, dp(13))
            setOnClickListener { if (attendanceMode) captureWaNow() else captureNow() }
        }
        actionBar.addView(
            connectButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
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

    /**
     * Explicit capture: read the CURRENT frame URL (framesets never re-fire
     * onPageFinished when inner iframes navigate), verify the schedule, save.
     */
    private fun captureNow() {
        if (verifying) return
        verifying = true
        connectButton.isEnabled = false
        connectButton.text = "验证中…"
        scope.launch {
            val href = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    webView.evaluateJavascript("(function(){return location.href})()") { value ->
                        continuation.resume(value?.trim()?.removePrefix("\"")?.removeSuffix("\"").orEmpty()) { }
                    }
                }
            }
            trace("capture href=$href")
            val raw = CookieManager.getInstance().getCookie(href).orEmpty()
            val container = AppContainer.get(this@JwxtLoginActivity)
            var verified: Pair<String, String>? = null
            for (base in candidateBases(href)) {
                val api = LocalJwxtApi(base, cookieHeader = { raw })
                val result = withContext(Dispatchers.IO) { runCatching { api.overview() } }
                val outcome = result.fold({ "ok" }, { (it.message ?: "fail").take(120) })
                trace("capture try $base -> $outcome")
                if (result.isSuccess) {
                    verified = base to raw
                    break
                }
            }
            if (verified != null) {
                val (base, cookie) = verified
                persistVerified(container, base, cookie)
                Toast.makeText(this@JwxtLoginActivity, "教务已连接", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(
                    this@JwxtLoginActivity,
                    "验证失败：确认已登录并停在教务页面",
                    Toast.LENGTH_LONG
                ).show()
                connectButton.isEnabled = true
                connectButton.text = "连接教务会话"
                verifying = false
            }
        }
    }

    /**
     * Attendance mode: the wa portal mints its JWT during SSO and keeps it in
     * sessionStorage["t"]. Poll briefly because the SPA finishes its own
     * ticket exchange after the page load event.
     */
    private fun captureWaNow() {
        if (verifying) return
        waAttempts = 0
        connectButton.isEnabled = false
        connectButton.text = "读取考勤登录态…"
        captureWaToken()
        webView.postDelayed({
            if (!verifying && !isFinishing) {
                connectButton.isEnabled = true
                connectButton.text = "连接考勤系统"
                Toast.makeText(
                    this@JwxtLoginActivity,
                    "还没检测到考勤登录，先在页面完成登录",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, 8_000)
    }

    private fun captureWaToken() {
        if (verifying || isFinishing) return
        webView.evaluateJavascript(
            "(function(){try{return sessionStorage.getItem('t')||''}catch(e){return ''}})()"
        ) { value ->
            val token = value?.trim()?.removePrefix("\"")?.removeSuffix("\"")
                ?.takeIf { it.isNotBlank() && it != "null" }
            if (token == null) {
                if (waAttempts < 10) {
                    waAttempts++
                    webView.postDelayed({ captureWaToken() }, 1_500)
                }
                return@evaluateJavascript
            }
        }
    }

    private fun maybeAutofill(url: String) {
        if (autofilled) return
        // The WebVPN-wrapped URL often has no "login" in it; try every page
        // and let the field probing decide.
        attemptAutofill(0)
    }

    /** The form can be rendered after onPageFinished; retry a few times. */
    private fun attemptAutofill(attempt: Int) {
        if (autofilled || attempt > 5 || isFinishing) return
        val credential = runCatching {
            AppContainer.get(this).jwxtCredentialStore.load()
        }.getOrNull()
        if (credential == null || credential.name.isBlank() || credential.value.isBlank()) {
            trace("autofill skipped: no saved credential")
            return
        }
        val user = org.json.JSONObject.quote(credential.name)
        val pass = org.json.JSONObject.quote(credential.value)
        val js = """
            (function(){
              var u=$user, p=$pass;
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
                trace("autofill ok attempt=$attempt")
                Toast.makeText(
                    this@JwxtLoginActivity,
                    "已填入本机保存的账号密码；完成滑块后登录",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                trace("autofill miss attempt=$attempt")
                webView.postDelayed({ attemptAutofill(attempt + 1) }, 1_200L + attempt * 800L)
            }
        }
    }

    /** Direct host, or the WebVPN-wrapped form (…/https/<token>/…). */
    private fun candidateBases(pageUrl: String): List<String> {
        val result = LinkedHashSet<String>()
        runCatching { java.net.URI(pageUrl) }.getOrNull()?.let { uri ->
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return@let
            result.add("$scheme://$host")
            val path = uri.path.orEmpty()
            val marker = "/https/"
            val markerAt = path.indexOf(marker)
            if (markerAt >= 0) {
                val rest = path.substring(markerAt + marker.length)
                val token = rest.substringBefore('/')
                if (token.isNotBlank()) {
                    result.add("$scheme://$host$marker$token")
                }
            }
        }
        return result.toList()
    }

    /** After any page load, opportunistically capture when a session exists. */
    private fun maybeCapture(pageUrl: String) {
        if (pageUrl.contains("login")) return
        if (captured) return
        val raw = CookieManager.getInstance().getCookie(pageUrl).orEmpty()
        if (raw.isBlank()) return
        captured = true
        scope.launch {
            val candidates = candidateBases(pageUrl)
            var verified: Pair<String, String>? = null
            for (base in candidates) {
                val api = LocalJwxtApi(base, cookieHeader = { raw })
                val ok = withContext(Dispatchers.IO) {
                    runCatching { api.overview() }.isSuccess
                }
                if (ok) {
                    verified = base to raw
                    break
                }
            }
            if (verified != null) {
                val (base, cookie) = verified
                persistVerified(AppContainer.get(this@JwxtLoginActivity), base, cookie)
                Toast.makeText(this@JwxtLoginActivity, "教务已连接", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                captured = false
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    /**
     * Record credentials as the user TYPES, never at submit time: the CAS
     * page encrypts the password field in JS before submitting, so a submit
     * hook would read ciphertext. Capture-phase listeners survive pages that
     * stop propagation, and `change` covers autofill. Prefers user-ish fields
     * (学号/账号/user/…) over arbitrary text inputs like captchas; only pushes
     * once both a username and a password are known.
     */
    private fun injectCredentialHook() {
        webView.evaluateJavascript(CRED_HOOK_JS, null)
    }

    /** Cookie + base URL + remembered credentials, all through one door. */
    private suspend fun persistVerified(container: AppContainer, base: String, cookie: String) {
        container.adoptJwxtSession(cookie)
        if (rememberedUser.isNotBlank() && rememberedPass.isNotBlank()) {
            container.jwxtCredentialStore.save(
                top.sykesp42.rin.data.SessionCookie(name = rememberedUser.trim(), value = rememberedPass)
            )
        }
        runCatching { container.settings.setJwxtBaseUrl(base) }
        runCatching { container.settings.setJwxtConnectedAt(Instant.now().toString()) }
        runCatching { container.settings.setJwxtNeedsLogin(false) }
        container.noteJwxtHealthy()
        // Debug builds discover the student attendance page with this fresh
        // cookie; release builds have no receiver for the action.
        runCatching {
            sendBroadcast(
                Intent("top.sykesp42.rin.debug.PROBE_ATTENDANCE").setPackage(packageName)
            )
        }
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

    private fun trace(line: String) {
        runCatching {
            java.io.File(filesDir, "jwxt_trace.txt")
                .appendText(System.currentTimeMillis().toString() + " " + line + "\n")
        }
    }

    companion object {
        fun intent(context: Context, attendance: Boolean = false): Intent =
            Intent(context, JwxtLoginActivity::class.java)
                .putExtra(EXTRA_ATTENDANCE, attendance)

        private const val EXTRA_ATTENDANCE = "attendance_mode"
        private const val ATTENDANCE_URL = "https://wa.gdupt.edu.cn/"
        private const val DEFAULT_BASE = "https://jwxt.gdupt.edu.cn"
        private const val WEBVPN_ENTRY = "https://webvpn2.gdupt.edu.cn"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/124 SuiSign/0.1.0"

        /**
         * Injected on every page load: records typed 学号/密码 as the user
         * TYPES (submit-time reads see CAS-ciphertext), preferring user-ish
         * fields over captcha boxes; Android persists it on verify success.
         * Both input orders push (username-first and password-first), and an
         * unknown username falls back to scanning pre-filled user-ish fields.
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

        // SuiSign dark tokens (ui/theme/Tokens.kt).
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
