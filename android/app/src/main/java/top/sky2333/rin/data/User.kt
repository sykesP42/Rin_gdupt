package top.sky2333.rin.data

/** A local display identity; the app has no account system. */
data class User(
    val username: String = "",
    val displayName: String = ""
)
