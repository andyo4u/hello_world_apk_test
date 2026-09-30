package app.quarterhour.core.social

import app.quarterhour.core.filter.ClickbaitDetector
import app.quarterhour.core.profile.InterestModel
import kotlin.math.exp
import kotlin.math.ln

/** Chooses the day's 15 image/video posts from the user's timelines. */
class SocialPicker(
    private val model: InterestModel,
    private val clickbait: ClickbaitDetector = ClickbaitDetector(),
    private val dailyCount: Int = DAILY_COUNT,
) {
    enum class Rejection { NO_MEDIA, SPONSORED, SENSITIVE, CLICKBAIT, BLOCKED, ALREADY_SHOWN }

    data class Result(val picks: List<SocialPost>, val rejected: Map<Rejection, Int>)

    fun pick(posts: List<SocialPost>, alreadyShownIds: Set<String>, now: Long): Result {
        val rejected = mutableMapOf<Rejection, Int>()
        fun reject(r: Rejection) = rejected.merge(r, 1, Int::plus)

        val eligible = posts.distinctBy { it.id }.filter { p ->
            when {
                p.id in alreadyShownIds -> { reject(Rejection.ALREADY_SHOWN); false }
                p.media.isEmpty() -> { reject(Rejection.NO_MEDIA); false }
                isSponsored(p.text) -> { reject(Rejection.SPONSORED); false }
                p.labels.any { it.lowercase() in HIDDEN_LABELS || it == "sensitive" || it.startsWith("cw:") } -> { reject(Rejection.SENSITIVE); false }
                model.isBlocked(p.authorHandle) -> { reject(Rejection.BLOCKED); false }
                clickbait.isClickbait(p.text.lineSequence().firstOrNull().orEmpty().take(200)) -> { reject(Rejection.CLICKBAIT); false }
                else -> true
            }
        }

        val scored = eligible.associateWith { score(it, now) }
        val perAuthor = mutableMapOf<String, Int>()
        val picks = scored.entries.sortedByDescending { it.value }
            .map { it.key }
            .filter { perAuthor.merge(it.authorHandle, 1, Int::plus)!! <= MAX_PER_AUTHOR }
            .take(dailyCount)
        return Result(picks, rejected)
    }

    internal fun score(p: SocialPost, now: Long): Double {
        val terms = InterestModel.keyTerms(p.text).map { model.termScore(it, now) }.sortedDescending().take(3).sum()
        val author = model.sourceScore(p.authorHandle, now)
        val popularity = ln(1.0 + p.likeCount) * 0.4
        val ageHours = ((now - p.createdAtMillis).coerceAtLeast(0)) / 3_600_000.0
        val freshness = 2.0 * exp(-ageHours / 18.0)
        val videoVariety = if (p.media.any { it.type == MediaType.VIDEO }) 0.3 else 0.0
        return terms * 0.6 + author * 0.8 + popularity + freshness + videoVariety
    }

    companion object {
        const val DAILY_COUNT = 15
        const val MAX_PER_AUTHOR = 3

        private val HIDDEN_LABELS = setOf("porn", "sexual", "nudity", "graphic-media", "gore", "!warn", "!hide")

        private val SPONSORED_PATTERNS = listOf(
            Regex("(^|\\s)#(ad|ads|sponsored|spon|promo|paidpartnership|affiliate|partner)\\b", RegexOption.IGNORE_CASE),
            Regex("\\bpaid partnership\\b", RegexOption.IGNORE_CASE),
            Regex("\\buse (my )?code\\b", RegexOption.IGNORE_CASE),
            Regex("\\blink in bio\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(\\d{1,2})% off\\b", RegexOption.IGNORE_CASE),
        )

        fun isSponsored(text: String): Boolean = SPONSORED_PATTERNS.any { it.containsMatchIn(text) }
    }
}
