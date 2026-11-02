package io.github.gumwoo.aiinterview.presentation.home

import androidx.compose.foundation.clickable
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
import io.github.gumwoo.aiinterview.data.InterviewSettings
import io.github.gumwoo.aiinterview.presentation.settings.difficultyLabel
import io.github.gumwoo.aiinterview.presentation.theme.CallColors
import io.github.gumwoo.aiinterview.presentation.theme.PillButton

@Composable
fun HomeScreen(settings: InterviewSettings, canStart: Boolean, onStart: () -> Unit, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    stringResource(R.string.home_settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = CallColors.Muted,
                    modifier = Modifier
                        .clickable(onClick = onOpenSettings)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(64.dp))
            Text(
                stringResource(R.string.home_label),
                style = MaterialTheme.typography.labelMedium,
                color = CallColors.Muted,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(40.dp))
            DetailRow(stringResource(R.string.home_row_topics), stringResource(R.string.home_topics_count, settings.topics.size))
            DetailRow(stringResource(R.string.settings_difficulty), stringResource(difficultyLabel(settings.difficulty)))
            DetailRow(stringResource(R.string.settings_duration), stringResource(R.string.home_duration_value, settings.durationMinutes))
            DetailRow(
                stringResource(R.string.home_row_server),
                stringResource(if (canStart) R.string.home_server_ready else R.string.home_server_missing),
            )
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
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = CallColors.Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
