package app.roadready.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.roadready.core.AppState
import app.roadready.core.Condition
import app.roadready.core.Drive
import app.roadready.core.DriveLog
import app.roadready.core.Driver
import app.roadready.core.Format
import app.roadready.core.SunClock
import app.roadready.ui.ConditionChips
import app.roadready.ui.DateField
import app.roadready.ui.SectionCard
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

@Composable
fun LogScreen(state: AppState, driver: Driver, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val drives = state.drivesFor(driver.id)
    val totals = DriveLog.totals(drives)
    val zone = ZoneId.of(state.homeCity.zoneId)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionCard("${driver.name}'s log") {
                    Text("${Format.hours(totals.minutes)} total · ${Format.hours(totals.nightMinutes)} at night · ${totals.drives} drives")
                    if (totals.byCondition.isNotEmpty()) {
                        Text(
                            totals.byCondition.entries.joinToString(" · ") { "${it.key.label} ${Format.hours(it.value)}" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Share (top right) sends a CSV plus a summary for the DMV certification form.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (drives.isEmpty()) {
                item {
                    Text(
                        "No drives yet. Start the timer on the Home tab, or add a past drive with the + button.",
                        Modifier.padding(16.dp),
                    )
                }
            }
            items(drives, key = { it.id }) { d -> DriveRow(d, zone, onClick = { onOpen(d.id) }) }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Add drive") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun DriveRow(d: Drive, zone: ZoneId, onClick: () -> Unit) {
    val start = Instant.ofEpochMilli(d.startMillis).atZone(zone)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(start.format(dayFormat), fontWeight = FontWeight.SemiBold)
                Text(Format.hours(d.minutes), fontWeight = FontWeight.SemiBold)
            }
            val details = buildList {
                add(start.format(timeFormat))
                if (d.nightMinutes > 0) add("${Format.hours(d.nightMinutes)} night")
                if (d.supervisor.isNotBlank()) add("with ${d.supervisor}")
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (d.conditions.isNotEmpty()) {
                Text(
                    d.conditions.sortedBy { it.ordinal }.joinToString(" · ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (d.notes.isNotBlank()) Text(d.notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Add a past drive, or edit one (including one just finished with the timer). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DriveEditScreen(
    state: AppState,
    driver: Driver,
    driveId: String?,
    onSave: (Drive) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val existing = driveId?.let { id -> state.drives.firstOrNull { it.id == id } }
    val zone = ZoneId.of(state.homeCity.zoneId)
    val initialStart = existing?.let { Instant.ofEpochMilli(it.startMillis).atZone(zone) }
        ?: ZonedDateTime.now(zone).minusHours(1).withSecond(0).withNano(0)

    var date by remember { mutableStateOf(initialStart.toLocalDate()) }
    var time by remember { mutableStateOf(initialStart.toLocalTime().withSecond(0).withNano(0)) }
    var minutes by rememberSaveable { mutableStateOf(existing?.minutes?.toString() ?: "60") }
    var supervisor by rememberSaveable { mutableStateOf(existing?.supervisor ?: state.supervisors.lastOrNull().orEmpty()) }
    var conditions by remember { mutableStateOf(existing?.conditions ?: emptySet()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val duration = minutes.toIntOrNull()?.takeIf { it in 1..(24 * 60) }
    val startMillis = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
    val night = duration?.let { SunClock(state.homeCity).nightMinutes(startMillis, it) } ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add a drive" else "Edit drive") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete drive") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Driver: ${driver.name}", style = MaterialTheme.typography.titleMedium)
            DateField("Date", date, onChange = { if (it != null) date = it })
            OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Started at ${time.format(timeFormat)}")
            }
            OutlinedTextField(
                value = minutes,
                onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(4) },
                label = { Text("Minutes") },
                isError = duration == null,
                supportingText = {
                    Text(
                        if (duration == null) "Enter 1 to 1440 minutes"
                        else "${Format.hours(duration)}, of which ${Format.hours(night)} after sunset (${state.homeCity.label})",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = supervisor,
                onValueChange = { supervisor = it },
                label = { Text("Supervising driver (21+, licensed 3+ years)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.supervisors.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.supervisors.forEach { name ->
                        FilterChip(selected = supervisor == name, onClick = { supervisor = name }, label = { Text(name) })
                    }
                }
            }
            Text("What did you practice?", style = MaterialTheme.typography.titleSmall)
            ConditionChips(conditions) { c -> conditions = if (c in conditions) conditions - c else conditions + c }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes — what went well, what to work on") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val m = duration ?: return@Button
                    onSave(
                        Drive(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            driverId = driver.id,
                            startMillis = startMillis,
                            minutes = m,
                            nightMinutes = night,
                            supervisor = supervisor.trim(),
                            conditions = conditions,
                            notes = notes.trim(),
                        ),
                    )
                },
                enabled = duration != null && !date.isAfter(LocalDate.now(zone)),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save drive") }
        }
    }

    if (pickTime) {
        val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(picker.hour, picker.minute); pickTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = picker) },
        )
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this drive?") },
            text = { Text("${Format.hours(existing.minutes)} will be removed from ${driver.name}'s log.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(existing.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
