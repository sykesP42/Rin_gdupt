package top.sykesp42.rin.data

import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The school's attendance-statistics system (wa.gdupt.edu.cn). Its read APIs
 * need the JWT minted by CAS → /v1/sso/toLogin; the token itself lives in the
 * page's sessionStorage under "t".
 *
 * Only the authenticated probe/verification lives here for now: the official
 * absence schema still has to be observed from a real session before it can
 * feed the ledger. No guessed fields.
 */
class WaAttendanceApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(20))
        .build()
) {
    data class Session(val userId: String, val token: String)

    private val json = Json { ignoreUnknownKeys = true }

    /** USER_ID claim from the JWT payload; empty when malformed. */
    fun decodeUserId(token: String): String = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return ""
        val decoded = String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        Regex("\"USER_ID\"\\s*:\\s*\"?(\\d+)\"?").find(decoded)?.groupValues?.getOrNull(1).orEmpty()
    }.getOrDefault("")

    fun verify(session: Session): Boolean = runCatching {
        get("/v1/sso/loginUserInfo", session).let { body ->
            json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content == "200"
        }
    }.getOrDefault(false)

    /**
     * Read the user's absence page and return a redacted shape summary:
     * status, top-level keys, record count and per-record keys. Values (names,
     * dates) are deliberately not included so the trace stays safe.
     */
    fun probeAbsence(session: Session): String = runCatching {
        val body = get("/v1/data-statistics/attendance/user/absence?current=1&size=50", session)
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return "unparseable len=${body.length}"
        val data = root["data"]
        val summary = StringBuilder("code=${root["code"]} keys=${root.keys}")
        when (data) {
            is JsonObject -> {
                summary.append(" data.keys=${data.keys}")
                val records = data["records"] as? JsonArray
                summary.append(" records=${records?.size ?: -1}")
                (records?.firstOrNull() as? JsonObject)?.let { first ->
                    summary.append(" record.keys=${first.keys}")
                }
            }
            is JsonArray -> {
                summary.append(" data.array=${data.size}")
                (data.firstOrNull() as? JsonObject)?.let { first ->
                    summary.append(" record.keys=${first.keys}")
                }
            }
            else -> summary.append(" data=${data?.javaClass?.simpleName}")
        }
        summary.toString()
    }.getOrElse { "probe failed: ${it.javaClass.simpleName}: ${it.message?.take(120)}" }

    private fun get(path: String, session: Session): String {
        val request = Request.Builder()
            .url(BASE_URL + path)
            .header("schoolId", SCHOOL_ID)
            .header("token", session.token)
            .header("userId", session.userId)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        const val BASE_URL = "https://wa.gdupt.edu.cn"
        const val SCHOOL_ID = "11656"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/124 SuiSign/0.1.0"
    }
}
