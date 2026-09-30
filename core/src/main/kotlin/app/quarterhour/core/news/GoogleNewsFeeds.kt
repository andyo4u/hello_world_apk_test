package app.quarterhour.core.news

import java.net.URLEncoder

/** Builds Google News RSS URLs for a region, e.g. `GoogleNewsFeeds(language = "en", country = "US")`. */
class GoogleNewsFeeds(
    private val language: String = "en",
    private val country: String = "US",
) {
    private val params: String
        get() = "hl=$language-$country&gl=$country&ceid=$country:$language"

    fun topStories(): String = "$BASE/rss?$params"

    fun topic(topic: Topic): String = "$BASE/rss/headlines/section/topic/${topic.id}?$params"

    fun search(query: String): String =
        "$BASE/rss/search?q=${URLEncoder.encode(query, "UTF-8")}&$params"

    companion object {
        const val BASE = "https://news.google.com"
    }
}
