package io.github.gumwoo.aiinterview.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.gumwoo.aiinterview.R
import io.github.gumwoo.aiinterview.data.HistoryItem
import io.github.gumwoo.aiinterview.data.SessionTurnDetail
import io.github.gumwoo.aiinterview.presentation.interview.verdictColor
import io.github.gumwoo.aiinterview.presentation.theme.CallColors
import io.github.gumwoo.aiinterview.presentation.theme.SmallButton
import io.github.gumwoo.aiinterview.presentation.theme.keepWords
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SOURCE_MANUAL = "MANUAL"
private const val VERDICT_CORRECT = "CORRECT"
private const val VERDICT_PARTIAL = "PARTIAL"
private const val VERDICT_WRONG = "WRONG"
private const val STATUS_GRADED = "GRADED"

private val KOREAN_CLOCK = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val KOREAN_DAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

/** 지난 면접을 전화 앱의 통화 기록처럼 보여 준다. */
@Composable
fun HistoryScreen(
    state: HistoryState,
    onOpen: (HistoryItem) -> Unit,
    onCloseDetail: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val selected = state.selected
    BackHandler(onBack = if (selected != null) onCloseDetail else onBack)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selected != null) dateLabel(selected.startedAt) else stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                SmallButton(stringResource(R.string.settings_close), if (selected != null) onCloseDetail else onBack)
            }
            when {
                selected != null -> HistoryDetail(selected, state, onDelete, onRetry)
                state.failed -> FailedMessage(onRetry)
                state.items.isEmpty() && !state.loading -> Message(stringResource(R.string.history_empty))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.sessionId }) { item ->
                        HistoryRow(item, onClick = { onOpen(item) })
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        Text(dateLabel(item.startedAt), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        Text(metaLabel(item), style = MaterialTheme.typography.bodySmall, color = CallColors.Muted)
        val counts = countsLabel(item)
        if (counts.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(counts, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryDetail(item: HistoryItem, state: HistoryState, onDelete: () -> Unit, onRetry: () -> Unit) {
    val turns = state.detail?.turns.orEmpty().filter { it.processingStatus == STATUS_GRADED }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "summary") {
            Text(
                buildAnnotatedString {
                    append(metaLabel(item))
                    val counts = countsLabel(item)
                    if (counts.isNotEmpty()) {
                        append(" · ")
                        append(counts)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = CallColors.Muted,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (state.failed) {
            item(key = "failed") { FailedMessage(onRetry) }
        }
        items(turns, key = { it.turnId }) { turn ->
            TurnBlock(turn)
            Divider()
        }
        if (state.detail != null) {
            item(key = "delete") {
                Spacer(Modifier.height(16.dp))
                if (state.confirmingDelete) {
                    Text(
                        stringResource(R.string.history_delete_notice).keepWords(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CallColors.Muted,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SmallButton(
                    stringResource(if (state.confirmingDelete) R.string.history_delete_confirm else R.string.history_delete),
                    onDelete,
                    color = CallColors.End,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun TurnBlock(turn: SessionTurnDetail) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(turnLabel(turn), style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
        Spacer(Modifier.height(4.dp))
        Text(turn.questionText.keepWords(), style = MaterialTheme.typography.bodyLarge)
        turn.answerText?.let { answer ->
            Spacer(Modifier.height(6.dp))
            Text(answer.keepWords(), style = MaterialTheme.typography.bodyMedium, color = CallColors.Muted)
        }
        val feedback = turn.feedbackToSpeak
        if (turn.verdict != VERDICT_CORRECT && !feedback.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(feedback.keepWords(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CallColors.Control))
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = CallColors.Muted,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun FailedMessage(onRetry: () -> Unit) {
    Column {
        Message(stringResource(R.string.history_failed))
        SmallButton(stringResource(R.string.history_retry), onRetry)
    }
}

@Composable
private fun dateLabel(startedAt: String): String {
    val started = Instant.parse(startedAt).atZone(ZoneId.systemDefault())
    val day = when (started.toLocalDate()) {
        LocalDate.now() -> stringResource(R.string.home_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.history_yesterday)
        else -> started.format(KOREAN_DAY)
    }
    return "$day ${started.format(KOREAN_CLOCK)}"
}

@Composable
private fun metaLabel(item: HistoryItem): String {
    val parts = buildList {
        if (item.source == SOURCE_MANUAL) add(stringResource(R.string.history_manual))
        item.endedAt?.let { endedAt ->
            val seconds = Duration.between(Instant.parse(item.startedAt), Instant.parse(endedAt)).seconds
            add(stringResource(R.string.home_duration_value, ((seconds + 59) / 60).coerceAtLeast(1).toInt()))
        }
        add(stringResource(R.string.history_questions, item.answeredCount))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun countsLabel(item: HistoryItem): AnnotatedString {
    val parts = listOf(
        VERDICT_CORRECT to R.string.history_count_correct,
        VERDICT_PARTIAL to R.string.history_count_partial,
        VERDICT_WRONG to R.string.history_count_wrong,
    ).mapNotNull { (verdict, label) ->
        val count = item.verdictCounts[verdict] ?: 0
        if (count > 0) verdict to stringResource(label, count) else null
    }
    return buildAnnotatedString {
        parts.forEachIndexed { index, (verdict, text) ->
            if (index > 0) withStyle(SpanStyle(color = CallColors.Muted)) { append(" · ") }
            withStyle(SpanStyle(color = verdictColor(verdict))) { append(text) }
        }
    }
}

@Composable
private fun turnLabel(turn: SessionTurnDetail): AnnotatedString {
    val verdict = turn.verdict
    val label = when {
        verdict == VERDICT_CORRECT && turn.review -> stringResource(R.string.history_verdict_review_correct)
        verdict == VERDICT_CORRECT -> stringResource(R.string.history_verdict_correct)
        verdict == VERDICT_PARTIAL -> stringResource(R.string.history_verdict_partial)
        verdict == VERDICT_WRONG -> stringResource(R.string.history_verdict_wrong)
        else -> stringResource(R.string.verdict_undetermined)
    }
    return buildAnnotatedString {
        if (turn.review) {
            append(stringResource(R.string.history_review))
            append(" · ")
        }
        withStyle(SpanStyle(color = verdictColor(verdict.orEmpty()))) { append(label) }
    }
}
