package app.roadready.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.roadready.core.AppState
import app.roadready.core.HomeCity
import app.roadready.core.OregonGdl
import app.roadready.core.RulesOfTheRoad
import app.roadready.ui.Avatar
import app.roadready.ui.SectionCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: AppState,
    onCity: (HomeCity) -> Unit,
    onEditDriver: (String) -> Unit,
    onAddDriver: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Drivers") {
                state.drivers.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onEditDriver(d.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(d)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name)
                            Text(OregonGdl.stage(d, LocalDate.now()).label, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${d.name}")
                    }
                }
                OutlinedButton(onClick = onAddDriver) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add a driver")
                }
            }
            SectionCard("Home city") {
                Text(
                    "Used to work out sunset and sunrise, so each drive's night minutes are counted automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HomeCity.entries.forEach { city ->
                        FilterChip(selected = state.homeCity == city, onClick = { onCity(city) }, label = { Text(city.label) })
                    }
                }
            }
            SectionCard("About") {
                Text("RoadReady Oregon helps families teach teens to drive under Oregon's graduated licensing rules.")
                Text(
                    "Everything stays on this phone: no account, no internet access. Use Share on the Log tab to " +
                        "send a driving log as a spreadsheet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(RulesOfTheRoad.DISCLAIMER, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
