package top.sky2333.rin.data

import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class HourlyWeather(
    val time: String,
    val temperatureC: Int,
    val weather: PetWeather,
)

data class PetWeatherSnapshot(
    val weather: PetWeather,
    val temperatureC: Int?,
    val humidity: Int? = null,
    val fetchedAt: Long,
    val hourly: List<HourlyWeather> = emptyList(),
)

/**
 * Campus weather for the pet scenes. Coordinates are a fixed campus city, not
 * the device's, so this never needs a location permission or personal data.
 */
class PetWeatherRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cached: PetWeatherSnapshot? = null
    private var lastAttempt = 0L

    suspend fun current(force: Boolean = false): PetWeatherSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            cached?.let { if (now - it.fetchedAt < TTL_MS) return@withLock it }
            if (!force && now - lastAttempt < RETRY_MS) return@withLock cached
            lastAttempt = now
            val fresh = runCatching { fetch() }.getOrNull()
            if (fresh != null) cached = fresh
            cached
        }
    }

    private fun fetch(): PetWeatherSnapshot {
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", "Rin/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("weather http ${response.code}")
            val body = response.body?.string().orEmpty()
            val decoded = json.decodeFromString<OpenMeteo>(body)
            val current = decoded.current ?: error("weather body empty")
            val temperature = current.temperatureC
            val hourly = decoded.hourly?.let { forecast ->
                forecast.time.indices.mapNotNull { index ->
                    val value = forecast.temperatures.getOrNull(index) ?: return@mapNotNull null
                    val code = forecast.codes.getOrNull(index) ?: -1
                    HourlyWeather(
                        time = forecast.time[index],
                        temperatureC = value.roundToInt(),
                        weather = PetWeather.from(code, value),
                    )
                }
            }.orEmpty()
            return PetWeatherSnapshot(
                weather = PetWeather.from(current.weatherCode ?: -1, temperature),
                temperatureC = temperature?.roundToInt(),
                humidity = current.humidity?.roundToInt(),
                fetchedAt = System.currentTimeMillis(),
                hourly = hourly,
            )
        }
    }

    @Serializable
    private data class OpenMeteo(
        val current: Current? = null,
        val hourly: Hourly? = null,
    )

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperatureC: Double? = null,
        @SerialName("weather_code") val weatherCode: Int? = null,
        @SerialName("relative_humidity_2m") val humidity: Double? = null,
    )

    @Serializable
    private data class Hourly(
        val time: List<String> = emptyList(),
        @SerialName("temperature_2m") val temperatures: List<Double?> = emptyList(),
        @SerialName("weather_code") val codes: List<Int?> = emptyList(),
    )

    private companion object {
        const val ENDPOINT =
            "https://api.open-meteo.com/v1/forecast?latitude=21.6627&longitude=110.9256" +
                "&current=temperature_2m,relative_humidity_2m,weather_code" +
                "&hourly=temperature_2m,weather_code&forecast_days=2&timezone=Asia%2FShanghai"
        const val TTL_MS = 30 * 60_000L
        const val RETRY_MS = 2 * 60_000L
    }
}
