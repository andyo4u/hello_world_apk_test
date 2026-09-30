package app.quarterhour.core.paywall

import app.quarterhour.core.news.Article
import app.quarterhour.core.news.Domains
import app.quarterhour.core.news.RelatedCoverage

/**
 * Method 2 of 3: when a story's top link is paywalled, find the same story
 * from a free outlet. Candidates come from the Google News cluster attached to
 * the article, then from a Google News search for the headline. Wire services
 * are preferred because their copy is written to be republished.
 */
class FreeSourceFinder(private val detector: PaywallDetector) {

    data class Candidate(val title: String, val link: String, val sourceName: String, val sourceDomain: String?)

    /**
     * Ranks candidates that are not on a paywalled domain and whose headline
     * plausibly describes the same story. Best first.
     */
    fun rank(article: Article, candidates: List<Candidate>): List<Candidate> {
        val original = Domains.of(article.sourceUrl ?: article.link)
        return candidates
            .asSequence()
            .filter { c -> c.sourceDomain == null || original == null || !Domains.matches(c.sourceDomain, original) }
            .filterNot { detector.checkDomain(it.sourceDomain).isPaywalled }
            .filterNot { it.sourceDomain == null && isPaywalledName(it.sourceName) }
            .map { it to TitleSimilarity.score(article.title, it.title) }
            .filter { (_, sim) -> sim >= MIN_SIMILARITY }
            // Everything left is the same story; wire copy first, then closest headline.
            .sortedWith(compareByDescending<Pair<Candidate, Double>> { isWire(it.first) }.thenByDescending { it.second })
            .map { it.first }
            .distinctBy { it.link }
            .toList()
    }

    fun fromCluster(related: List<RelatedCoverage>): List<Candidate> =
        related.map { Candidate(it.title, it.link, it.sourceName, null) }

    fun fromSearch(results: List<Article>): List<Candidate> =
        results.map { Candidate(it.title, it.link, it.sourceName, it.sourceDomain) }

    /** Cluster entries only carry a publisher name, so map common paywalled names too. */
    private fun isPaywalledName(name: String): Boolean {
        val n = name.lowercase()
        return PAYWALLED_NAMES.any { it in n }
    }

    private fun isWire(c: Candidate): Boolean {
        val n = c.sourceName.lowercase()
        return WIRE_NAMES.any { it in n } || c.sourceDomain?.let { d -> WIRE_DOMAINS.any { Domains.matches(d, it) } } == true
    }

    companion object {
        const val MIN_SIMILARITY = 0.3
        private val WIRE_NAMES = listOf("associated press", "ap news", "reuters", "agence france", "afp")
        private val WIRE_DOMAINS = listOf("apnews.com", "reuters.com", "afp.com")
        private val PAYWALLED_NAMES = listOf(
            "wall street journal", "financial times", "new york times", "washington post", "bloomberg",
            "the economist", "barron's", "the athletic", "the new yorker", "the atlantic", "the telegraph",
            "business insider", "boston globe", "los angeles times",
        )
    }
}

/** Dice coefficient over headline words, ignoring stop words. 0.0..1.0 */
object TitleSimilarity {
    private val STOP = setOf(
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "at", "by", "from",
        "is", "are", "was", "were", "be", "as", "after", "over", "its", "it", "that", "this", "says",
    )

    fun tokens(text: String): Set<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 && it !in STOP }.toSet()

    fun score(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return 2.0 * ta.intersect(tb).size / (ta.size + tb.size)
    }
}
