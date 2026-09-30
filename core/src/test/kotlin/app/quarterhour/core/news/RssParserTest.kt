package app.quarterhour.core.news

import app.quarterhour.core.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {
    private val items = RssParser.parse(Fixtures.resource("google_news.xml"), "TOP")

    @Test fun parsesItemsAndStripsPublisherSuffix() {
        assertEquals(2, items.size)
        assertEquals("Central bank holds rates steady as inflation cools", items[0].title)
        assertEquals("The Wall Street Journal", items[0].sourceName)
        assertEquals("wsj.com", items[0].sourceDomain)
        assertEquals("CBMiAAA", items[0].id)
        assertEquals("TOP", items[0].feedKey)
        assertTrue(items[0].publishedAtMillis > 0)
    }

    @Test fun parsesClusterCoverage() {
        val related = items[0].related
        assertEquals(3, related.size)
        assertEquals("AP News", related[1].sourceName)
        assertEquals("https://news.google.com/rss/articles/CBMiCCC?oc=5", related[1].link)
        assertTrue(items[1].related.isEmpty())
    }

    @Test fun feedUrls() {
        val f = GoogleNewsFeeds("en", "GB")
        assertEquals("https://news.google.com/rss/headlines/section/topic/SCIENCE?hl=en-GB&gl=GB&ceid=GB:en", f.topic(Topic.SCIENCE))
        assertEquals("https://news.google.com/rss/search?q=electric+cars&hl=en-GB&gl=GB&ceid=GB:en", f.search("electric cars"))
    }
}
