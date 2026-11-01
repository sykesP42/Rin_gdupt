package top.sykesp42.rin.data

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/** A campus news item from the school's public news list. */
@Serializable
data class CampusNewsItem(
    val title: String,
    val url: String,
    val date: String
)

private val NEWS_DATE = Regex("""\d{4}-\d{1,2}-\d{1,2}""")

/**
 * Parse the school news list. The featured row carries `p.list-date`; ordinary
 * rows carry a bare `<span>` date. Both use an `info/…` detail link. The date is
 * the publish date, not necessarily the event date.
 */
internal fun parseCampusNews(html: String, baseUrl: String): List<CampusNewsItem> {
    val base = baseUrl.toHttpUrlOrNull() ?: return emptyList()
    val document = Jsoup.parse(html)
    val out = LinkedHashMap<String, CampusNewsItem>()
    for (li in document.select("li")) {
        val featured = li.selectFirst("p.list-date")?.text()?.trim()
        val date = if (!featured.isNullOrEmpty()) {
            featured
        } else {
            li.select("span").firstNotNullOfOrNull { span ->
                span.text().trim().takeIf { NEWS_DATE.matches(it) }
            }
        } ?: continue
        val anchor = li.select("a[href]").firstOrNull { link ->
            val href = link.attr("href")
            val text = link.text().trim()
            href.contains("info/") && text.isNotEmpty() && !text.contains("详情")
        } ?: continue
        val url = base.resolve(anchor.attr("href"))?.toString() ?: continue
        out.putIfAbsent(url, CampusNewsItem(title = anchor.text().trim(), url = url, date = date))
    }
    return out.values.toList()
}

internal fun groupNewsByDate(items: List<CampusNewsItem>): Map<LocalDate, List<CampusNewsItem>> =
    items.mapNotNull { item ->
        runCatching { LocalDate.parse(item.date) }.getOrNull()?.let { it to item }
    }.groupBy({ it.first }, { it.second })

/**
 * Reads the school's public 广油要闻 list. The first page is `gyyw.htm`; older
 * pages are numbered backwards (`gyyw/35.htm` is page 2 of 36), so a few pages
 * are enough to cover the calendar's recent months. No login, no personal data.
 */
class CampusNewsFetcher(
    private val client: OkHttpClient,
    private val listUrl: String = LIST_URL,
    private val pages: Int = 3
) {
    suspend fun fetch(): List<CampusNewsItem> = withContext(Dispatchers.IO) {
        val (firstHtml, firstItems) = fetchPage(listUrl)
        if (firstItems.isEmpty()) return@withContext emptyList()
        val merged = LinkedHashMap<String, CampusNewsItem>()
        firstItems.forEach { merged.putIfAbsent(it.url, it) }
        olderPageUrls(firstHtml, listUrl, pages).forEach { url ->
            runCatching { fetchPage(url) }.getOrNull()?.let { (_, items) ->
                items.forEach { merged.putIfAbsent(it.url, it) }
            }
        }
        merged.values.toList()
    }

    private fun fetchPage(url: String): Pair<String, List<CampusNewsItem>> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,*/*")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("校园要闻不可达 HTTP ${response.code}")
            }
            val html = response.body?.string().orEmpty()
            return html to parseCampusNews(html, url)
        }
    }

    companion object {
        const val LIST_URL = "https://site.gdupt.edu.cn/news/gyyw.htm"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 SuiSign/0.1.0"
    }
}

/**
 * Older list pages from the pagination links. The CMS numbers them backwards,
 * so page 2 is the highest number and page 3 is one lower. Exposed for tests.
 */
internal fun olderPageUrls(html: String, listUrl: String, pages: Int): List<String> {
    if (pages <= 1) return emptyList()
    val base = listUrl.toHttpUrlOrNull() ?: return emptyList()
    val highest = Jsoup.parse(html).select("a[href]").mapNotNull { link ->
        Regex("""gyyw/(\d+)\.htm""").find(link.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull()
    }.maxOrNull() ?: return emptyList()
    return (0 until pages - 1).mapNotNull { offset ->
        (highest - offset).takeIf { it >= 1 }
    }.mapNotNull { number -> base.resolve("gyyw/$number.htm")?.toString() }
}
