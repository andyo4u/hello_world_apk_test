package app.roadready.core

import kotlinx.serialization.Serializable

/**
 * Everything the app stores, as one JSON document. Changes go through the pure
 * functions below so they can be unit-tested without Android.
 */
@Serializable
data class AppState(
    val drivers: List<Driver> = emptyList(),
    val selectedDriverId: String? = null,
    val drives: List<Drive> = emptyList(),
    val activeDrives: List<ActiveDrive> = emptyList(),
    val attempts: List<QuizAttempt> = emptyList(),
    /** driverId → questionId → stat. */
    val mastery: Map<String, Map<String, QuestionStat>> = emptyMap(),
    /** driverId → skillId → level. */
    val skills: Map<String, Map<String, SkillLevel>> = emptyMap(),
    val homeCity: HomeCity = HomeCity.PORTLAND,
    /** Names offered when logging a drive. */
    val supervisors: List<String> = emptyList(),
) {
    val selectedDriver: Driver?
        get() = drivers.firstOrNull { it.id == selectedDriverId } ?: drivers.firstOrNull()

    fun drivesFor(driverId: String): List<Drive> = drives.filter { it.driverId == driverId }.sortedByDescending { it.startMillis }
    fun activeDriveFor(driverId: String): ActiveDrive? = activeDrives.firstOrNull { it.driverId == driverId }
    fun attemptsFor(driverId: String): List<QuizAttempt> = attempts.filter { it.driverId == driverId }.sortedByDescending { it.atMillis }
    fun masteryFor(driverId: String): Map<String, QuestionStat> = mastery[driverId].orEmpty()
    fun skillsFor(driverId: String): Map<String, SkillLevel> = skills[driverId].orEmpty()

    fun upsertDriver(driver: Driver): AppState {
        val exists = drivers.any { it.id == driver.id }
        return copy(
            drivers = if (exists) drivers.map { if (it.id == driver.id) driver else it } else drivers + driver,
            selectedDriverId = if (exists) selectedDriverId else driver.id,
        )
    }

    /** Removes a kid and everything recorded for them. */
    fun removeDriver(driverId: String): AppState = copy(
        drivers = drivers.filterNot { it.id == driverId },
        selectedDriverId = if (selectedDriverId == driverId) drivers.firstOrNull { it.id != driverId }?.id else selectedDriverId,
        drives = drives.filterNot { it.driverId == driverId },
        activeDrives = activeDrives.filterNot { it.driverId == driverId },
        attempts = attempts.filterNot { it.driverId == driverId },
        mastery = mastery - driverId,
        skills = skills - driverId,
    )

    fun select(driverId: String): AppState = if (drivers.any { it.id == driverId }) copy(selectedDriverId = driverId) else this

    fun startDrive(driverId: String, nowMillis: Long, supervisor: String): AppState =
        if (activeDriveFor(driverId) != null) this
        else copy(activeDrives = activeDrives + ActiveDrive(driverId, nowMillis, supervisor.trim()))

    fun cancelDrive(driverId: String): AppState = copy(activeDrives = activeDrives.filterNot { it.driverId == driverId })

    /**
     * Stops the timer and logs the drive. Drives under a minute are dropped.
     * [sun] splits the minutes into day and night.
     */
    fun finishDrive(
        driverId: String,
        nowMillis: Long,
        id: String,
        sun: SunClock,
        conditions: Set<Condition> = emptySet(),
        notes: String = "",
    ): AppState {
        val active = activeDriveFor(driverId) ?: return this
        val minutes = ((nowMillis - active.startMillis) / 60_000L).toInt()
        val stopped = cancelDrive(driverId)
        if (minutes < 1) return stopped
        val drive = Drive(
            id = id,
            driverId = driverId,
            startMillis = active.startMillis,
            minutes = minutes,
            nightMinutes = sun.nightMinutes(active.startMillis, minutes),
            supervisor = active.supervisor,
            conditions = conditions,
            notes = notes.trim(),
        )
        return stopped.saveDrive(drive)
    }

    /** Adds or replaces a drive, and remembers the supervisor's name for next time. */
    fun saveDrive(drive: Drive): AppState {
        val exists = drives.any { it.id == drive.id }
        val name = drive.supervisor.trim()
        return copy(
            drives = if (exists) drives.map { if (it.id == drive.id) drive else it } else drives + drive,
            supervisors = if (name.isEmpty() || supervisors.any { it.equals(name, ignoreCase = true) }) supervisors
            else supervisors + name,
        )
    }

    fun deleteDrive(id: String): AppState = copy(drives = drives.filterNot { it.id == id })

    fun recordAnswer(driverId: String, questionId: String, correct: Boolean): AppState {
        val forDriver = masteryFor(driverId)
        val updated = forDriver + (questionId to QuizEngine.record(forDriver[questionId], correct))
        return copy(mastery = mastery + (driverId to updated))
    }

    fun recordAttempt(attempt: QuizAttempt): AppState = copy(attempts = attempts + attempt)

    fun setSkill(driverId: String, skillId: String, level: SkillLevel): AppState =
        copy(skills = skills + (driverId to (skillsFor(driverId) + (skillId to level))))
}
