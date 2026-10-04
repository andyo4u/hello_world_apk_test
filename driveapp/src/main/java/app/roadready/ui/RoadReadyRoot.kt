package app.roadready.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.roadready.core.Driver
import app.roadready.core.QuizMode
import app.roadready.core.SunClock
import app.roadready.data.StateStore
import app.roadready.ui.screens.DriveEditScreen
import app.roadready.ui.screens.DriverEditScreen
import app.roadready.ui.screens.HomeScreen
import app.roadready.ui.screens.LogScreen
import app.roadready.ui.screens.QuizRunScreen
import app.roadready.ui.screens.QuizScreen
import app.roadready.ui.screens.SettingsScreen
import app.roadready.ui.screens.SkillsScreen
import app.roadready.ui.screens.StudyScreen
import app.roadready.ui.screens.TopicScreen
import app.roadready.ui.screens.WelcomeScreen
import kotlinx.coroutines.launch
import java.util.UUID

object Routes {
    const val HOME = "home"
    const val LOG = "log"
    const val STUDY = "study"
    const val QUIZ = "quiz"
    const val SKILLS = "skills"
    const val SETTINGS = "settings"
    const val DRIVER = "driver/{id}"
    const val DRIVE = "drive/{id}"
    const val TOPIC = "topic/{id}"
    const val QUIZ_RUN = "quizrun/{mode}/{topic}"
    const val NEW = "new"
    fun driver(id: String = NEW) = "driver/${Uri.encode(id)}"
    fun drive(id: String = NEW) = "drive/${Uri.encode(id)}"
    fun topic(id: String) = "topic/${Uri.encode(id)}"
    fun quizRun(mode: QuizMode, topic: String? = null) = "quizrun/${mode.name}/${Uri.encode(topic ?: "-")}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.LOG, "Log", Icons.Default.DirectionsCar),
    Tab(Routes.STUDY, "Study", Icons.AutoMirrored.Filled.MenuBook),
    Tab(Routes.QUIZ, "Quiz", Icons.Default.Quiz),
    Tab(Routes.SKILLS, "Skills", Icons.Default.Checklist),
)

@Composable
fun RoadReadyRoot(store: StateStore, shareLog: (Driver) -> Unit) {
    val state by store.flow.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val driver = state.selectedDriver
    val onTab = driver != null && tabs.any { it.route == route }

    Scaffold(
        topBar = {
            if (onTab && driver != null) {
                DriverTopBar(
                    drivers = state.drivers,
                    selected = driver,
                    onSelect = { id -> store.edit { it.select(id) } },
                    onAdd = { nav.navigate(Routes.driver()) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onShare = if (route == Routes.LOG) ({ shareLog(driver) }) else null,
                )
            }
        },
        bottomBar = { if (onTab) BottomTabs(nav, route) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.HOME) {
                if (driver == null) {
                    WelcomeScreen(onAddDriver = { nav.navigate(Routes.driver()) })
                } else {
                    HomeScreen(
                        state = state,
                        driver = driver,
                        onStartDrive = { supervisor ->
                            store.edit { it.startDrive(driver.id, System.currentTimeMillis(), supervisor) }
                        },
                        onCancelDrive = { store.edit { it.cancelDrive(driver.id) } },
                        onFinishDrive = {
                            val id = UUID.randomUUID().toString()
                            store.edit { it.finishDrive(driver.id, System.currentTimeMillis(), id, SunClock(it.homeCity)) }
                            if (store.value.drives.any { it.id == id }) {
                                nav.navigate(Routes.drive(id))
                            } else {
                                scope.launch { snackbar.showSnackbar("Drive was under a minute, so it wasn't logged") }
                            }
                        },
                        onOpenQuiz = { nav.navigate(Routes.QUIZ) { launchSingleTop = true } },
                        onOpenSkills = { nav.navigate(Routes.SKILLS) { launchSingleTop = true } },
                        onEditDriver = { nav.navigate(Routes.driver(driver.id)) },
                    )
                }
            }
            composable(Routes.LOG) {
                if (driver != null) {
                    LogScreen(
                        state = state,
                        driver = driver,
                        onAdd = { nav.navigate(Routes.drive()) },
                        onOpen = { nav.navigate(Routes.drive(it)) },
                    )
                }
            }
            composable(Routes.STUDY) {
                if (driver != null) StudyScreen(state, driver, onOpenTopic = { nav.navigate(Routes.topic(it)) })
            }
            composable(Routes.QUIZ) {
                if (driver != null) {
                    QuizScreen(state, driver, onStart = { mode, topic -> nav.navigate(Routes.quizRun(mode, topic)) })
                }
            }
            composable(Routes.SKILLS) {
                if (driver != null) {
                    SkillsScreen(state, driver, onSet = { skill, level -> store.edit { it.setSkill(driver.id, skill, level) } })
                }
            }
            composable(Routes.TOPIC) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                TopicScreen(
                    topicId = id,
                    stats = driver?.let { state.masteryFor(it.id) }.orEmpty(),
                    onBack = { nav.popBackStack() },
                    onPractice = { nav.navigate(Routes.quizRun(QuizMode.TOPIC, id)) },
                )
            }
            composable(Routes.QUIZ_RUN) { entry ->
                val mode = runCatching { QuizMode.valueOf(entry.arguments?.getString("mode").orEmpty()) }.getOrDefault(QuizMode.PRACTICE)
                val topic = entry.arguments?.getString("topic")?.takeIf { it != "-" }
                if (driver != null) {
                    QuizRunScreen(
                        driver = driver,
                        mode = mode,
                        topicId = topic,
                        stats = state.masteryFor(driver.id),
                        onAnswer = { qid, correct -> store.edit { it.recordAnswer(driver.id, qid, correct) } },
                        onFinish = { attempt -> store.edit { it.recordAttempt(attempt) } },
                        onClose = { nav.popBackStack() },
                    )
                }
            }
            composable(Routes.DRIVE) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                if (driver != null) {
                    DriveEditScreen(
                        state = state,
                        driver = driver,
                        driveId = id.takeIf { it != Routes.NEW },
                        onSave = { drive -> store.edit { it.saveDrive(drive) }; nav.popBackStack() },
                        onDelete = { driveId -> store.edit { it.deleteDrive(driveId) }; nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable(Routes.DRIVER) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                DriverEditScreen(
                    existing = state.drivers.firstOrNull { it.id == id },
                    onSave = { d -> store.edit { it.upsertDriver(d) }; nav.popBackStack() },
                    onDelete = { d -> store.edit { it.removeDriver(d.id) }; nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = state,
                    onCity = { city -> store.edit { it.copy(homeCity = city) } },
                    onEditDriver = { nav.navigate(Routes.driver(it)) },
                    onAddDriver = { nav.navigate(Routes.driver()) },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverTopBar(
    drivers: List<Driver>,
    selected: Driver,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onShare: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(
                Modifier.clickable { menu = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(selected, 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(selected.name)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch driver")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                drivers.forEach { d ->
                    DropdownMenuItem(
                        text = { Text(d.name) },
                        leadingIcon = { Avatar(d, 24.dp) },
                        onClick = { menu = false; onSelect(d.id) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Add a driver") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { menu = false; onAdd() },
                )
            }
        },
        actions = {
            if (onShare != null) IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share log") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        },
    )
}

@Composable
private fun BottomTabs(nav: NavHostController, route: String?) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = route == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
