package io.github.gumwoo.aiinterview.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.gumwoo.aiinterview.R
import io.github.gumwoo.aiinterview.data.InterviewSchedule
import io.github.gumwoo.aiinterview.data.InterviewSettings
import io.github.gumwoo.aiinterview.presentation.schedule.daysLabel
import io.github.gumwoo.aiinterview.presentation.settings.difficultyLabel
import io.github.gumwoo.aiinterview.presentation.theme.CallColors
import io.github.gumwoo.aiinterview.presentation.theme.PillButton
import io.github.gumwoo.aiinterview.presentation.theme.SmallButton
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val KOREAN_CLOCK = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

@Composable
fun HomeScreen(
    settings: InterviewSettings,
    schedule: InterviewSchedule,
    canStart: Boolean,
    onStart: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val now = ZonedDateTime.now()
    val next = schedule.nextOccurrence(now)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.home_label), style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
                SmallButton(stringResource(R.string.home_settings), onOpenSettings)
            }
            Spacer(Modifier.height(56.dp))
            Text(stringResource(R.string.home_next_call), style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
            Spacer(Modifier.height(8.dp))
            Text(
                if (next == null) stringResource(R.string.home_no_call) else nextCallLabel(next, now),
                style = MaterialTheme.typography.displaySmall,
            )
            if (next != null) {
                Spacer(Modifier.height(6.dp))
                Text(schedule.daysLabel(), style = MaterialTheme.typography.bodyMedium, color = CallColors.Muted)
            }
            Spacer(Modifier.height(36.dp))
            DetailRow(
                label = stringResource(R.string.schedule_title),
                value = stringResource(if (next == null) R.string.home_schedule_set else R.string.home_schedule_change),
                onClick = onOpenSchedule,
            )
            DetailRow(stringResource(R.string.home_row_topics), stringResource(R.string.home_topics_count, settings.topics.size))
            DetailRow(
                stringResource(R.string.settings_difficulty),
                stringResource(difficultyLabel(settings.difficulty)),
            )
            DetailRow(stringResource(R.string.settings_duration), stringResource(R.string.home_duration_value, settings.durationMinutes))
            if (!canStart) {
                DetailRow(stringResource(R.string.home_row_server), stringResource(R.string.home_server_missing))
            }
            Spacer(Modifier.weight(1f))
            if (canStart) {
                PillButton(stringResource(R.string.home_start), onStart)
            } else {
                PillButton(stringResource(R.string.home_setup), onOpenSettings)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun nextCallLabel(next: ZonedDateTime, now: ZonedDateTime): String {
    val day = when (ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate())) {
        0L -> stringResource(R.string.home_today)
        1L -> stringResource(R.string.home_tomorrow)
        else -> next.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    }
    return "$day ${next.format(KOREAN_CLOCK)}"
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = CallColors.Muted)
        if (onClick == null) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
        } else {
            SmallButton(value, onClick)
        }
    }
}
