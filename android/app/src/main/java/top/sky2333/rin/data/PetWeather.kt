package top.sky2333.rin.data

enum class PetWeather {
    UNKNOWN, CLEAR, CLOUDY, RAIN, SNOW, HOT, COLD;

    companion object {
        /** WMO weather codes from Open-Meteo; temperature drives hot/cold scenes. */
        fun from(code: Int, temperatureC: Double?): PetWeather = when {
            code in 71..77 || code in 85..86 -> SNOW
            code in 51..67 || code in 80..82 || code in 95..99 -> RAIN
            temperatureC != null && temperatureC >= 33.0 -> HOT
            temperatureC != null && temperatureC <= 8.0 -> COLD
            code == 0 -> CLEAR
            code in 1..3 || code == 45 || code == 48 -> CLOUDY
            else -> UNKNOWN
        }
    }
}
