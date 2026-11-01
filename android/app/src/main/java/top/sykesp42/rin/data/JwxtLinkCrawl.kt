package top.sykesp42.rin.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/** A same-origin link found in a JWXT page, with query values stripped. */
data class JwxtDiscoveredLink(
    val label: String,
    val relativePath: String,
    val url: String = ""
)

/**
 * Read-only discovery of JWXT menu links. The WebVPN base path is removed so
 * reports never contain the wrapper token; all query values are dropped so a
 * ticket/session can never be persisted.
 */
object JwxtLinkCrawl {

    val KEYWORDS = listOf("考勤", "签到", "出勤", "缺勤", "旷课", "请假", "attendance", "kq")

    private val ONCLICK = Regex(
        """(?:location(?:\.href)?|window\.open)\s*=\s*['"]([^'"]+)['"]"""
    )
    private val INLINE_ACTION = Regex("""["']([^"'\s]+?\.action[^"'\s]*)["']""")

    fun extract(
        pageUrl: String,
        baseUrl: String,
        html: String,
        limit: Int = 200
    ): List<JwxtDiscoveredLink> {
        val base = baseUrl.toHttpUrlOrNull() ?: return emptyList()
        val page = pageUrl.toHttpUrlOrNull() ?: base
        val basePath = base.encodedPath.trimEnd('/')
        val found = LinkedHashMap<String, JwxtDiscoveredLink>()

        fun add(raw: String?, label: String?) {
            if (found.size >= limit) return
            val href = raw?.trim().orEmpty()
            if (href.isEmpty() || href.startsWith("#")) return
            if (href.startsWith("javascript:", true) || href.startsWith("mailto:", true)) return
            // Root-relative links must stay under the WebVPN base path, while
            // an already-wrapped /https/<token>/ path must not be prefixed again.
            val resolved = when {
                href.startsWith("/https/") -> page.resolve(href) ?: return
                href.startsWith("/") && !href.startsWith("//") ->
                    (baseUrl.trimEnd('/') + href).toHttpUrlOrNull() ?: return
                else -> page.resolve(href) ?: return
            }
            if (resolved.host != base.host) return
            val path = resolved.encodedPath
            if (path != basePath && !path.startsWith("$basePath/")) return
            val relative = path.removePrefix(basePath).ifBlank { "/" }
            val keys = resolved.queryParameterNames
            val relativePath =
                if (keys.isEmpty()) relative else "$relative?" + keys.joinToString("&")
            val text = label?.trim()?.takeIf { it.isNotEmpty() } ?: relativePath
            found.putIfAbsent(relativePath, JwxtDiscoveredLink(text, relativePath, resolved.toString()))
        }

        val document = Jsoup.parse(html, pageUrl)
        document.select("a[href]").forEach { a ->
            add(a.attr("href"), a.text().ifBlank { a.attr("title") })
        }
        document.select("iframe[src], frame[src]").forEach { frame ->
            add(frame.attr("src"), frame.attr("title").ifBlank { "iframe" })
        }
        ONCLICK.findAll(html).forEach { add(it.groupValues[1], null) }
        INLINE_ACTION.findAll(html).forEach { add(it.groupValues[1], "js") }
        return found.values.take(limit)
    }

    fun interesting(
        links: List<JwxtDiscoveredLink>,
        keywords: List<String> = KEYWORDS
    ): List<JwxtDiscoveredLink> = links.filter { link ->
        keywords.any {
            link.label.contains(it, ignoreCase = true) ||
                link.relativePath.contains(it, ignoreCase = true)
        }
    }
}
