package app.quarterhour.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Shows user_profile.md. Viewing it doesn't use reading time. */
@Composable
fun ProfileScreen(markdown: String, onShare: () -> Unit, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        markdown.lines().forEach { line -> MarkdownLine(line) }
        Row(Modifier.padding(top = 24.dp)) {
            OutlinedButton(onClick = onShare) { Text("Share file") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { confirm = true }) { Text("Forget everything") }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset your profile?") },
            text = { Text("QuarterHour will forget what you like and start learning again. Your chosen topics are kept.") },
            confirmButton = { TextButton(onClick = { confirm = false; onReset() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Minimal Markdown: headings, bullets, **bold**, _italic_ lines. */
@Composable
private fun MarkdownLine(line: String) {
    when {
        line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp))
        line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        line.startsWith("_") && line.endsWith("_") -> Text(line.trim('_'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        line.isBlank() -> Unit
        else -> Text(bold(line), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun bold(line: String): AnnotatedString = buildAnnotatedString {
    val parts = line.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
