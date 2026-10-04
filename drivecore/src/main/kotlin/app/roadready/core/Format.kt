package app.roadready.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    fun date(date: LocalDate): String = date.format(dateFormat)

    /** 95 → "1 h 35 m", 120 → "2 h", 20 → "20 m". */
    fun hours(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m m"
            m == 0 -> "$h h"
            else -> "$h h $m m"
        }
    }
}
