package app.quarterhour.core.profile

import app.quarterhour.core.paywall.TitleSimilarity
import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
data class Weighted(val score: Double, val updatedAtMillis: Long) {
    /** Score after exponential decay towards zero. */
    fun at(nowMillis: Long, halfLifeMillis: Long): Double {
        val dt = (nowMillis - updatedAtMillis).coerceAtLeast(0)
        return score * 0.5.pow(dt.toDouble() / halfLifeMillis)
    }
}

/** What the user did with a piece of content. */
sealed interface Signal {
    data object Opened : Signal
    data class Dwell(val seconds: Long) : Signal
    data object ThumbUp : Signal
    data object ThumbDown : Signal
    /** "Less like this" on the whole topic or source of the item. */
    data object LessLikeThis : Signal
}

/** Everything the app has learned about the user's interests. Stored only on the device. */
@Serializable
data class InterestModel(
    val topics: Map<String, Weighted> = emptyMap(),
    val sources: Map<String, Weighted> = emptyMap(),
    val terms: Map<String, Weighted> = emptyMap(),
    val followedTopics: Set<String> = emptySet(),
    val followedKeywords: Set<String> = emptySet(),
    val blockedSources: Set<String> = emptySet(),
    val interactions: Int = 0,
) {
    fun topicScore(key: String, now: Long): Double =
        (topics[key]?.at(now, HALF_LIFE_MS) ?: 0.0) + if (key in followedTopics) FOLLOW_BONUS else 0.0

    fun sourceScore(source: String, now: Long): Double = sources[source.lowercase()]?.at(now, HALF_LIFE_MS) ?: 0.0

    fun termScore(term: String, now: Long): Double = terms[term]?.at(now, HALF_LIFE_MS) ?: 0.0

    /**
     * Applies a signal about an item.
     * @param topicKey feed key the item came from (Google News topic, "q:<keyword>", or "social")
     * @param source publisher or social account
     * @param title headline or post text, used to learn finer-grained terms
     */
    fun record(signal: Signal, topicKey: String, source: String, title: String, now: Long): InterestModel {
        val weight = when (signal) {
            Signal.Opened -> 1.0
            is Signal.Dwell -> (signal.seconds / 60.0).coerceAtMost(2.0)
            Signal.ThumbUp -> 3.0
            Signal.ThumbDown -> -3.0
            Signal.LessLikeThis -> -5.0
        }
        val newTerms = terms.toMutableMap()
        for (term in keyTerms(title)) {
            newTerms[term] = bump(newTerms[term], weight * TERM_FACTOR, now)
        }
        return copy(
            topics = topics + (topicKey to bump(topics[topicKey], weight, now)),
            sources = sources + (source.lowercase() to bump(sources[source.lowercase()], weight * SOURCE_FACTOR, now)),
            terms = prune(newTerms, now),
            interactions = interactions + 1,
        )
    }

    fun blockSource(source: String): InterestModel = copy(blockedSources = blockedSources + source.lowercase())

    fun isBlocked(source: String): Boolean = source.lowercase() in blockedSources

    fun topTopics(now: Long, n: Int = 8): List<Pair<String, Double>> =
        (topics.keys + followedTopics).associateWith { topicScore(it, now) }
            .entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

    fun topTerms(now: Long, n: Int = 15): List<Pair<String, Double>> =
        terms.mapValues { it.value.at(now, HALF_LIFE_MS) }.filter { it.value > 0.2 }
            .entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

    fun sourcesByScore(now: Long): List<Pair<String, Double>> =
        sources.mapValues { it.value.at(now, HALF_LIFE_MS) }.entries.sortedByDescending { it.value }.map { it.key to it.value }

    private fun bump(existing: Weighted?, delta: Double, now: Long): Weighted {
        val current = existing?.at(now, HALF_LIFE_MS) ?: 0.0
        return Weighted((current + delta).coerceIn(-MAX_SCORE, MAX_SCORE), now)
    }

    private fun prune(map: Map<String, Weighted>, now: Long): Map<String, Weighted> {
        if (map.size <= MAX_TERMS) return map
        return map.entries.sortedByDescending { kotlin.math.abs(it.value.at(now, HALF_LIFE_MS)) }
            .take(MAX_TERMS).associate { it.key to it.value }
    }

    companion object {
        const val HALF_LIFE_MS = 14L * 24 * 60 * 60 * 1000
        const val FOLLOW_BONUS = 3.0
        const val MAX_SCORE = 30.0
        const val TERM_FACTOR = 0.4
        const val SOURCE_FACTOR = 0.5
        const val MAX_TERMS = 400

        fun keyTerms(title: String): Set<String> = TitleSimilarity.tokens(title).filter { it.length >= 4 && !it.all(Char::isDigit) }.toSet()
    }
}
