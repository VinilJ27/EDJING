package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ConsoleCharcoal
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBAmber
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.VuGreen
import com.example.viewmodel.DeckPanelMode
import com.example.viewmodel.DjUiState
import com.example.viewmodel.DjViewModel
import com.example.viewmodel.TutorialLesson

@Composable
fun TutorialsScreen(
    state: DjUiState,
    viewModel: DjViewModel,
    onPracticeInConsole: (DeckPanelMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val completedSet = state.settings.completedTutorialIds
        .split(',')
        .filter { it.isNotBlank() }
        .toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DJ School & Tutorials",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${completedSet.size} of ${state.tutorials.size} lessons mastered",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(Icons.Default.School, contentDescription = null, tint = DeckACyan, modifier = Modifier.size(28.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 310.dp),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(state.tutorials, key = { it.id }) { lesson ->
                TutorialCard(
                    lesson = lesson,
                    completed = completedSet.contains(lesson.id),
                    onToggleComplete = { viewModel.toggleTutorialComplete(lesson.id) },
                    onPractice = { onPracticeInConsole(lesson.targetPanelMode) }
                )
            }
        }
    }
}

@Composable
private fun TutorialCard(
    lesson: TutorialLesson,
    completed: Boolean,
    onToggleComplete: () -> Unit,
    onPractice: () -> Unit
) {
    val accent = Color(lesson.accentColorHex)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ConsoleCharcoal)
            .border(1.5.dp, if (completed) VuGreen else accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${lesson.level} • ${lesson.durationLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row {
                    repeat(3) { star ->
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (completed) DeckBAmber else TextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DeckSurface)
                    .clickable(onClick = onToggleComplete)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Mark lesson complete",
                    tint = if (completed) VuGreen else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (completed) "Done" else "Mark Done",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (completed) VuGreen else TextSecondary
                )
            }
        }

        Text(
            text = lesson.title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Text(
            text = lesson.description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DeckSurface)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lesson.steps.forEachIndexed { idx, step ->
                Text(
                    text = "${idx + 1}. $step",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }
        }

        Button(
            onClick = onPractice,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = VoidBlack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("practice_tutorial_${lesson.id}")
        ) {
            Text("Practice in DJ Console", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
