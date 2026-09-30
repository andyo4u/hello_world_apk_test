package app.quarterhour.core.paywall

import app.quarterhour.core.Fixtures
import app.quarterhour.core.news.RssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSourceFinderTest {
    private val finder = FreeSourceFinder(PaywallDetector())
    private val wsj = RssParser.parse(Fixtures.resource("google_news.xml"), "TOP")[0]

    @Test fun picksFreeSameStoryFromCluster() {
        val ranked = finder.rank(wsj, finder.fromCluster(wsj.related))
        // WSJ (same outlet + paywalled) and FT (paywalled, different story) are excluded.
        assertEquals(listOf("AP News"), ranked.map { it.sourceName })
    }

    @Test fun prefersWireServices() {
        val candidates = listOf(
            FreeSourceFinder.Candidate("Central bank holds rates steady as inflation cools", "https://a", "Local Daily", "localdaily.com"),
            FreeSourceFinder.Candidate("Central bank holds rates steady amid cooling inflation", "https://b", "Reuters", "reuters.com"),
        )
        assertEquals("Reuters", finder.rank(wsj, candidates).first().sourceName)
    }

    @Test fun rejectsDifferentStories() {
        val c = FreeSourceFinder.Candidate("Local team wins championship", "https://c", "Sports Site", "sports.example")
        assertTrue(finder.rank(wsj, listOf(c)).isEmpty())
    }

    @Test fun similarity() {
        assertTrue(TitleSimilarity.score("Central bank holds rates steady", "Central bank keeps rates steady") > 0.6)
        assertTrue(TitleSimilarity.score("Central bank holds rates steady", "Local team wins") == 0.0)
    }
}
