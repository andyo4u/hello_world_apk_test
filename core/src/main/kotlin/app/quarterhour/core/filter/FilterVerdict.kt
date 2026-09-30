package app.quarterhour.core.filter

import kotlinx.serialization.Serializable

@Serializable
enum class FilterReason(val label: String) {
    CLICKBAIT("Clickbait headline"),
    AI_CONTENT("Likely AI-generated"),
    PAYWALL("Paywalled"),
    SPONSORED("Sponsored / ad"),
    BLOCKED_SOURCE("Source you blocked"),
    UNREADABLE("Couldn't open cleanly"),
}

data class Score(val value: Double, val reasons: List<String>) {
    companion object {
        val ZERO = Score(0.0, emptyList())
    }
}
