package com.masterchief.agent.brain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Real weather from Open-Meteo (https://open-meteo.com) — free, no API key.
 * If the network or the service fails, fetch() returns a failure and the
 * UI says "unavailable" instead of showing an invented temperature.
 */
@Serializable
data class OpenMeteoResp(val current: CurrentWeather? = null)

@Serializable
data class CurrentWeather(
    @SerialName("temperature_2m") val temperature: Double = 0.0,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("weather_code") val code: Int = 0,
    @SerialName("wind_speed_10m") val windKmh: Double? = null
)

data class WeatherNow(
    val place: String,
    val tempC: Double,
    val humidity: Int?,
    val windKmh: Double?,
    val description: String
)

object WeatherClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Lagos, Nigeria (Tayo's home base). Used as the default; the briefing
    // labels it as Lagos so nobody mistakes it for live GPS weather.
    const val LAGOS_LAT = 6.5244
    const val LAGOS_LON = 3.3792

    fun describe(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Weather code $code"
    }

    fun fetch(place: String, lat: Double, lon: Double): Result<WeatherNow> = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Weather service returned HTTP ${resp.code}.")
            }
            val parsed = json.decodeFromString<OpenMeteoResp>(raw)
            val cur = parsed.current
                ?: throw IllegalStateException("Weather service returned no current reading.")
            WeatherNow(place, cur.temperature, cur.humidity, cur.windKmh, describe(cur.code))
        }
    }
}
