package io.github.gumwoo.aiinterview.presentation.interview

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.gumwoo.aiinterview.R
import io.github.gumwoo.aiinterview.presentation.theme.AdaptiveLayout
import io.github.gumwoo.aiinterview.presentation.theme.CallColors
import io.github.gumwoo.aiinterview.presentation.theme.CallControl
import io.github.gumwoo.aiinterview.presentation.theme.EndCallButton
import io.github.gumwoo.aiinterview.presentation.theme.PillButton
import io.github.gumwoo.aiinterview.presentation.theme.PointList
import io.github.gumwoo.aiinterview.presentation.theme.StatusDot
import io.github.gumwoo.aiinterview.presentation.theme.VoiceBars
import io.github.gumwoo.aiinterview.presentation.theme.keepWords
import kotlinx.coroutines.delay

/** 면접 통화 화면. 접으면 자막과 버튼이 한 단에, 펴면 왼쪽 통화와 오른쪽 대화 기록으로 나뉜다. */
@Composable
fun InterviewScreen(
    state: InterviewUiState,
    micGranted: Boolean,
    cloudRecognition: Boolean,
    viewModel: InterviewViewModel,
    onLeave: () -> Unit,
) {
    var confirmingEnd by rememberSaveable { mutableStateOf(false) }
    var captionsOn by rememberSaveable { mutableStateOf(true) }
    // 시작 전이거나 종료 요청까지 실패했으면 결과 없이 나간다.
    val leavesDirectly = state.sessionId == null || state.phase == InterviewPhase.ENDING
    val requestEnd = { if (leavesDirectly) onLeave() else confirmingEnd = true }
    val endEnabled = state.phase != InterviewPhase.ENDING || state.error != null

    BackHandler(onBack = requestEnd)

    AdaptiveLayout { wide ->
        Box(Modifier.fillMaxSize()) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(bottom = 24.dp)) {
                        CallHeader(state, large = true)
                        Spacer(Modifier.weight(1f))
                        CallActions(state, captionsOn, { captionsOn = !captionsOn }, viewModel, requestEnd, endEnabled)
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(CallColors.Control))
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    ) {
                        HistoryLines(state)
                        CaptionContent(state, captionsOn)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(bottom = 24.dp)) {
                    CallHeader(state, large = false)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    ) {
                        CaptionContent(state, captionsOn)
                    }
                    CallActions(state, captionsOn, { captionsOn = !captionsOn }, viewModel, requestEnd, endEnabled)
                }
            }
            if (state.phase == InterviewPhase.REVIEW) {
                ReviewSheet(
                    state = state,
                    micGranted = micGranted,
                    cloudRecognition = cloudRecognition,
                    viewModel = viewModel,
                    onEnd = requestEnd,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(if (wide) Modifier.fillMaxWidth(0.535f) else Modifier.fillMaxWidth()),
                )
            }
        }
    }

    if (confirmingEnd) {
        AlertDialog(
            onDismissRequest = { confirmingEnd = false },
            containerColor = CallColors.Sheet,
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.end_dialog_title), style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    stringResource(R.string.end_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CallColors.Muted,
                )
            },
            confirmButton = {
                SheetAction(stringResource(R.string.end_dialog_confirm), CallColors.End) {
                    confirmingEnd = false
                    viewModel.end()
                }
            },
            dismissButton = {
                SheetAction(stringResource(R.string.end_dialog_cancel), CallColors.Text) { confirmingEnd = false }
            },
        )
    }
}

@Composable
private fun CallHeader(state: InterviewUiState, large: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = if (large) 56.dp else 28.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(2.dp))
        CallTimer(state.callStartedAtMillis)
        Spacer(Modifier.height(14.dp))
        CallStatus(state)
    }
}

@Composable
private fun CallTimer(startedAtMillis: Long?) {
    val elapsed by produceState(0L, startedAtMillis) {
        while (startedAtMillis != null) {
            value = (SystemClock.elapsedRealtime() - startedAtMillis) / 1000
            delay(1000)
        }
    }
    Text(
        if (startedAtMillis == null) " " else "%02d:%02d".format(elapsed / 60, elapsed % 60),
        style = MaterialTheme.typography.labelMedium,
        color = CallColors.Muted,
    )
}

