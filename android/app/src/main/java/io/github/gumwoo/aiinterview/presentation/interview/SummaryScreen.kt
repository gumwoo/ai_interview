package io.github.gumwoo.aiinterview.presentation.interview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.gumwoo.aiinterview.R
import io.github.gumwoo.aiinterview.data.SummaryResponse
import io.github.gumwoo.aiinterview.presentation.theme.CallColors
import io.github.gumwoo.aiinterview.presentation.theme.PillButton
import io.github.gumwoo.aiinterview.presentation.theme.PointList

private val VERDICT_ORDER = listOf("CORRECT", "PARTIAL", "WRONG", "UNDETERMINED")

/** 통화가 끝난 뒤의 기록. */
@Composable
fun SummaryScreen(summary: SummaryResponse?, durationSeconds: Long, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(48.dp))
                Text(
                    stringResource(R.string.summary_duration, durationSeconds / 60, durationSeconds % 60),
                    style = MaterialTheme.typography.labelMedium,
                    color = CallColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (summary != null) {
                    Spacer(Modifier.height(36.dp))
                    Row(Modifier.fillMaxWidth()) {
                        VERDICT_ORDER
                            .filter { it != "UNDETERMINED" || (summary.verdictCounts[it] ?: 0) > 0 }
                            .forEach { verdict ->
                                VerdictCount(verdict, summary.verdictCounts[verdict] ?: 0, Modifier.weight(1f))
                            }
                    }
                    Spacer(Modifier.height(36.dp))
                    Text(stringResource(R.string.summary_review), style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
                    Spacer(Modifier.height(10.dp))
                    val points = (summary.incorrectPoints + summary.missingPoints).distinct()
                    if (points.isEmpty()) {
                        Text(stringResource(R.string.summary_nothing), style = MaterialTheme.typography.bodyMedium, color = CallColors.Muted)
                    } else {
                        PointList(points)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            PillButton(stringResource(R.string.summary_done), onDone)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun VerdictCount(verdict: String, count: Long, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(verdictLabel(verdict)), style = MaterialTheme.typography.labelMedium, color = verdictColor(verdict))
    }
}
