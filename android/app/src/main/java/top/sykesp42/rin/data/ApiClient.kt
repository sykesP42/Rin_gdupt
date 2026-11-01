package top.sykesp42.rin.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ApiError(val status: Int, val code: String, override val message: String) : IOException(message)

data class User(val id: String, val username: String, val displayName: String, val role: String)

/**
 * OkHttp client for the existing gdupt-sign-up API. Keeps the session cookie in
 * a SessionStore and sends the Origin header the server's CSRF rule requires.
 */
class ApiClient(
    baseUrl: String,
    private val store: SessionStore,
    http: OkHttpClient = OkHttpClient()
) : MonitorApi {
    private val base = baseUrl.trimEnd('/')
    private val baseHttp: HttpUrl = base.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("invalid server url")
    private val origin: String = buildString {
        append(baseHttp.scheme).append("://").append(baseHttp.host)
        val defaultPort = if (baseHttp.scheme == "https") 443 else 80
        if (baseHttp.port != defaultPort) append(':').append(baseHttp.port)
    }
    private val client = http.newBuilder().cookieJar(PersistingCookieJar(store)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    suspend fun login(username: String, password: String): User = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val request = requestBuilder("$base/api/v1/auth/login")
            .post(payload.toString().toRequestBody(mediaJson))
            .build()
        call(request) { parseUser(it) }
    }

    suspend fun me(): User = withContext(Dispatchers.IO) {
        call(requestBuilder("$base/api/v1/auth/me").get().build()) { parseUser(it) }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching {
            call(requestBuilder("$base/api/v1/auth/logout").post("{}".toRequestBody(mediaJson)).build()) { Unit }
        }
        store.clear()
    }

    override suspend fun recommendation(): ApiRecommendation =
        getData("/api/v1/courses/recommendation")

    override suspend fun course(code: String): ApiCourse =
        getData("/api/v1/courses/${pathSegment(code)}")

    override suspend fun selfSigned(code: String): Boolean {
        val roster = roster(code)
        return roster.signed.any { it.isCurrentUser }
    }

    override suspend fun roster(code: String): ApiRoster =
        getData("/api/v1/courses/${pathSegment(code)}/roster")

    override suspend fun qrPng(code: String): ByteArray = qrCode(code)

    override suspend fun schedule(): ApiScheduleOverview =
        getData("/api/v1/jwxt/schedule/overview")

    suspend fun jwxtAccount(): ApiJwxtAccount =
        getData("/api/v1/jwxt/account")

    suspend fun signSettings(): ApiSignSettings =
        getData("/api/v1/sign/settings")

    suspend fun signAccess(): SignAccessRemote =
        getData("/api/v1/sign/access")

    suspend fun applySignAccess(code: String, appVersion: String): SignAccessRemote =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("code", code)
                put("appVersion", appVersion)
            }
            val request = requestBuilder("$base/api/v1/sign/access/apply")
                .post(payload.toString().toRequestBody(mediaJson))
                .build()
            call(request) { response ->
                val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val data = root["data"] as? JsonObject ?: JsonObject(emptyMap())
                json.decodeFromJsonElement<SignAccessRemote>(data)
            }
        }

    suspend fun signURL(code: String): String = withContext(Dispatchers.IO) {
        val path = "/api/v1/courses/${pathSegment(code)}/sign-url"
        call(requestBuilder("$base$path").get().build()) { response ->
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val data = root["data"] as? JsonObject ?: JsonObject(emptyMap())
            data["redirectUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
    }

    /** Fresh dynamic sign QR image bytes, minted at request time (threshold-enforced). */
    suspend fun qrCode(code: String): ByteArray = withContext(Dispatchers.IO) {
        val path = "/api/v1/courses/${pathSegment(code)}/qr"
        call(requestBuilder("$base$path").get().build()) { response ->
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    suspend fun rooms(query: String): List<ApiRoom> = withContext(Dispatchers.IO) {
        val path = "/api/v1/rooms?query=" + java.net.URLEncoder.encode(query, "UTF-8")
        call(requestBuilder("$base$path").get().build()) { response ->
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val data = root["data"] as? JsonObject ?: root
            json.decodeFromJsonElement<ApiRoomList>(data).items
        }
    }

    suspend fun setThreshold(threshold: Int, confirmation: String): Int = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("rosterThreshold", threshold)
            if (confirmation.isNotBlank()) put("confirmation", confirmation)
        }
        val request = requestBuilder("$base/api/v1/sign/settings")
            .patch(payload.toString().toRequestBody(mediaJson))
            .build()
        call(request) { response ->
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val data = root["data"] as? JsonObject ?: JsonObject(emptyMap())
            data["rosterThreshold"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: threshold
        }
    }

    private suspend inline fun <reified T> getData(path: String): T = withContext(Dispatchers.IO) {
        call(requestBuilder("$base$path").get().build()) { response ->
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            json.decodeFromJsonElement<T>(root["data"] ?: JsonObject(emptyMap()))
        }
    }

    private fun pathSegment(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun requestBuilder(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "SuiSign/0.1.0 (android)")
            .header("Origin", origin)

    private fun <T> call(request: Request, parse: (Response) -> T): T {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val error = runCatching {
                    json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                }.getOrNull()
                val code = error?.get("code")?.jsonPrimitive?.contentOrNull ?: "HTTP_${response.code}"
                val message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "请求失败"
                throw ApiError(response.code, code, message)
            }
            return parse(response)
        }
    }

    private fun parseUser(response: Response): User {
        val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
        val data = root["data"] as? JsonObject ?: JsonObject(emptyMap())
        return User(
            id = data.str("id"),
            username = data.str("username"),
            displayName = data.str("displayName"),
            role = data.str("role")
        )
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: ""
}

class PersistingCookieJar(private val store: SessionStore) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.firstOrNull { it.name == "session" || it.name == "__Host-session" }
            ?.let { store.save(SessionCookie(it.name, it.value)) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookie = store.load() ?: return emptyList()
        val builder = Cookie.Builder()
            .name(cookie.name)
            .value(cookie.value)
            .domain(url.host)
            .path("/")
        if (url.isHttps) builder.secure()
        return listOf(builder.build())
    }
}
