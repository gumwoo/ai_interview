package io.github.gumwoo.aiinterview.presentation.schedule

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.gumwoo.aiinterview.R
import io.github.gumwoo.aiinterview.alarm.AlarmScheduler
import io.github.gumwoo.aiinterview.data.InterviewSchedule
import io.github.gumwoo.aiinterview.data.LocalScheduleRepository
import io.github.gumwoo.aiinterview.presentation.theme.CallColors
import io.github.gumwoo.aiinterview.presentation.theme.PillButton
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val KOREAN_TIME = DateTimeFormatter.ofPattern("h:mm", Locale.KOREAN)
private val KOREAN_NOON = DateTimeFormatter.ofPattern("a", Locale.KOREAN)

/** 요일을 월요일부터 짧게 잇는다. 예: 월·화·수 */
fun InterviewSchedule.daysLabel(): String = days.sorted()
    .joinToString("·") { it.getDisplayName(TextStyle.SHORT, Locale.KOREAN) }

@Composable
fun ScheduleScreen(
    store: LocalScheduleRepository,
    scheduler: AlarmScheduler,
    requestCallPermissions: (() -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val saved = remember { store.load() }
    var enabled by rememberSaveable { mutableStateOf(saved.enabled) }
    var hour by rememberSaveable { mutableIntStateOf(saved.hour) }
    var minute by rememberSaveable { mutableIntStateOf(saved.minute) }
    var days by rememberSaveable { mutableStateOf(saved.days.map { it.name }) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    // 시스템 설정에서 돌아오면 안내를 다시 계산한다.
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeCount++
        onPauseOrDispose { }
    }

    BackHandler(onBack = onBack)

    val time = LocalTime.of(hour, minute)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleMedium)
                TextAction(stringResource(R.string.settings_close), onClick = onBack)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.schedule_enable), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CallColors.Background,
                            checkedTrackColor = CallColors.Text,
                            uncheckedThumbColor = CallColors.Muted,
                            uncheckedTrackColor = CallColors.Control,
                            uncheckedBorderColor = CallColors.Control,
                        ),
                    )
                }
                Spacer(Modifier.height(36.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { pickingTime = true }
                        .padding(vertical = 8.dp),
                ) {
                    Text(time.format(KOREAN_NOON), style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
                    Text(time.format(KOREAN_TIME), style = MaterialTheme.typography.displayLarge)
                    Text(stringResource(R.string.schedule_time_hint), style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
                }
                Spacer(Modifier.height(32.dp))
                Text(stringResource(R.string.schedule_days), style = MaterialTheme.typography.labelMedium, color = CallColors.Muted)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DayOfWeek.entries.forEach { day ->
                        DayButton(
                            label = day.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                            selected = day.name in days,
                            onClick = { days = if (day.name in days) days - day.name else days + day.name },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                PermissionNotices(resumeCount)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.schedule_test), style = MaterialTheme.typography.bodyLarge)
                    TextAction(stringResource(R.string.schedule_test_action)) {
                        requestCallPermissions {
                            scheduler.testCall()
                            message = R.string.schedule_test_set
                        }
                    }
                }
                message?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = CallColors.Muted)
                }
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            PillButton(
                text = stringResource(R.string.settings_save),
                enabled = !enabled || days.isNotEmpty(),
                onClick = {
                    store.save(InterviewSchedule(enabled, hour, minute, days.map(DayOfWeek::valueOf).toSet()))
                    val apply = {
                        scheduler.reschedule()
                        message = R.string.settings_saved
                    }
                    if (enabled) requestCallPermissions(apply) else apply()
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickingTime) {
        TimeDialog(
            hour = hour,
            minute = minute,
            onDismiss = { pickingTime = false },
            onConfirm = { newHour, newMinute ->
                hour = newHour
                minute = newMinute
                pickingTime = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CallColors.Sheet,
        shape = RoundedCornerShape(24.dp),
        text = { TimeInput(state) },
        confirmButton = {
            TextAction(stringResource(R.string.schedule_time_confirm)) { onConfirm(state.hour, state.minute) }
        },
        dismissButton = { TextAction(stringResource(R.string.end_dialog_cancel), onClick = onDismiss) },
    )
}

@Composable
private fun DayButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (selected) CallColors.Text else CallColors.Control)
            .clickable(role = Role.Checkbox, onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) CallColors.Background else CallColors.Text,
            textAlign = TextAlign.Center,
        )
    }
}

/** 막혀 있는 설정만 안내한다. */
@Composable
private fun PermissionNotices(@Suppress("UNUSED_PARAMETER") refreshKey: Int) {
    val context = LocalContext.current
    val notifications = context.getSystemService(NotificationManager::class.java)
    val power = context.getSystemService(PowerManager::class.java)
    if (!notifications.areNotificationsEnabled()) {
        Notice(R.string.notice_notifications_title, R.string.notice_notifications_text) {
            context.openSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    if (!notifications.canUseFullScreenIntent()) {
        Notice(R.string.notice_full_screen_title, R.string.notice_full_screen_text) {
            context.openSettings(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()),
            )
        }
    }
    if (!power.isIgnoringBatteryOptimizations(context.packageName)) {
        Notice(R.string.notice_battery_title, R.string.notice_battery_text) {
            context.openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

private fun Context.openSettings(intent: Intent) {
    startActivity(intent)
}

@Composable
private fun Notice(title: Int, text: Int, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(CallColors.Sheet, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.labelMedium, color = CallColors.Partial)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = CallColors.Muted)
        TextAction(stringResource(R.string.notice_open), onClick = onOpen)
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = CallColors.Text,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
    )
}
