package app.roadready.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.roadready.core.ActiveDrive
import app.roadready.core.AppState
import app.roadready.core.DriveLog
import app.roadready.core.Driver
import app.roadready.core.Format
import app.roadready.core.OregonGdl
import app.roadready.core.QuizEngine
import app.roadready.core.QuizMode
import app.roadready.core.RulesOfTheRoad
import app.roadready.core.Skills
import app.roadready.core.Stage
import app.roadready.ui.Avatar
import app.roadready.ui.LabeledProgress
import app.roadready.ui.SectionCard
import app.roadready.ui.theme.Good
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun WelcomeScreen(onAddDriver: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.DirectionsCar, contentDescription = null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("RoadReady Oregon", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Log supervised hours, study Oregon's rules of the road, take practice tests and track " +
                "behind-the-wheel skills — for each of your teens.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddDriver) { Text("Add your first driver") }
        Spacer(Modifier.height(24.dp))
        Text(RulesOfTheRoad.DISCLAIMER, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun HomeScreen(
    state: AppState,
    driver: Driver,
    onStartDrive: (supervisor: String) -> Unit,
    onCancelDrive: () -> Unit,
    onFinishDrive: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenSkills: () -> Unit,
    onEditDriver: () -> Unit,
    today: LocalDate = LocalDate.now(ZoneId.of(state.homeCity.zoneId)),
) {
    val stage = OregonGdl.stage(driver, today)
    val totals = DriveLog.totals(state.drivesFor(driver.id))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(driver, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(driver.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Age ${OregonGdl.ageOn(driver, today)} · ${stage.label}", modifier = Modifier.testTag("stage"))
            }
            IconButton(onClick = onEditDriver) { Icon(Icons.Default.Edit, contentDescription = "Edit ${driver.name}") }
        }

        if (stage != Stage.PRE_PERMIT) {
            DriveTimerCard(
                active = state.activeDriveFor(driver.id),
                supervisors = state.supervisors,
                onStart = onStartDrive,
                onCancel = onCancelDrive,
                onFinish = onFinishDrive,
            )
        }

        if (stage == Stage.PERMIT || totals.minutes > 0) {
            val required = OregonGdl.requiredHours(driver)
            SectionCard("Supervised driving") {
                LabeledProgress(
                    "Total",
                    "${Format.hours(totals.minutes)} / $required h",
                    totals.minutes / (required * 60f),
                )
                LabeledProgress(
                    "Night (not required, but recommended)",
                    Format.hours(totals.nightMinutes),
                    if (totals.minutes == 0) 0f else totals.nightMinutes / totals.minutes.toFloat(),
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text("${totals.drives} drives logged", style = MaterialTheme.typography.bodySmall)
            }
        }

        val milestones = OregonGdl.milestones(driver, today, totals.minutes)
        if (milestones.isNotEmpty()) {
            SectionCard(
                when (stage) {
                    Stage.PRE_PERMIT -> "Steps to an instruction permit"
                    Stage.PERMIT -> "Steps to a provisional license" +
                        (OregonGdl.provisionalEligibleOn(driver)?.let { " (earliest ${Format.date(it)})" } ?: "")
                    else -> "Coming up"
                },
            ) {
                milestones.forEach { m ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            if (m.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (m.done) "Done" else "Not done",
                            tint = if (m.done) Good else MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(m.label, fontWeight = FontWeight.Medium)
                            Text(m.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        val restrictions = OregonGdl.restrictions(driver, today)
        if (restrictions.isNotEmpty()) {
            SectionCard("Rules for ${driver.name} right now") {
                restrictions.forEach { r ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(r.title, fontWeight = FontWeight.Medium)
                            Text(r.detail, style = MaterialTheme.typography.bodySmall)
                            r.endsOn?.let { Text("Until ${Format.date(it)}", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }

        val stats = state.masteryFor(driver.id)
        val lastMock = state.attemptsFor(driver.id).firstOrNull { it.mode == QuizMode.MOCK_EXAM }
        SectionCard("Knowledge test", Modifier.clickable(onClick = onOpenQuiz)) {
            LabeledProgress("Ready score", "${QuizEngine.readiness(stats)}%", QuizEngine.readiness(stats) / 100f)
            Text(
                lastMock?.let {
                    "Last mock test: ${it.correct}/${it.total} — " +
                        if (QuizEngine.passed(it.mode, it.correct, it.total)) "passed" else "not yet passing"
                } ?: "No mock test yet. The real one is ${OregonGdl.KNOWLEDGE_TEST_QUESTIONS} questions; ${OregonGdl.KNOWLEDGE_TEST_PASS} to pass.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val skills = Skills.progress(state.skillsFor(driver.id))
        SectionCard("Behind-the-wheel skills", Modifier.clickable(onClick = onOpenSkills)) {
            LabeledProgress("Curriculum", "$skills%", skills / 100f, color = Good)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriveTimerCard(
    active: ActiveDrive?,
    supervisors: List<String>,
    onStart: (String) -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active != null) {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(active.startMillis) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                val seconds = ((now - active.startMillis) / 1000).coerceAtLeast(0)
                Text("Drive in progress", style = MaterialTheme.typography.titleMedium)
                Text(
                    "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("timer"),
                )
                if (active.supervisor.isNotEmpty()) Text("Supervisor: ${active.supervisor}")
                Text("Eyes on the road — the timer keeps running if you close the app.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onFinish) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("End drive")
                    }
                    TextButton(onClick = onCancel) { Text("Discard") }
                }
            } else {
                var supervisor by rememberSaveable { mutableStateOf(supervisors.lastOrNull().orEmpty()) }
                Text("Start a supervised drive", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = supervisor,
                    onValueChange = { supervisor = it },
                    label = { Text("Supervising driver") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (supervisors.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        supervisors.forEach { name ->
                            FilterChip(selected = supervisor == name, onClick = { supervisor = name }, label = { Text(name) })
                        }
                    }
                }
                Box(Modifier.fillMaxWidth()) {
                    Button(onClick = { onStart(supervisor) }, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start drive")
                    }
                }
            }
        }
    }
}
