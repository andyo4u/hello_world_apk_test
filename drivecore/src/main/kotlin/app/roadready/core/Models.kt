package app.roadready.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

/** Stores a [LocalDate] as an ISO string ("2010-04-30") so the JSON stays readable. */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

typealias Date = @Serializable(with = LocalDateSerializer::class) LocalDate

/** One teen learning to drive. */
@Serializable
data class Driver(
    val id: String,
    val name: String,
    val birthDate: Date,
    /** ARGB color used for the kid's avatar and chips. */
    val color: Long = 0xFF1F6F8BL,
    val permitDate: Date? = null,
    val provisionalDate: Date? = null,
    /** Enrolled in or finished an ODOT-approved traffic safety education course. */
    val driverEd: Boolean = false,
    val passedDriveTest: Boolean = false,
)

/** What a drive covered. Shown as chips; used for the experience breakdown. */
@Serializable
enum class Condition(val label: String) {
    RESIDENTIAL("Residential"),
    CITY("City traffic"),
    HIGHWAY("Highway / freeway"),
    RURAL("Rural roads"),
    PARKING("Parking"),
    RAIN("Rain"),
    FOG("Fog"),
    SNOW_ICE("Snow / ice"),
    GRAVEL("Gravel"),
    MOUNTAIN("Mountain pass"),
    ROUNDABOUT("Roundabout"),
    NIGHT("Night"),
}

/** One supervised drive in the log. */
@Serializable
data class Drive(
    val id: String,
    val driverId: String,
    val startMillis: Long,
    val minutes: Int,
    /** Part of [minutes] driven between sunset and sunrise. */
    val nightMinutes: Int = 0,
    val supervisor: String = "",
    val conditions: Set<Condition> = emptySet(),
    val notes: String = "",
)

/** A drive whose timer is running. Persisted so it survives the app being closed. */
@Serializable
data class ActiveDrive(
    val driverId: String,
    val startMillis: Long,
    val supervisor: String = "",
)

@Serializable
enum class QuizMode(val label: String) { PRACTICE("Practice"), TOPIC("Topic"), MOCK_EXAM("Mock test") }

@Serializable
data class QuizAttempt(
    val driverId: String,
    val atMillis: Long,
    val mode: QuizMode,
    val correct: Int,
    val total: Int,
    val topicId: String? = null,
) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}

/** Leitner-box state for one question and one kid: box 0 = new or just missed, 4 = known. */
@Serializable
data class QuestionStat(val box: Int = 0, val seen: Int = 0, val correct: Int = 0)

@Serializable
enum class SkillLevel(val label: String) {
    NOT_STARTED("Not started"),
    INTRODUCED("Introduced"),
    PRACTICING("Practicing"),
    CONFIDENT("Confident");

    fun next(): SkillLevel = entries[(ordinal + 1) % entries.size]
}

/** A place in Oregon used to work out sunset and sunrise for the night-driving split. */
@Serializable
enum class HomeCity(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    /** Most of Malheur County keeps Mountain time. */
    val zoneId: String = "America/Los_Angeles",
) {
    PORTLAND("Portland", 45.5152, -122.6784),
    SALEM("Salem", 44.9429, -123.0351),
    EUGENE("Eugene", 44.0521, -123.0868),
    BEND("Bend", 44.0582, -121.3153),
    MEDFORD("Medford", 42.3265, -122.8756),
    COOS_BAY("Coos Bay", 43.3665, -124.2179),
    KLAMATH_FALLS("Klamath Falls", 42.2249, -121.7817),
    PENDLETON("Pendleton", 45.6721, -118.7886),
    ONTARIO("Ontario", 44.0266, -116.9629, "America/Boise"),
}
