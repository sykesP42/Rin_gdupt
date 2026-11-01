package top.sykesp42.rin.data

import android.content.Context
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Device license service is independent from the main course/sign backend. */
const val LICENSE_BASE_URL = "https://www.sykesp.com/rin-license"

/**
 * Carrier SNI filtering still blocks the unfiled domain on some mobile
 * networks; the bare-IP entry (plain HTTP, host-locked in the network
 * security config) is the transport fallback. Only network-level failures
 * fall back — server answers (VERIFY_FAILED, throttling) must not.
 */
const val LICENSE_FALLBACK_URL = "http://123.56.99.154/rin-license"

class LicenseApiException(
    val code: String,
    override val message: String
) : IOException(message)

class LicenseIdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences("license_identity", Context.MODE_PRIVATE)

    @Synchronized
    fun installationId(): String {
        val existing = prefs.getString("installation_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("installation_id", created).apply()
        return created
    }

    fun secret(): String = prefs.getString("secret", null).orEmpty()

    fun saveSecret(value: String) {
        prefs.edit().putString("secret", value).apply()
    }
}

class LicenseApi(
    baseUrl: String = LICENSE_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    private val base = baseUrl.trimEnd('/')
    private val fallbackBase = LICENSE_FALLBACK_URL.trimEnd('/')
    private val media = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun apply(
        code: String,
        installationId: String,
        appVersion: String,
        label: String,
        device: Map<String, String>
    ): SignAccessRemote = post(
        "/api/license/apply",
        buildJsonObject {
            put("code", code)
            put("installationId", installationId)
            put("appVersion", appVersion)
            put("label", label)
            put("device", deviceJson(device))
        }
    )

    /**
     * 学号+姓名 grant: the standalone service checks its whitelist and either
     * activates the device or answers with the same generic failure as a bad
     * code, so the two grant paths are indistinguishable from outside.
     */
    suspend fun verify(
        studentId: String,
        realName: String,
        installationId: String,
        appVersion: String,
        label: String,
        device: Map<String, String>
    ): SignAccessRemote = post(
        "/api/license/verify",
        buildJsonObject {
            put("studentId", studentId)
            put("realName", realName)
            put("installationId", installationId)
            put("appVersion", appVersion)
            put("label", label)
            put("device", deviceJson(device))
        }
    )

    suspend fun status(
        installationId: String,
        secret: String,
        device: Map<String, String>
    ): SignAccessRemote = post(
        "/api/license/status",
        buildJsonObject {
            put("installationId", installationId)
            put("secret", secret)
            put("device", deviceJson(device))
        }
    )

    private fun deviceJson(device: Map<String, String>) = buildJsonObject {
        device.forEach { (key, value) -> put(key, value) }
    }

    private suspend fun post(path: String, body: JsonObject): SignAccessRemote {
        try {
            return postTo(base, path, body)
        } catch (e: LicenseApiException) {
            throw e
        } catch (e: IOException) {
            if (fallbackBase == base) throw e
            return postTo(fallbackBase, path, body)
        }
    }

    private suspend fun postTo(base: String, path: String, body: JsonObject): SignAccessRemote = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base$path")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "SuiSign/0.1.9 (android)")
            .post(body.toString().toRequestBody(media))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val error = root?.get("error") as? JsonObject
                val code = error?.get("code")?.jsonPrimitive?.contentOrNull.orEmpty()
                    .ifBlank { "HTTP_${response.code}" }
                val message = error?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
                    .ifBlank { "授权服务暂时不可用" }
                throw LicenseApiException(code, message)
            }
            val data = root?.get("data") as? JsonObject ?: JsonObject(emptyMap())
            json.decodeFromJsonElement<SignAccessRemote>(data)
        }
    }
}
