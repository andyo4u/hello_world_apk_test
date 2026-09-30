package app.quarterhour.core.profile

import app.quarterhour.core.news.Article
import kotlin.math.exp
import kotlin.math.tanh

/** Orders articles by learned interest, freshness and variety. */
class Ranker(private val model: InterestModel, private val now: Long) {

    fun relevance(a: Article): Double {
        val topic = model.topicScore(a.feedKey, now)
        val source = model.sourceScore(a.sourceName, now)
        val terms = InterestModel.keyTerms(a.title).map { model.termScore(it, now) }
        val termScore = if (terms.isEmpty()) 0.0 else terms.sortedDescending().take(3).sum()
        val ageHours = ((now - a.publishedAtMillis).coerceAtLeast(0)) / 3_600_000.0
        val freshness = 2.0 * exp(-ageHours / 12.0)
        return tanh(topic / 10.0) * 3 + tanh(source / 5.0) * 1.5 + tanh(termScore / 5.0) * 2 + freshness
    }

    /** Greedy re-rank: each pick lowers the score of later items from the same source or topic. */
    fun rank(articles: List<Article>): List<Article> {
        val remaining = articles.filterNot { model.isBlocked(it.sourceName) }
            .associateWith { relevance(it) }.toMutableMap()
        val sourceCount = mutableMapOf<String, Int>()
        val topicCount = mutableMapOf<String, Int>()
        val out = mutableListOf<Article>()
        while (remaining.isNotEmpty()) {
            val best = remaining.maxBy { (a, s) ->
                s - 0.8 * (sourceCount[a.sourceName] ?: 0) - 0.3 * (topicCount[a.feedKey] ?: 0)
            }.key
            out += best
            remaining.remove(best)
            sourceCount.merge(best.sourceName, 1, Int::plus)
            topicCount.merge(best.feedKey, 1, Int::plus)
        }
        return out
    }
}
