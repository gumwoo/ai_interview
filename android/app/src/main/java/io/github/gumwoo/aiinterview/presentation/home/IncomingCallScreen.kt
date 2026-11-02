package io.github.gumwoo.aiinterview.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.gumwoo.aiinterview.R
import io.github.gumwoo.aiinterview.data.InterviewSettings
import io.github.gumwoo.aiinterview.presentation.settings.topicLabel
import io.github.gumwoo.aiinterview.presentation.theme.CallColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun IncomingCallScreen(settings: InterviewSettings, onAnswer: () -> Unit, onDecline: () -> Unit) {
    BackHandler(onBack = onDecline)
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(96.dp))
            Text(
                stringResource(R.string.incoming_label),
                style = MaterialTheme.typography.labelMedium,
                color = CallColors.Muted,
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            Text(topicsLine(settings), style = MaterialTheme.typography.bodyMedium, color = CallColors.Muted)
            Spacer(Modifier.weight(1f))
            AnswerSlider(onAnswer, onDecline)
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun topicsLine(settings: InterviewSettings): String {
    val first = settings.topics.take(2).map { stringResource(topicLabel(it)) }.joinToString(", ")
    val rest = settings.topics.size - 2
    return if (rest > 0) {
        stringResource(R.string.incoming_topics_more, first, rest, settings.durationMinutes)
    } else {
        stringResource(R.string.incoming_topics, first, settings.durationMinutes)
    }
}

/** 전화기 버튼을 오른쪽으로 밀면 받고 왼쪽으로 밀면 거절한다. 양쪽 글자를 눌러도 된다. */
@Composable
private fun AnswerSlider(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val handleWidth = 92.dp
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(CallColors.Control),
    ) {
        val limit = with(LocalDensity.current) { ((maxWidth - handleWidth) / 2 - 6.dp).toPx() }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SliderLabel(stringResource(R.string.incoming_decline), onDecline)
            SliderLabel(stringResource(R.string.incoming_answer), onAnswer)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(width = handleWidth, height = 64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(CallColors.Text)
                .pointerInput(limit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val position = offset.value
                            scope.launch {
                                when {
                                    abs(position) < limit * SWIPE_THRESHOLD -> offset.animateTo(0f)
                                    position > 0 -> onAnswer()
                                    else -> onDecline()
                                }
                            }
                        },
                        onHorizontalDrag = { _, amount ->
                            scope.launch { offset.snapTo((offset.value + amount).coerceIn(-limit, limit)) }
                        },
                    )
                },
        ) {
            Icon(
                painterResource(R.drawable.ic_call),
                contentDescription = null,
                tint = CallColors.Answer,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun SliderLabel(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = CallColors.Text)
    }
}

private const val SWIPE_THRESHOLD = 0.7f
