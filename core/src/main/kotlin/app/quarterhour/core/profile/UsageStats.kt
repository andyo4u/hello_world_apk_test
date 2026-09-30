package app.quarterhour.core.profile

import app.quarterhour.core.filter.FilterReason
import kotlinx.serialization.Serializable

/** Running counters shown in user_profile.md. */
@Serializable
data class UsageStats(
    /** Articles opened per hour of day (0..23). */
    val opensByHour: List<Int> = List(24) { 0 },
    /** Minutes used per ISO date, most recent 30 days. */
    val minutesByDay: Map<String, Int> = emptyMap(),
    val filteredCounts: Map<FilterReason, Int> = emptyMap(),
    val articlesRead: Int = 0,
    val socialViewed: Int = 0,
) {
    fun withOpen(hour: Int): UsageStats =
        copy(opensByHour = opensByHour.toMutableList().also { it[hour.coerceIn(0, 23)]++ }, articlesRead = articlesRead + 1)

    fun withFiltered(reason: FilterReason, n: Int = 1): UsageStats =
        copy(filteredCounts = filteredCounts + (reason to (filteredCounts[reason] ?: 0) + n))

    fun withMinutes(isoDate: String, minutes: Int): UsageStats {
        val updated = (minutesByDay + (isoDate to minutes)).entries.sortedByDescending { it.key }.take(30)
        return copy(minutesByDay = updated.associate { it.key to it.value })
    }

    fun withSocialViewed(): UsageStats = copy(socialViewed = socialViewed + 1)
}
