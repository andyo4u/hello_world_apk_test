package app.roadready.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadready.core.AppState
import app.roadready.core.Driver
import app.roadready.core.SkillLevel
import app.roadready.core.Skills
import app.roadready.ui.LabeledProgress
import app.roadready.ui.SectionCard
import app.roadready.ui.theme.Amber
import app.roadready.ui.theme.Good

@Composable
fun SkillsScreen(state: AppState, driver: Driver, onSet: (skillId: String, SkillLevel) -> Unit) {
    val levels = state.skillsFor(driver.id)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard("${driver.name}'s behind-the-wheel skills") {
                val progress = Skills.progress(levels)
                LabeledProgress("Curriculum", "$progress%", progress / 100f, color = Good)
                Text(
                    "Work top to bottom. Tap a skill's level to move it along: " +
                        SkillLevel.entries.joinToString(" → ") { it.label } + ".",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Skills.groups.forEach { group ->
            item(key = group.title) {
                Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp))
            }
            items(group.skills, key = { it.id }) { skill ->
                val level = levels[skill.id] ?: SkillLevel.NOT_STARTED
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(skill.title, fontWeight = FontWeight.Medium)
                        Text(skill.tip, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = { onSet(skill.id, level.next()) },
                        label = { Text(level.label) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = levelColor(level)),
                    )
                }
            }
        }
    }
}

@Composable
private fun levelColor(level: SkillLevel): Color = when (level) {
    SkillLevel.NOT_STARTED -> Color.Transparent
    SkillLevel.INTRODUCED -> MaterialTheme.colorScheme.surfaceVariant
    SkillLevel.PRACTICING -> Amber.copy(alpha = 0.35f)
    SkillLevel.CONFIDENT -> Good.copy(alpha = 0.3f)
}
