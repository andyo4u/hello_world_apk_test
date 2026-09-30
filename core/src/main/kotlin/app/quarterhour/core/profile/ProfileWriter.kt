package app.quarterhour.core.profile

import app.quarterhour.core.filter.FilterReason
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Renders `user_profile.md`: a plain-language summary of what the user is into. */
object ProfileWriter {

    fun render(
        model: InterestModel,
        stats: UsageStats,
        socialAccounts: List<String>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        topicLabel: (String) -> String = ::defaultLabel,
    ): String = buildString {
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).withZone(zone).format(Instant.ofEpochMilli(nowMillis))
        appendLine("# QuarterHour reader profile")
        appendLine()
        appendLine("_Last updated: ${stamp}. Kept on this device only; learned from ${model.interactions} interactions._")
        appendLine()

        appendLine("## Top topics")
        val topics = model.topTopics(nowMillis).filter { it.second > 0.05 }
        if (topics.isEmpty()) appendLine("- Still learning — read a few articles.")
        topics.forEach { (key, score) ->
            val followed = if (key in model.followedTopics) " (followed)" else ""
            appendLine("- **${topicLabel(key)}**$followed — ${bar(score)} ${fmt(score)}")
        }
        appendLine()

        appendLine("## Recurring interests")
        val terms = model.topTerms(nowMillis)
        if (terms.isEmpty()) appendLine("- None yet.") else appendLine(terms.joinToString(", ") { it.first })
        if (model.followedKeywords.isNotEmpty()) {
            appendLine()
            appendLine("Followed keywords: ${model.followedKeywords.sorted().joinToString(", ")}")
        }
        appendLine()

        appendLine("## Sources")
        val sources = model.sourcesByScore(nowMillis)
        val liked = sources.filter { it.second > 0.5 }.take(8)
        val disliked = sources.filter { it.second < -0.5 }.take(8)
        appendLine("- Favorites: ${liked.joinToString(", ") { it.first }.ifEmpty { "none yet" }}")
        appendLine("- Less interested in: ${disliked.joinToString(", ") { it.first }.ifEmpty { "none" }}")
        appendLine("- Blocked: ${model.blockedSources.sorted().joinToString(", ").ifEmpty { "none" }}")
        appendLine()

        appendLine("## Reading habits")
        appendLine("- Articles read: ${stats.articlesRead}; social posts viewed: ${stats.socialViewed}")
        val peak = stats.opensByHour.withIndex().maxByOrNull { it.value }
        if (peak != null && peak.value > 0) appendLine("- Usually reads around ${peak.index}:00")
        val recent = stats.minutesByDay.entries.sortedByDescending { it.key }.take(7)
        if (recent.isNotEmpty()) {
            appendLine("- Last 7 days (minutes of 15): " + recent.joinToString(", ") { "${it.key.substring(5)}: ${it.value}" })
        }
        appendLine()

        appendLine("## Filtered for you")
        if (stats.filteredCounts.isEmpty()) appendLine("- Nothing filtered yet.")
        FilterReason.entries.forEach { r -> stats.filteredCounts[r]?.let { appendLine("- ${r.label}: $it") } }
        appendLine()

        appendLine("## Social")
        appendLine("- Connected: ${socialAccounts.joinToString(", ").ifEmpty { "none" }}")
        val socialScore = model.topicScore("social", nowMillis)
        appendLine("- Social engagement score: ${fmt(socialScore)}")
    }

    private fun bar(score: Double): String {
        val n = (score / 2).toInt().coerceIn(0, 10)
        return "█".repeat(n) + "░".repeat(10 - n)
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun defaultLabel(key: String): String = when {
        key.startsWith("q:") -> "\"${key.removePrefix("q:")}\""
        else -> key.lowercase().replaceFirstChar { it.uppercase() }
    }
}
