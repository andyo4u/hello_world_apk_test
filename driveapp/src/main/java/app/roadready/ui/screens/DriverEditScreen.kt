package app.roadready.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.roadready.core.Driver
import app.roadready.core.OregonGdl
import app.roadready.ui.Avatar
import app.roadready.ui.DateField
import app.roadready.ui.DriverColors
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverEditScreen(existing: Driver?, onSave: (Driver) -> Unit, onDelete: (Driver) -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var birth by remember { mutableStateOf(existing?.birthDate) }
    var permit by remember { mutableStateOf(existing?.permitDate) }
    var provisional by remember { mutableStateOf(existing?.provisionalDate) }
    var driverEd by rememberSaveable { mutableStateOf(existing?.driverEd ?: false) }
    var driveTest by rememberSaveable { mutableStateOf(existing?.passedDriveTest ?: false) }
    var color by rememberSaveable { mutableLongStateOf(existing?.color ?: DriverColors.first()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val error = when {
        name.isBlank() -> "Enter a name"
        birth == null -> "Pick a birthday"
        birth!!.isAfter(today) -> "Birthday can't be in the future"
        permit != null && permit!!.isBefore(birth!!.plusYears(OregonGdl.PERMIT_AGE.toLong())) ->
            "Oregon permits start at ${OregonGdl.PERMIT_AGE} — check the permit date"
        provisional != null && permit == null -> "Add the permit date too"
        provisional != null && provisional!!.isBefore(permit!!) -> "The license date is before the permit date"
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add a driver" else "Edit ${existing.name}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Remove driver") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DriverColors.forEach { c ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = c },
                    ) {
                        Avatar(Driver("", name.ifBlank { " " }, LocalDate.MIN, color = c), 36.dp)
                    }
                }
            }
            DateField("Birthday", birth, onChange = { birth = it }, typeable = true)
            Text(
                "Leave these empty until they happen. They decide which Oregon rules apply.",
                style = MaterialTheme.typography.bodySmall,
            )
            DateField("Instruction permit issued", permit, onChange = { permit = it }, clearable = true)
            DateField("Provisional license issued", provisional, onChange = { provisional = it }, clearable = true)
            SwitchRow(
                "ODOT-approved driver education",
                "Enrolled or finished. Lowers the hours needed from 100 to 50.",
                driverEd,
            ) { driverEd = it }
            SwitchRow("Passed the drive test", null, driveTest) { driveTest = it }
            if (error != null && name.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    onSave(
                        Driver(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            birthDate = birth ?: return@Button,
                            color = color,
                            permitDate = permit,
                            provisionalDate = provisional,
                            driverEd = driverEd,
                            passedDriveTest = driveTest,
                        ),
                    )
                },
                enabled = error == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${existing.name}?") },
            text = { Text("This deletes their driving log, quiz progress and skills from this phone. Share the log first if you need it.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(existing) }) { Text("Remove", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
