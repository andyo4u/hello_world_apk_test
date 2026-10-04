package app.roadready.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise and sunset from the NOAA / Almanac solar formula (accurate to a
 * minute or two, plenty for splitting a drive into day and night). Oregon's
 * headlight law uses sunset to sunrise, so that's what "night" means here.
 */
class SunClock(
    private val latitude: Double,
    private val longitude: Double,
    val zone: ZoneId = OREGON_ZONE,
) {
    constructor(city: HomeCity) : this(city.latitude, city.longitude, ZoneId.of(city.zoneId))

    private val cache = HashMap<LocalDate, Pair<Instant, Instant>>()

    /** Sunrise and sunset on [date] (local), as instants. */
    fun sunriseSunset(date: LocalDate): Pair<Instant, Instant> = cache.getOrPut(date) {
        Pair(event(date, rising = true), event(date, rising = false))
    }

    fun isDark(at: Instant): Boolean {
        val (rise, set) = sunriseSunset(at.atZone(zone).toLocalDate())
        return at < rise || at >= set
    }

    /** Minutes of a drive starting at [startMillis] and lasting [minutes] that fall after sunset or before sunrise. */
    fun nightMinutes(startMillis: Long, minutes: Int): Int {
        var night = 0
        for (m in 0 until minutes) {
            // Sample the middle of each minute.
            if (isDark(Instant.ofEpochMilli(startMillis + m * 60_000L + 30_000L))) night++
        }
        return night
    }

    private fun event(date: LocalDate, rising: Boolean): Instant {
        val n = date.dayOfYear
        val lngHour = longitude / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        val l = norm360(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634)
        var ra = norm360(deg(atan2(0.91764 * tan(rad(l)), 1.0)))
        // Put right ascension in the same quadrant as the true longitude.
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(ZENITH)) - sinDec * sin(rad(latitude))) / (cosDec * cos(rad(latitude)))
        val h = (if (rising) 360.0 - deg(acos(cosH.coerceIn(-1.0, 1.0))) else deg(acos(cosH.coerceIn(-1.0, 1.0)))) / 15.0
        val localMean = h + ra - 0.06571 * t - 6.622
        val utcHours = ((localMean - lngHour) % 24.0 + 24.0) % 24.0
        val midnightUtc = date.atStartOfDay(ZoneId.of("UTC")).toInstant()
        var instant = midnightUtc.plusSeconds((utcHours * 3600).roundToInt().toLong())
        // UTC hour wraps past midnight: sunset in Oregon is the next UTC day. Bring it onto the local date.
        val local = instant.atZone(zone).toLocalDate()
        if (local.isBefore(date)) instant = instant.plusSeconds(86_400)
        if (local.isAfter(date)) instant = instant.minusSeconds(86_400)
        return instant
    }

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun norm360(d: Double) = ((d % 360.0) + 360.0) % 360.0

    companion object {
        val OREGON_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
        /** Official sunrise/sunset: sun's center 50' below the horizon. */
        private const val ZENITH = 90.833
    }
}
