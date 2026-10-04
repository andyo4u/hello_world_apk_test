package app.roadready.core

import java.time.LocalDate
import java.time.LocalTime

/** Where a teen is in Oregon's graduated licensing. */
enum class Stage(val label: String) {
    PRE_PERMIT("Getting ready for the permit"),
    PERMIT("Instruction permit"),
    PROVISIONAL("Provisional license"),
    FULL("Full driving privileges"),
}

data class Milestone(val label: String, val done: Boolean, val detail: String)

/** A provisional-license or permit rule in force today, and when it stops applying (null = while the stage lasts). */
data class Restriction(val title: String, val detail: String, val endsOn: LocalDate?)

/**
 * Oregon graduated driver license rules (ODOT DMV teen licensing page, ORS 807.065
 * and ORS 807.122). Ages and dates are pure arithmetic so they can be unit-tested.
 */
object OregonGdl {
    const val PERMIT_AGE = 15
    const val PROVISIONAL_AGE = 16
    const val ADULT_AGE = 18
    const val PERMIT_HOLD_MONTHS = 6L
    const val HOURS_WITHOUT_DRIVER_ED = 100
    const val HOURS_WITH_DRIVER_ED = 50
    const val KNOWLEDGE_TEST_QUESTIONS = 35
    const val KNOWLEDGE_TEST_PASS = 28

    fun requiredHours(driver: Driver): Int =
        if (driver.driverEd) HOURS_WITH_DRIVER_ED else HOURS_WITHOUT_DRIVER_ED

    fun ageOn(driver: Driver, date: LocalDate): Int =
        java.time.Period.between(driver.birthDate, date).years.coerceAtLeast(0)

    fun permitEligibleOn(driver: Driver): LocalDate = driver.birthDate.plusYears(PERMIT_AGE.toLong())

    /** Earliest date for a provisional license: 16th birthday, and 6 months after the permit was issued. */
    fun provisionalEligibleOn(driver: Driver): LocalDate? {
        val permit = driver.permitDate ?: return null
        val sixteen = driver.birthDate.plusYears(PROVISIONAL_AGE.toLong())
        val held = permit.plusMonths(PERMIT_HOLD_MONTHS)
        return maxOf(sixteen, held)
    }

    fun stage(driver: Driver, today: LocalDate): Stage = when {
        driver.provisionalDate != null && !today.isBefore(driver.provisionalDate) ->
            if (ageOn(driver, today) >= ADULT_AGE) Stage.FULL else Stage.PROVISIONAL
        driver.permitDate != null && !today.isBefore(driver.permitDate) -> Stage.PERMIT
        else -> Stage.PRE_PERMIT
    }

    /** The checklist toward the next license. */
    fun milestones(driver: Driver, today: LocalDate, loggedMinutes: Int): List<Milestone> {
        val hours = loggedMinutes / 60
        val required = requiredHours(driver)
        val permitOn = permitEligibleOn(driver)
        return when (stage(driver, today)) {
            Stage.PRE_PERMIT -> listOf(
                Milestone(
                    "Be at least $PERMIT_AGE",
                    !today.isBefore(permitOn),
                    if (today.isBefore(permitOn)) "On ${Format.date(permitOn)}" else "Eligible now",
                ),
                Milestone(
                    "Pass the knowledge test",
                    false,
                    "$KNOWLEDGE_TEST_QUESTIONS questions; $KNOWLEDGE_TEST_PASS correct to pass. Practice with the mock test.",
                ),
                Milestone("Pass the vision screening", false, "At the DMV office, with glasses or contacts if you wear them"),
            )
            Stage.PERMIT -> {
                val eligible = provisionalEligibleOn(driver)!!
                val sixteen = driver.birthDate.plusYears(PROVISIONAL_AGE.toLong())
                val held = driver.permitDate!!.plusMonths(PERMIT_HOLD_MONTHS)
                listOf(
                    Milestone("Be at least $PROVISIONAL_AGE", !today.isBefore(sixteen), "On ${Format.date(sixteen)}"),
                    Milestone("Hold the permit 6 months", !today.isBefore(held), "On ${Format.date(held)}"),
                    Milestone(
                        "Log $required supervised hours",
                        hours >= required,
                        "${Format.hours(loggedMinutes)} of $required h" +
                            if (driver.driverEd) " (with ODOT-approved driver ed)" else " (50 with ODOT-approved driver ed)",
                    ),
                    Milestone(
                        "ODOT-approved driver education",
                        driver.driverEd,
                        if (driver.driverEd) "Cuts the requirement to 50 hours" else "Optional, but halves the hours and often lowers insurance",
                    ),
                    Milestone(
                        "Pass the drive test",
                        driver.passedDriveTest,
                        "Earliest ${Format.date(eligible)}. Ask your driver-ed provider whether they can give it.",
                    ),
                )
            }
            Stage.PROVISIONAL -> {
                val eighteen = driver.birthDate.plusYears(ADULT_AGE.toLong())
                listOf(Milestone("Turn $ADULT_AGE", false, "Provisional limits end by ${Format.date(eighteen)}"))
            }
            Stage.FULL -> emptyList()
        }
    }

    /** Rules in force on [today], each with the date it stops applying. */
    fun restrictions(driver: Driver, today: LocalDate): List<Restriction> {
        val eighteen = driver.birthDate.plusYears(ADULT_AGE.toLong())
        val under18 = today.isBefore(eighteen)
        val phone = Restriction(
            "No phone while driving",
            "Under 18, no calls or texts at all, hands-free included.",
            eighteen,
        )
        return when (stage(driver, today)) {
            Stage.PRE_PERMIT -> emptyList()
            Stage.PERMIT -> listOf(
                Restriction(
                    "Supervisor in the front seat",
                    "A licensed driver 21 or older must sit in the front passenger seat. " +
                        "Hours count toward the log when that driver has held a license for 3+ years.",
                    null,
                ),
                phone,
            )
            Stage.PROVISIONAL -> {
                val licensed = driver.provisionalDate!!
                // Passenger and night limits end at one year or at 18, whichever comes first.
                val limitsEnd = minOf(licensed.plusYears(1), eighteen)
                val sixMonths = minOf(licensed.plusMonths(6), limitsEnd)
                buildList {
                    if (today.isBefore(sixMonths)) {
                        add(
                            Restriction(
                                "No teen passengers",
                                "No passengers under 20 who aren't immediate family.",
                                sixMonths,
                            ),
                        )
                    } else if (today.isBefore(limitsEnd)) {
                        add(
                            Restriction(
                                "At most 3 teen passengers",
                                "No more than 3 passengers under 20 who aren't immediate family.",
                                limitsEnd,
                            ),
                        )
                    }
                    if (today.isBefore(limitsEnd)) {
                        add(
                            Restriction(
                                "No driving midnight–5 a.m.",
                                "Except between home and work, for work, between home and a school event with no " +
                                    "other ride, or with a licensed driver 25 or older.",
                                limitsEnd,
                            ),
                        )
                    }
                    if (under18) add(phone)
                    if (today.isBefore(limitsEnd)) {
                        add(
                            Restriction(
                                "Family and instructors don't count",
                                "Passenger limits don't apply with a parent, stepparent or guardian with a valid " +
                                    "license, or a certified driver-ed instructor.",
                                limitsEnd,
                            ),
                        )
                    }
                }
            }
            Stage.FULL -> emptyList()
        }
    }

    /** True from midnight up to 5 a.m., the provisional curfew window. */
    fun inCurfew(time: LocalTime): Boolean = time.isBefore(LocalTime.of(5, 0))
}
