package app.quarterhour.core.news

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Parses Google News RSS into [Article]s. */
object RssParser {

    fun parse(xml: String, feedKey: String): List<Article> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("item").mapNotNull { item ->
            val rawTitle = item.selectFirst("title")?.text()?.trim().orEmpty()
            val link = item.selectFirst("link")?.text()?.trim().orEmpty()
            if (rawTitle.isEmpty() || link.isEmpty()) return@mapNotNull null
            val sourceEl = item.selectFirst("source")
            val sourceName = sourceEl?.text()?.trim().orEmpty()
            Article(
                id = item.selectFirst("guid")?.text()?.trim()?.ifEmpty { null } ?: link,
                title = stripSourceSuffix(rawTitle, sourceName),
                link = link,
                sourceName = sourceName,
                sourceUrl = sourceEl?.attr("url")?.ifEmpty { null },
                publishedAtMillis = parseDate(item.selectFirst("pubDate")?.text()),
                feedKey = feedKey,
                related = parseRelated(item.selectFirst("description")?.text().orEmpty()),
            )
        }
    }

    /** Google News titles end with " - Publisher"; drop it since the source is shown separately. */
    internal fun stripSourceSuffix(title: String, source: String): String {
        if (source.isEmpty()) return title
        val suffix = " - $source"
        return if (title.endsWith(suffix)) title.dropLast(suffix.length).trim() else title
    }

    /**
     * The description of a clustered story is an HTML list of other outlets'
     * coverage: `<ol><li><a href="…">Title</a>&nbsp;&nbsp;<font>Source</font></li>…</ol>`.
     */
    internal fun parseRelated(descriptionHtml: String): List<RelatedCoverage> {
        if (descriptionHtml.isBlank()) return emptyList()
        val html = Jsoup.parseBodyFragment(descriptionHtml)
        return html.select("li").mapNotNull { li ->
            val a = li.selectFirst("a[href]") ?: return@mapNotNull null
            RelatedCoverage(
                title = a.text().trim(),
                link = a.attr("href").trim(),
                sourceName = li.selectFirst("font")?.text()?.trim().orEmpty(),
            )
        }
    }

    private fun parseDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching {
            ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }
}
