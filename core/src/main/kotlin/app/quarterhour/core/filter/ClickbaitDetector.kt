package app.quarterhour.core.filter

/**
 * Rule-based clickbait scoring for headlines. Each rule adds weight; a total
 * at or above [threshold] hides the article. Rules are deliberately about the
 * *form* of the headline (withholding, hype, direct address), not the topic.
 *
 * [adjustments] lets user feedback from the Filtered drawer loosen or tighten
 * individual rules without code changes.
 */
class ClickbaitDetector(
    private val threshold: Double = DEFAULT_THRESHOLD,
    private val adjustments: Map<String, Double> = emptyMap(),
) {
    private data class Rule(val id: String, val weight: Double, val test: (String, String) -> Boolean)

    fun score(headline: String, body: String? = null): Score {
        val lower = headline.lowercase().trim()
        val reasons = mutableListOf<String>()
        var total = 0.0
        for (rule in RULES) {
            if (rule.test(headline, lower)) {
                total += (rule.weight + (adjustments[rule.id] ?: 0.0)).coerceAtLeast(0.0)
                reasons += rule.id
            }
        }
        if (body != null && headlineBodyMismatch(headline, body)) {
            total += 1.0
            reasons += "headline-body-mismatch"
        }
        return Score(total, reasons)
    }

    fun isClickbait(headline: String, body: String? = null): Boolean = score(headline, body).value >= threshold

    companion object {
        const val DEFAULT_THRESHOLD = 2.0

        private val WITHHOLDING = listOf(
            "you won't believe", "you wont believe", "what happened next", "what happens next",
            "will shock you", "will blow your mind", "this one trick", "one weird trick", "the truth about",
            "don't want you to know", "nobody is talking about", "no one is talking about",
            "you need to see", "left speechless", "can't stop", "jaw-dropping",
            "jaw dropping", "is going viral", "breaks the internet", "internet is losing it",
            "fans are furious", "you'll never guess", "youll never guess", "wait until you see",
            "before it's deleted", "doctors hate", "the internet can't", "everyone is talking about",
        )
        /** Legit explainers use these too, so they only count alongside other signals. */
        private val SOFT_TEASERS = listOf("here's why", "heres why", "this is why", "the reason why", "the real reason")
        private val HYPE = setOf(
            "shocking", "stunning", "insane", "unbelievable", "incredible", "mind-blowing", "epic",
            "outrageous", "heartbreaking", "terrifying", "bizarre", "genius", "hilarious", "must-see",
            "destroys", "slams", "obliterates", "eviscerates", "savage", "brutal", "meltdown",
        )
        private val DIRECT_ADDRESS = Regex("\\b(you|your|you're|you'll)\\b")
        private val LISTICLE = Regex("^(\\d{1,3})\\s+(things|reasons|ways|signs|secrets|tricks|facts|photos|times|celebrities|foods|mistakes)\\b")
        private val DANGLING_DEMONSTRATIVE = Regex("^(this|these|here's|here is|that)\\b")
        private val WORD = Regex("[A-Za-z']+")

        private val RULES = listOf(
            Rule("withholding-phrase", 2.0) { _, l -> WITHHOLDING.any { it in l } },
            Rule("soft-teaser", 1.0) { _, l -> SOFT_TEASERS.any { it in l } },
            Rule("listicle", 1.0) { _, l -> LISTICLE.containsMatchIn(l) },
            Rule("hype-words", 1.0) { _, l -> WORD.findAll(l).count { it.value in HYPE } >= 1 },
            Rule("many-hype-words", 1.0) { _, l -> WORD.findAll(l).count { it.value in HYPE } >= 2 },
            Rule("all-caps-words", 1.0) { h, _ ->
                WORD.findAll(h).count { it.value.length >= 4 && it.value == it.value.uppercase() && it.value !in ACRONYM_OK } >= 2
            },
            Rule("excess-punctuation", 1.0) { h, _ -> "!!" in h || "?!" in h || h.count { it == '!' } >= 2 },
            Rule("exclamation", 0.5) { h, _ -> h.trimEnd().endsWith("!") },
            Rule("direct-address", 0.5) { _, l -> DIRECT_ADDRESS.containsMatchIn(l) },
            Rule("dangling-demonstrative", 1.0) { _, l ->
                DANGLING_DEMONSTRATIVE.containsMatchIn(l) && (l.contains(" is ") || l.contains(" will ") || l.contains(" could "))
            },
            Rule("question-teaser", 0.5) { h, _ -> h.trimEnd().endsWith("?") },
            Rule("ellipsis-teaser", 1.0) { h, _ -> h.trimEnd().endsWith("...") || h.trimEnd().endsWith("…") },
        )

        /** Common acronyms that are not shouting. */
        private val ACRONYM_OK = setOf("NASA", "NATO", "COVID", "FIFA", "UEFA", "OPEC", "NBA", "NFL", "NHL", "MLB", "CEO", "USA", "UK", "EU")

        /** A headline promising something the body never mentions is bait. */
        internal fun headlineBodyMismatch(headline: String, body: String): Boolean {
            val words = WORD.findAll(headline.lowercase()).map { it.value }.filter { it.length >= 5 }.toSet()
            if (words.size < 3 || body.length < 400) return false
            val bodyLower = body.lowercase()
            val found = words.count { it in bodyLower }
            return found.toDouble() / words.size < 0.25
        }
    }
}