@Composable
private fun CallStatus(state: InterviewUiState) {
    val phase = state.phase
    val color = when (phase) {
        InterviewPhase.SPEAKING, InterviewPhase.FEEDBACK -> CallColors.Speaking
        InterviewPhase.LISTENING -> CallColors.Text
        else -> CallColors.Muted
    }
    Row(
        modifier = Modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (phase == InterviewPhase.SPEAKING || phase == InterviewPhase.FEEDBACK || phase == InterviewPhase.LISTENING) {
            VoiceBars(color)
        }
        val label = if (phase == InterviewPhase.CONNECTING && state.error != null) R.string.call_failed else statusLabel(phase)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** 지금 말하는 내용을 크게 보여 주는 자막. */
@Composable
private fun CaptionContent(state: InterviewUiState, captionsOn: Boolean) {
    state.error?.takeIf { state.phase != InterviewPhase.REVIEW }?.let { error ->
        Text(errorText(error), style = MaterialTheme.typography.bodyMedium, color = CallColors.End)
        Spacer(Modifier.height(16.dp))
    }
    if (!captionsOn) {
        Text(stringResource(R.string.call_captions_off), style = MaterialTheme.typography.bodyLarge, color = CallColors.Dim)
        return
    }
    when (state.phase) {
        InterviewPhase.FEEDBACK -> {
            state.verdict?.let { verdict ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(verdictColor(verdict))
                    Text(
                        stringResource(verdictLabel(verdict)),
                        style = MaterialTheme.typography.labelMedium,
                        color = verdictColor(verdict),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            SpokenCaption(state.feedback, state.spokenUpTo)
            if (state.missingPoints.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.call_missing), style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
                Spacer(Modifier.height(8.dp))
                PointList(state.missingPoints)
            }
        }

        InterviewPhase.LISTENING -> {
            Text(state.questionText.keepWords(), style = MaterialTheme.typography.bodyMedium, color = CallColors.Muted)
            Spacer(Modifier.height(18.dp))
            Text(
                state.transcript.ifEmpty { stringResource(R.string.call_listen_placeholder) }.keepWords(),
                style = MaterialTheme.typography.headlineSmall,
                color = if (state.transcript.isEmpty()) CallColors.Dim else CallColors.Text,
            )
        }

        InterviewPhase.GRADING ->
            Text(state.transcript.keepWords(), style = MaterialTheme.typography.headlineSmall, color = CallColors.Dim)

        else -> SpokenCaption(state.questionText, state.spokenUpTo)
    }
    state.hint?.let { hint ->
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.call_hint), style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
        Spacer(Modifier.height(4.dp))
        Text(hint.keepWords(), style = MaterialTheme.typography.bodyLarge, color = CallColors.Text)
    }
}

/** 읽은 문장은 밝게, 아직 읽지 않은 문장은 어둡게 보여 준다. */
@Composable
private fun SpokenCaption(text: String, spokenUpTo: Int?) {
    val annotated = remember(text, spokenUpTo) {
        val split = (spokenUpTo ?: text.length).coerceIn(0, text.length)
        buildAnnotatedString {
            withStyle(SpanStyle(color = CallColors.Text)) { append(text.substring(0, split).keepWords()) }
            withStyle(SpanStyle(color = CallColors.Dim)) { append(text.substring(split).keepWords()) }
        }
    }
    Text(annotated, style = MaterialTheme.typography.headlineSmall)
}

/** 펼친 화면 오른쪽에 쌓이는 지난 대화. 지금 말하는 줄은 자막으로 따로 보여 준다. */
@Composable
private fun HistoryLines(state: InterviewUiState) {
    val current = when (state.phase) {
        InterviewPhase.FEEDBACK -> state.feedback
        else -> state.questionText
    }
    val past = state.history.dropLastWhile { it.fromInterviewer && it.text == current }
    past.forEach { line ->
        Text(
            stringResource(if (line.fromInterviewer) R.string.call_interviewer else R.string.call_me),
            style = MaterialTheme.typography.bodySmall,
            color = CallColors.Muted,
        )
        Text(line.text.keepWords(), style = MaterialTheme.typography.bodyMedium, color = CallColors.Muted)
        Spacer(Modifier.height(16.dp))
    }
    if (past.isNotEmpty()) {
        Text(stringResource(R.string.call_interviewer), style = MaterialTheme.typography.bodySmall, color = CallColors.Muted)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CallActions(
    state: InterviewUiState,
    captionsOn: Boolean,
    onToggleCaptions: () -> Unit,
    viewModel: InterviewViewModel,
    onEnd: () -> Unit,
    endEnabled: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when {
            state.retryAction != null && state.phase != InterviewPhase.REVIEW ->
                PillButton(stringResource(R.string.control_retry), viewModel::retry, Modifier.padding(horizontal = 24.dp))

            state.phase == InterviewPhase.LISTENING ->
                PillButton(stringResource(R.string.control_done_speaking), viewModel::stopListening, Modifier.padding(horizontal = 24.dp))
        }
        Spacer(Modifier.height(12.dp))
        val talking = state.phase == InterviewPhase.SPEAKING ||
            state.phase == InterviewPhase.LISTENING ||
            state.phase == InterviewPhase.FEEDBACK
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            CallControl(
                R.drawable.ic_replay,
                stringResource(R.string.control_replay),
                viewModel::replay,
                Modifier.weight(1f),
                enabled = talking,
            )
            if (state.phase == InterviewPhase.FEEDBACK) {
                CallControl(R.drawable.ic_skip, stringResource(R.string.control_skip), viewModel::skipSpeech, Modifier.weight(1f))
            } else {
                CallControl(
                    R.drawable.ic_hint,
                    stringResource(R.string.control_hint),
                    viewModel::requestHint,
                    Modifier.weight(1f),
                    enabled = talking,
                )
            }
            CallControl(
                R.drawable.ic_captions,
                stringResource(R.string.control_captions),
                onToggleCaptions,
                Modifier.weight(1f),
                active = captionsOn,
            )
            CallControl(
                R.drawable.ic_speaker,
                stringResource(R.string.control_speaker),
                viewModel::toggleSpeaker,
                Modifier.weight(1f),
                active = state.speakerOn,
            )
        }
        Spacer(Modifier.height(18.dp))
        EndCallButton(onEnd, enabled = endEnabled)
    }
}

/** 말을 끝내면 아래에서 올라와, 잘못 들은 부분을 고치고 보낸다. */
@Composable
private fun ReviewSheet(
    state: InterviewUiState,
    micGranted: Boolean,
    cloudRecognition: Boolean,
    viewModel: InterviewViewModel,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable(state.turnId) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val empty = state.transcript.isBlank()
    val canEdit = (editing || !micGranted) && state.retryAction == null

    LaunchedEffect(canEdit) {
        if (canEdit) focus.requestFocus()
    }

    Column(
        modifier = modifier
            .background(CallColors.Sheet, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Text(
            stringResource(if (empty && !canEdit) R.string.review_empty_title else R.string.review_title),
            style = MaterialTheme.typography.labelMedium,
            color = CallColors.Muted,
        )
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = state.transcript,
            onValueChange = viewModel::onTranscriptChange,
            enabled = canEdit,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = CallColors.Text),
            cursorBrush = SolidColor(CallColors.Text),
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).focusRequester(focus),
        )
        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(errorText(error), style = MaterialTheme.typography.bodyMedium, color = CallColors.Partial)
        }
        if (!micGranted) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.review_mic_denied), style = MaterialTheme.typography.bodySmall, color = CallColors.Muted)
        } else if (cloudRecognition) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.review_cloud_stt_notice), style = MaterialTheme.typography.bodySmall, color = CallColors.Dim)
        }
        Spacer(Modifier.height(18.dp))
        when {
            state.retryAction != null -> PillButton(stringResource(R.string.control_retry), viewModel::retry)
            empty && micGranted && !canEdit -> PillButton(stringResource(R.string.review_speak_again), { viewModel.listen() })
            else -> PillButton(stringResource(R.string.review_send), viewModel::submit, enabled = !empty)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.retryAction == null) {
                if (micGranted && !empty) {
                    SheetAction(stringResource(R.string.review_speak_more), CallColors.Text) {
                        editing = false
                        viewModel.listen(append = true)
                    }
                }
                if (!canEdit) {
                    SheetAction(
                        stringResource(if (empty) R.string.review_type else R.string.review_edit),
                        CallColors.Text,
                    ) { editing = true }
                }
                SheetAction(stringResource(R.string.control_replay), CallColors.Text, viewModel::replay)
            }
            SheetAction(stringResource(R.string.review_end), CallColors.End, onEnd)
        }
    }
}

