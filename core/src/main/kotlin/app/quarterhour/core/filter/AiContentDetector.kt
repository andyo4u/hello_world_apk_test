package app.quarterhour.core.filter

import app.quarterhour.core.news.Domains

/**
 * Flags *obvious* machine-generated articles. Deliberately conservative: it
 * looks for leftovers that humans rarely write, template-like repetition, and
 * known content farms, rather than guessing from style.
 */
class AiContentDetector(
    private val knownFarms: Set<String> = DEFAULT_AI_FARMS,
    private val threshold: Double = DEFAULT_THRESHOLD,
) {
    fun score(host: String?, byline: String?, text: String, imagesFlaggedAi: Int = 0): Score {
        val reasons = mutableListOf<String>()
        var total = 0.0
        if (host != null && knownFarms.any { Domains.matches(host, it) }) {
            total += 3.0; reasons += "known-ai-farm"
        }
        val lower = text.lowercase()
        val tells = LEFTOVER_PHRASES.count { it in lower }
        if (tells > 0) {
            total += 3.0; reasons += "ai-leftover-phrase"
        }
        val cliches = CLICHES.count { it in lower }
        if (cliches >= 3) {
            total += 1.0; reasons += "ai-cliches"
        }
        if (cliches >= 5) {
            total += 1.0; reasons += "many-ai-cliches"
        }
        if (repetitiveness(text) > 0.12) {
            total += 1.0; reasons += "repetitive-text"
        }
        if (byline.isNullOrBlank() || GENERIC_BYLINES.any { byline.lowercase().contains(it) }) {
            total += 0.5; reasons += "no-real-author"
        }
        if (imagesFlaggedAi > 0) {
            total += 1.0; reasons += "ai-image-metadata"
        }
        return Score(total, reasons)
    }

    fun isAi(host: String?, byline: String?, text: String, imagesFlaggedAi: Int = 0): Boolean =
        score(host, byline, text, imagesFlaggedAi).value >= threshold

    companion object {
        const val DEFAULT_THRESHOLD = 3.0

        /**
         * Empty by default: the app ships no unverified accusations. Users add
         * domains from the Filtered drawer ("Always hide this source as AI").
         */
        val DEFAULT_AI_FARMS: Set<String> = emptySet()

        internal val LEFTOVER_PHRASES = listOf(
            "as an ai language model", "as an ai model", "i'm sorry, but i cannot", "i cannot fulfill this request",
            "as of my last knowledge update", "as of my knowledge cutoff", "i don't have access to real-time",
            "regenerate response", "certainly! here is", "certainly! here's", "here is a rewritten version",
            "[insert ", "(insert ", "please note that as an ai",
        )

        internal val CLICHES = listOf(
            "in today's fast-paced world", "in the ever-evolving landscape", "it's important to note that",
            "it is important to note that", "delve into", "delves into", "in conclusion,", "a testament to",
            "navigating the complexities", "the realm of", "tapestry of", "plays a pivotal role",
            "in summary,", "unlock the potential", "whether you're a seasoned", "game-changer",
            "embark on a journey", "ever-changing", "it's worth noting that", "stands as a beacon",
        )

        private val GENERIC_BYLINES = listOf("staff writer", "admin", "editorial team", "news desk", "content team")

        /** Share of word 4-grams that repeat — templated filler scores high. */
        internal fun repetitiveness(text: String): Double {
            val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotEmpty() }
            if (words.size < 120) return 0.0
            val grams = words.windowed(4).map { it.joinToString(" ") }
            val counts = grams.groupingBy { it }.eachCount()
            val repeated = counts.values.filter { it > 1 }.sumOf { it - 1 }
            return repeated.toDouble() / grams.size
        }
    }
}
