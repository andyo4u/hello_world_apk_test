package app.quarterhour.core.news

import kotlinx.serialization.Serializable

/** Google News sections that can be followed. `id` is the RSS topic name. */
enum class Topic(val id: String, val label: String) {
    WORLD("WORLD", "World"),
    NATION("NATION", "Nation"),
    BUSINESS("BUSINESS", "Business"),
    TECHNOLOGY("TECHNOLOGY", "Technology"),
    ENTERTAINMENT("ENTERTAINMENT", "Entertainment"),
    SPORTS("SPORTS", "Sports"),
    SCIENCE("SCIENCE", "Science"),
    HEALTH("HEALTH", "Health"),
}

/** Another outlet covering the same story, as listed in a Google News cluster. */
@Serializable
data class RelatedCoverage(
    val title: String,
    val link: String,
    val sourceName: String,
)

@Serializable
data class Article(
    /** Stable id: the Google News guid, or the link when there is none. */
    val id: String,
    val title: String,
    /** Link as it appears in the feed (usually a news.google.com redirect). */
    val link: String,
    val sourceName: String,
    /** Publisher home page from the feed's `<source url=…>`, e.g. https://www.wsj.com */
    val sourceUrl: String?,
    val publishedAtMillis: Long,
    /** Topic id or "q:<keyword>" for keyword feeds. */
    val feedKey: String,
    val related: List<RelatedCoverage> = emptyList(),
) {
    val sourceDomain: String? get() = sourceUrl?.let { Domains.of(it) }
}

object Domains {
    /** Lower-cased host without a leading "www.", or null if the url has no host. */
    fun of(url: String): String? {
        val host = runCatching { java.net.URI(url.trim()).host }.getOrNull() ?: return null
        return host.lowercase().removePrefix("www.")
    }

    /** True when [host] equals [domain] or is a subdomain of it. */
    fun matches(host: String, domain: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        val d = domain.lowercase().removePrefix("www.")
        return h == d || h.endsWith(".$d")
    }
}
