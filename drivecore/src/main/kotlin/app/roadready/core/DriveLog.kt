package app.roadready.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LogTotals(
    val drives: Int,
    val minutes: Int,
    val nightMinutes: Int,
    val byCondition: Map<Condition, Int>,
) {
    val dayMinutes: Int get() = minutes - nightMinutes
}

/** Totals and exports for one kid's supervised driving. */
object DriveLog {
    fun totals(drives: List<Drive>): LogTotals = LogTotals(
        drives = drives.size,
        minutes = drives.sumOf { it.minutes },
        nightMinutes = drives.sumOf { it.nightMinutes },
        byCondition = Condition.entries.associateWith { c -> drives.filter { c in it.conditions }.sumOf { it.minutes } }
            .filterValues { it > 0 },
    )

    /** Spreadsheet-friendly log, oldest drive first. */
    fun csv(driver: Driver, drives: List<Drive>, zone: ZoneId): String = buildString {
        appendLine("Driver,Date,Start,Minutes,Night minutes,Supervisor,Conditions,Notes")
        val date = DateTimeFormatter.ISO_LOCAL_DATE
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        for (d in drives.sortedBy { it.startMillis }) {
            val start = Instant.ofEpochMilli(d.startMillis).atZone(zone)
            appendLine(
                listOf(
                    driver.name,
                    start.format(date),
                    start.format(time),
                    d.minutes.toString(),
                    d.nightMinutes.toString(),
                    d.supervisor,
                    d.conditions.sortedBy { it.ordinal }.joinToString("; ") { it.label },
                    d.notes,
                ).joinToString(",") { csvCell(it) },
            )
        }
    }

    /** Plain-text summary to copy onto the DMV's driving experience certification. */
    fun summary(driver: Driver, drives: List<Drive>): String {
        val t = totals(drives)
        val required = OregonGdl.requiredHours(driver)
        val supervisors = drives.map { it.supervisor.trim() }.filter { it.isNotEmpty() }.distinct()
        return buildString {
            appendLine("Supervised driving log — ${driver.name}")
            appendLine("Born ${Format.date(driver.birthDate)}")
            driver.permitDate?.let { appendLine("Instruction permit issued ${Format.date(it)}") }
            appendLine()
            appendLine("Total: ${Format.hours(t.minutes)} over ${t.drives} drives (required: $required h)")
            appendLine("Night: ${Format.hours(t.nightMinutes)}")
            if (t.byCondition.isNotEmpty()) {
                appendLine("Experience:")
                t.byCondition.forEach { (c, m) -> appendLine("  • ${c.label}: ${Format.hours(m)}") }
            }
            if (supervisors.isNotEmpty()) appendLine("Supervisors: ${supervisors.joinToString(", ")}")
            appendLine(
                if (driver.driverEd) "Took an ODOT-approved traffic safety education course."
                else "No ODOT-approved driver education course.",
            )
        }
    }

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\""
        else value
}
