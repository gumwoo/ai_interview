package io.github.gumwoo.aiinterview.presentation.theme

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.gumwoo.aiinterview.R

private val BETWEEN_LETTERS = Regex("(?<=\\S)(?=\\S)")

/** 한국어가 어절 중간에서 줄바꿈되지 않게 글자 사이에 줄바꿈 금지 문자를 넣는다. */
fun String.keepWords(): String = replace(BETWEEN_LETTERS, "⁠")

/** 이 폭부터 폴드를 펼친 화면으로 보고 2단으로 나눈다. */
private val WideLayoutMinWidth = 600.dp

@Composable
fun AdaptiveLayout(content: @Composable (wide: Boolean) -> Unit) {
    BoxWithConstraints {
        content(maxWidth >= WideLayoutMinWidth)
    }
}

/** 한 화면에 하나만 두는 주요 동작. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (enabled) CallColors.Text else CallColors.Control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) CallColors.Background else CallColors.Dim,
        )
    }
}

/** 아이콘과 이름이 있는 통화 버튼. */
@Composable
fun CallControl(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(if (active) CallColors.Text else CallColors.Control, CircleShape),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (active) CallColors.Background else CallColors.Text,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = CallColors.Muted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun EndCallButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val label = stringResource(R.string.control_end)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(108.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(CallColors.End)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Icon(
            painterResource(R.drawable.ic_call_end),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** 소리가 오가는 중임을 나타내는 막대. */
@Composable
fun VoiceBars(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "voice")
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(6.dp, 14.dp, 9.dp, 16.dp, 7.dp).forEachIndexed { index, height ->
            val scale by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 520 + index * 70, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
            Box(
                Modifier
                    .width(2.dp)
                    .height(height)
                    .graphicsLayer { scaleY = scale }
                    .background(color, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

/** 작은 점을 붙인 목록. */
@Composable
fun PointList(points: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach { point ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(4.dp)
                        .background(CallColors.Dim, CircleShape),
                )
                Text(point.keepWords(), style = MaterialTheme.typography.bodyMedium, color = CallColors.Text)
            }
        }
    }
}
