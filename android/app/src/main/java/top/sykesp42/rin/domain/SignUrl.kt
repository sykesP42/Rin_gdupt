package top.sykesp42.rin.domain

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Mirror of contracts.ValidateSignURL: only the exact school endpoint, http,
 * one non-empty data param, no userinfo, no fragment. Anything else must never
 * be handed to WeChat.
 */
object SignUrl {
    const val HOST = "class.gdupt.edu.cn"
    const val PATH = "/signApi/api/gdupt/SignActivity/signDispactch"

    fun isValid(raw: String): Boolean {
        val url = raw.trim().toHttpUrlOrNull() ?: return false
        if (url.scheme != "http" || url.host != HOST) return false
        if (url.encodedPath != PATH) return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        if (url.fragment != null) return false
        if (url.queryParameterNames.size != 1) return false
        if (url.queryParameterNames.first() != "data") return false
        val values = url.queryParameterValues("data")
        return values.size == 1 && values.singleOrNull()?.isNotEmpty() == true
    }
}
