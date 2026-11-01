package top.sykesp42.rin.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * GDUPT CAS login, reverse-engineered from the authserver page (2026-10-28).
 *
 * The WebVPN wrap is the only route to JWXT off campus, and its ticket dies
 * after a few hours. This client re-establishes that WebVPN session with the
 * user's own stored credentials when the normal cookie renewal can no longer
 * work. It never bypasses a captcha: if the school asks for one, callers must
 * send the user back to the embedded login.
 */
class CasClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .readTimeout(java.time.Duration.ofSeconds(20))
        .dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> =
                okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it.address.size }
        })
        .build()
) {
    class CasCaptchaRequired(message: String = "教务登录需要验证码，请在网页里登录一次") : Exception(message)
    class CasRejected(message: String) : Exception(message)

    data class WebVpnSession(val cookieHeader: String)

    /**
     * Full CAS round trip for `webvpn2.gdupt.edu.cn`: login form → encrypted
     * password POST → redirect back to the portal → WebVPN cookies.
     */
    fun loginWebVpn(username: String, password: String): WebVpnSession {
        val jar = MemoryCookieJar()
        val service = WEBVPN_CAS_SERVICE
        val loginUrl = "$CAS_BASE/login?service=" + urlEncode(service)
        val page = execute(
            Request.Builder().url(loginUrl).header("User-Agent", USER_AGENT).get().build(),
            jar
        )
        val pageUrl = page.request.url.toString()
        val pageBody = page.use { it.body?.string().orEmpty() }
        if (!pageBody.contains("casLoginForm", ignoreCase = true)) {
            // Already authenticated: CAS redirects straight to the service.
            if (pageUrl.contains("ticket=") || pageUrl.startsWith(WEBVPN_BASE)) {
                return session(jar)
            }
            throw CasRejected("CAS 登录页结构变了")
        }
        val pageInfo = inspectCasPage(pageBody)
        if (pageInfo.salt.isBlank()) throw CasRejected("CAS 缺少加密盐")
        // The school enables a slider captcha on this server; solving it
        // programmatically would bypass a human check, so stop here and let
        // the embedded login take over.
        if (pageInfo.slider) throw CasCaptchaRequired()
        val salt = pageInfo.salt
        val doc = Jsoup.parse(pageBody, pageUrl)
        val form = doc.selectFirst("#casLoginForm") ?: throw CasRejected("CAS 缺少登录表单")
        val action = form.absUrl("action").ifBlank { loginUrl }
        val fields = linkedMapOf<String, String>()
        form.select("input").forEach { input ->
            val name = input.attr("name")
            if (name.isNotBlank()) fields[name] = input.attr("value")
        }
        fields["username"] = username
        fields["password"] = encryptPassword(password, salt, newIv())
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> if (key.isNotBlank()) add(key, value) }
        }.build()
        val posted = execute(
            Request.Builder().url(action)
                .header("User-Agent", USER_AGENT)
                .header("Referer", pageUrl)
                .post(body)
                .build(),
            jar
        )
        val finalUrl = posted.request.url.toString()
        val finalBody = posted.use { it.body?.string().orEmpty() }
        if (finalUrl.contains("/authserver/") || finalBody.contains("casLoginForm", ignoreCase = true)) {
            if (CAPTCHA_HINT.containsMatchIn(finalBody) || finalBody.contains("captcha", ignoreCase = true)) {
                throw CasCaptchaRequired()
            }
            throw CasRejected("CAS 登录失败：账号或密码不正确，或需要人工验证")
        }
        return session(jar)
    }

    private fun session(jar: MemoryCookieJar): WebVpnSession {
        val header = jar.headerFor(WEBVPN_BASE.toHttpUrl())
        if (header.isBlank()) throw CasRejected("CAS 成功后仍没有 WebVPN 会话")
        if (!header.contains("wengine_vpn_ticket", ignoreCase = true)) {
            throw CasRejected("WebVPN 会话 Cookie 缺失")
        }
        return WebVpnSession(header)
    }

    private fun execute(request: Request, jar: CookieJar): okhttp3.Response =
        client.newBuilder().cookieJar(jar).build()
            .newCall(request)
            .execute()

    /** Same order/format as authserver's encrypt.js: AES-CBC-PKCS7(random64+pwd). */
    internal fun encryptPassword(password: String, salt: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        )
        val plain = randomChars(64) + password
        return java.util.Base64.getEncoder()
            .encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    private fun newIv(): String = randomChars(16)

    /** Authserver page facts we depend on; kept pure for fixture tests. */
    internal data class CasPage(val salt: String, val slider: Boolean)

    internal fun inspectCasPage(html: String): CasPage = CasPage(
        salt = SALT_INPUT.find(html)?.groupValues?.getOrNull(1)
            ?: SALT_SCRIPT.find(html)?.groupValues?.getOrNull(1)
            ?: "",
        slider = SLIDER_ENABLED.containsMatchIn(html) || SLIDER_FIELD.containsMatchIn(html)
    )

    private fun randomChars(length: Int): String = buildString {
        repeat(length) { append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)]) }
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private class MemoryCookieJar : CookieJar {
        private val cookies = linkedMapOf<String, Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { cookie -> this.cookies[key(cookie)] = cookie }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.values.filter { it.matches(url) }

        fun headerFor(url: HttpUrl): String =
            loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }

        private fun key(cookie: Cookie): String =
            "${cookie.name}|${cookie.domain}|${cookie.path}"
    }

    companion object {
        const val CAS_BASE = "https://authserver.gdupt.edu.cn/authserver"
        const val WEBVPN_BASE = "https://webvpn2.gdupt.edu.cn/"
        const val WEBVPN_CAS_SERVICE = "https://webvpn2.gdupt.edu.cn/login?cas_login=true"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/124 SuiSign/0.1.0"
        private val SALT_INPUT = Regex(
            """id=["']pwdDefaultEncryptSalt["'][^>]*value=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val SALT_SCRIPT = Regex(
            """var\s+pwdDefaultEncryptSalt\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val SLIDER_ENABLED = Regex(
            """id=["']isSliderCaptcha["'][^>]*value=["']true["']""",
            RegexOption.IGNORE_CASE
        )
        private val SLIDER_FIELD = Regex("""id=["']sliderCaptchaDynamicCode["']""", RegexOption.IGNORE_CASE)
        private val CAPTCHA_HINT = Regex("验证码|滑块|captcha", RegexOption.IGNORE_CASE)
        private const val RANDOM_CHARS =
            "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
        private val random = SecureRandom()
    }
}
