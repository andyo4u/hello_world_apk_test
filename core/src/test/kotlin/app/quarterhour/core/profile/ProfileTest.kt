package app.quarterhour.core.profile

import app.quarterhour.core.filter.FilterReason
import app.quarterhour.core.news.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class InterestModelTest {
    private val t0 = 1_790_000_000_000L
    private val day = 24L * 3600 * 1000

    @Test fun signalsMoveScores() {
        var m = InterestModel()
        m = m.record(Signal.Opened, "SCIENCE", "NASA", "Telescope images reveal galaxy formation", t0)
        m = m.record(Signal.ThumbUp, "SCIENCE", "NASA", "Mars rover finds ancient riverbed", t0)
        m = m.record(Signal.ThumbDown, "SPORTS", "Sports Daily", "Team wins match", t0)
        assertEquals(4.0, m.topicScore("SCIENCE", t0), 1e-9)
        assertEquals(-3.0, m.topicScore("SPORTS", t0), 1e-9)
        assertTrue(m.termScore("telescope", t0) > 0)
        assertEquals(3, m.interactions)
    }

    @Test fun scoresDecayWithHalfLife() {
        val m = InterestModel().record(Signal.ThumbUp, "SCIENCE", "NASA", "x", t0)
        assertEquals(1.5, m.topicScore("SCIENCE", t0 + 14 * day), 1e-9)
    }

    @Test fun dwellIsCapped() {
        val m = InterestModel().record(Signal.Dwell(600), "WORLD", "AP", "x", t0)
        assertEquals(2.0, m.topicScore("WORLD", t0), 1e-9)
    }

    @Test fun followedTopicsGetBonus() {
        val m = InterestModel(followedTopics = setOf("HEALTH"))
        assertEquals(InterestModel.FOLLOW_BONUS, m.topicScore("HEALTH", t0), 1e-9)
    }

    @Test fun learnsWithinTwentyInteractions() {
        var m = InterestModel(followedTopics = setOf("SCIENCE", "SPORTS"))
        repeat(10) { m = m.record(Signal.Opened, "SCIENCE", "NASA", "Space telescope discovery $it", t0) }
        repeat(10) { m = m.record(Signal.ThumbDown, "SPORTS", "ESPN", "Match report $it", t0) }
        fun art(id: String, key: String, src: String, title: String) = Article(id, title, "https://x/$id", src, null, t0, key)
        val ranked = Ranker(m, t0).rank(
            listOf(
                art("1", "SPORTS", "ESPN", "Team wins cup final"),
                art("2", "SCIENCE", "Science Mag", "New telescope spots distant planet"),
                art("3", "SPORTS", "ESPN", "Coach resigns after loss"),
            ),
        )
        assertEquals("2", ranked.first().id)
    }

    @Test fun blockedSourcesAreDroppedAndVarietyIsEnforced() {
        val m = InterestModel().blockSource("Spammy").record(Signal.ThumbUp, "WORLD", "BigNews", "world", t0)
        fun art(id: String, src: String) = Article(id, "Headline number $id about events", "https://x/$id", src, null, t0, "WORLD")
        val ranked = Ranker(m, t0).rank(listOf(art("a", "BigNews"), art("b", "BigNews"), art("c", "Other"), art("d", "Spammy")))
        assertEquals(3, ranked.size)
        assertEquals(listOf("BigNews", "Other", "BigNews"), ranked.map { it.sourceName })
    }
}

class ProfileWriterTest {
    @Test fun rendersAllSections() {
        val now = 1_790_000_000_000L
        val m = InterestModel(followedTopics = setOf("SCIENCE"), followedKeywords = setOf("electric cars"))
            .record(Signal.ThumbUp, "SCIENCE", "NASA", "Telescope galaxy discovery", now)
            .blockSource("Tabloid Daily")
        val stats = UsageStats().withOpen(8).withFiltered(FilterReason.CLICKBAIT, 4).withMinutes("2026-09-29", 12)
        val md = ProfileWriter.render(m, stats, listOf("@me.bsky.social"), now, ZoneOffset.UTC)
        listOf("# QuarterHour reader profile", "## Top topics", "**Science** (followed)", "telescope",
            "electric cars", "tabloid daily", "Clickbait headline: 4", "@me.bsky.social", "09-29: 12", "around 8:00")
            .forEach { assertTrue("missing '$it' in:\n$md", md.contains(it)) }
    }
}
