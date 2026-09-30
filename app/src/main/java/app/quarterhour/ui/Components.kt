package app.quarterhour.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quarterhour.budget.BudgetUi
import app.quarterhour.ui.theme.Amber

/** Remaining time as a ring with mm:ss inside. */
@Composable
fun BudgetRing(ui: BudgetUi, modifier: Modifier = Modifier) {
    val fraction = if (ui.limitMillis == 0L) 0f else ui.remainingMillis.toFloat() / ui.limitMillis
    val label = formatClock(ui.remainingMillis)
    Box(
        modifier.size(44.dp).semantics { contentDescription = "$label of reading time left today" },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { fraction },
            modifier = Modifier.size(40.dp),
            color = if (ui.remainingMillis <= 2 * 60_000) MaterialTheme.colorScheme.error else Amber,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 4.dp,
        )
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun formatClock(millis: Long): String {
    val total = (millis + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/**
 * Wraps a screen whose time counts against the daily budget. When the budget
 * is used up, the lockout replaces the content.
 */
@Composable
fun Counted(
    ui: BudgetUi,
    onEnter: () -> Unit,
    onExit: () -> Unit,
    readToday: List<String>,
    onOpenProfile: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (ui.exhausted) {
        LockoutScreen(ui.millisUntilReset, readToday, onOpenProfile)
    } else {
        DisposableEffect(Unit) {
            onEnter()
            onDispose { onExit() }
        }
        content()
    }
}

@Composable
fun LockoutScreen(millisUntilReset: Long, readToday: List<String>, onOpenProfile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp).testTag("lockout"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("See you tomorrow", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "You've used today's 15 minutes. New stories and posts in ${formatDuration(millisUntilReset)}.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (readToday.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text("Today you read", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            readToday.take(10).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onOpenProfile) { Text("View your reader profile") }
    }
}
