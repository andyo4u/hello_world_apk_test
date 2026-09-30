package app.quarterhour.core.news

import app.quarterhour.core.Fixtures
import app.quarterhour.core.filter.AiContentDetector
import app.quarterhour.core.filter.ClickbaitDetector
import app.quarterhour.core.filter.FilterReason
import app.quarterhour.core.net.HttpResponse
import app.quarterhour.core.paywall.PaywallDetector
import app.quarterhour.core.profile.InterestModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsPipelineTest {
    private val now = 1_790_000_000_000L
    private val feeds = GoogleNewsFeeds()

    private fun item(id: String, title: String, source: String, domain: String, related: String = "") = """
        <item><title>$title - $source</title><link>https://news.google.com/rss/articles/$id</link><guid>$id</guid>
        <pubDate>Tue, 29 Sep 2026 14:00:00 GMT</pubDate><description>${related}</description>
        <source url="https://$domain">$source</source></item>"""

    private fun rss(vararg items: String) = "<rss><channel>${items.joinToString("")}</channel></rss>"

    private val clusterHtml = "&lt;ol&gt;&lt;li&gt;&lt;a href=\"https://news.google.com/rss/articles/AP1\"&gt;Central bank keeps rates steady as inflation cools&lt;/a&gt;&lt;font&gt;AP News&lt;/font&gt;&lt;/li&gt;&lt;/ol&gt;"

    private val topFeed = rss(
        item("WSJ1", "Central bank holds rates steady as inflation cools", "The Wall Street Journal", "www.wsj.com", clusterHtml),
        item("PARK", "City council approves riverside park expansion", "Example Times", "example.com"),
        item("BAIT", "You won't believe this shocking park secret!!", "Bait Site", "bait.example"),
        item("PW", "Stadium deal reaches final vote", "Metro Paper", "metro.example"),
        item("AIX", "Guide to the city's new transit card", "Content Farm", "farm.example"),
        item("DEAD", "Bridge repair schedule announced", "Broken Site", "broken.example"),
    )

    private val resolved = mapOf(
        "PARK" to "https://example.com/park",
        "AP1" to "https://apnews.com/article/rates",
        "PW" to "https://metro.example/stadium",
        "AIX" to "https://farm.example/transit",
        "DEAD" to "https://broken.example/bridge",
    )

    private val pages = mapOf(
        "https://example.com/park" to Fixtures.page(),
        "https://apnews.com/article/rates" to Fixtures.page(title = "Central bank keeps rates steady as inflation cools"),
        "https://metro.example/stadium" to Fixtures.page(title = "Stadium deal", head = """<script src="https://cdn.tinypass.com/api/tp.js"></script>"""),
        "https://farm.example/transit" to Fixtures.page(title = "Transit card", body = "<p>As an AI language model, I cannot provide current fares.</p>" + Fixtures.body()),
    )

    private val io = PipelineIo(
        fetch = { url ->
            when {
                url == feeds.topStories() -> HttpResponse(200, topFeed, url, null)
                url.contains("/rss/search") -> HttpResponse(200, rss(), url, null)
                url in pages -> HttpResponse(200, pages.getValue(url), url, null)
                else -> HttpResponse(500, "", url, null)
            }
        },
        resolveLink = { link -> resolved[link.substringAfterLast('/')] },
    )

    private val pipeline = NewsPipeline(
        io, feeds, PaywallDetector(), ClickbaitDetector(), AiContentDetector(knownFarms = emptySet()),
    )

    @Test fun buildsCleanFeedAndExplainsWhatWasFiltered() = runBlocking {
        val result = pipeline.build(InterestModel(), now)

        val shown = result.items.associateBy { it.article.title }
        assertEquals(2, result.items.size)
        assertTrue("City council approves riverside park expansion" in shown)

        // WSJ story replaced by the AP version of the same story (method 2).
        val rates = result.items.single { it.substitutedFrom != null }
        assertEquals("The Wall Street Journal", rates.substitutedFrom)
        assertEquals("AP News", rates.article.sourceName)
        assertEquals("https://apnews.com/article/rates", rates.reader.url)

        val reasons = result.filtered.associate { it.title to it.reason }
        assertEquals(FilterReason.CLICKBAIT, reasons["You won't believe this shocking park secret!!"])
        assertEquals(FilterReason.PAYWALL, reasons["Stadium deal reaches final vote"])
        assertEquals(FilterReason.AI_CONTENT, reasons["Guide to the city's new transit card"])
        assertEquals(FilterReason.UNREADABLE, reasons["Bridge repair schedule announced"])
    }

    @Test fun readerCopyHasNoAdsButKeepsPhotos() = runBlocking {
        val park = pipeline.build(InterestModel(), now).items.single { it.article.id == "PARK" }
        assertTrue(park.reader.imageUrls.contains("https://cdn.example.com/park.jpg"))
        assertTrue(park.reader.imageUrls.none { "doubleclick" in it })
        assertNull(park.substitutedFrom)
    }

    @Test fun blockedSourcesAreFiltered() = runBlocking {
        val result = pipeline.build(InterestModel().blockSource("Example Times"), now)
        assertTrue(result.items.none { it.article.sourceName == "Example Times" })
        assertTrue(result.filtered.any { it.reason == FilterReason.BLOCKED_SOURCE })
    }

    @Test fun subscriptionUnlocksPublisher() = runBlocking {
        val subscribed = NewsPipeline(
            PipelineIo(
                fetch = { url ->
                    when (url) {
                        feeds.topStories() -> HttpResponse(200, rss(item("NYT", "Museum reopens after renovation", "The New York Times", "www.nytimes.com")), url, null)
                        "https://www.nytimes.com/museum" -> HttpResponse(200, Fixtures.page(title = "Museum reopens"), url, null)
                        else -> HttpResponse(404, "", url, null)
                    }
                },
                resolveLink = { "https://www.nytimes.com/museum" },
            ),
            feeds, PaywallDetector(subscribedDomains = setOf("nytimes.com")), ClickbaitDetector(), AiContentDetector(),
        )
        val result = subscribed.build(InterestModel(), now)
        assertEquals("The New York Times", result.items.single().article.sourceName)
    }

    @Test fun mergesDuplicateStories() {
        val a = Article("1", "Central bank holds rates steady", "l1", "A", null, 0, "TOP")
        val b = Article("2", "Central bank holds rates steady again", "l2", "B", null, 0, "BUSINESS")
        val merged = NewsPipeline.mergeDuplicates(listOf(a, b))
        assertEquals(1, merged.size)
        assertEquals("l2", merged[0].related.single().link)
    }
}