@Composable
private fun SheetAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
internal fun verdictColor(verdict: String): Color = when (verdict) {
    "CORRECT" -> CallColors.Speaking
    "PARTIAL" -> CallColors.Partial
    "WRONG" -> CallColors.End
    else -> CallColors.Muted
}

@Composable
private fun errorText(error: InterviewError): String = when (error) {
    InterviewError.Network -> stringResource(R.string.error_network)
    InterviewError.Unauthorized -> stringResource(R.string.error_unauthorized)
    is InterviewError.Server -> error.message
    InterviewError.SpeechNotUnderstood -> stringResource(R.string.error_speech)
    InterviewError.SpeechUnavailable -> stringResource(R.string.error_speech_unavailable)
    InterviewError.TtsFailed -> stringResource(R.string.error_tts)
}

private fun statusLabel(phase: InterviewPhase): Int = when (phase) {
    InterviewPhase.IDLE, InterviewPhase.CONNECTING -> R.string.call_connecting
    InterviewPhase.SPEAKING, InterviewPhase.FEEDBACK -> R.string.call_speaking
    InterviewPhase.LISTENING -> R.string.call_listening
    InterviewPhase.REVIEW -> R.string.call_review
    InterviewPhase.GRADING -> R.string.call_grading
    InterviewPhase.ENDING, InterviewPhase.FINISHED -> R.string.call_ending
}

internal fun verdictLabel(verdict: String): Int = when (verdict) {
    "CORRECT" -> R.string.verdict_correct
    "PARTIAL" -> R.string.verdict_partial
    "WRONG" -> R.string.verdict_wrong
    else -> R.string.verdict_undetermined
}
