package io.github.gumwoo.aiinterview.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.gumwoo.aiinterview.InterviewApplication
import java.time.LocalDate

/** 알람과 알림 버튼을 처리한다. 받기 전에는 서버와 Claude를 호출하지 않는다. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as InterviewApplication
        val scheduledDate = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_DATE)
        when (intent.action) {
            ACTION_RING -> {
                app.alarmScheduler.reschedule()
                // 이미 통화 중이면 겹쳐 울리지 않는다.
                if (!app.callActive) {
                    app.incomingCallNotifier.ring(scheduledDate)
                    app.alarmScheduler.startMissedTimer()
                }
            }

            ACTION_SNOOZE -> {
                stopRinging(app)
                app.alarmScheduler.snooze(scheduledDate?.let(LocalDate::parse))
            }

            ACTION_DECLINE -> stopRinging(app)

            ACTION_MISSED -> {
                app.incomingCallNotifier.stopRinging()
                app.incomingCallNotifier.showMissed()
            }
        }
    }

    private fun stopRinging(app: InterviewApplication) {
        app.incomingCallNotifier.stopRinging()
        app.alarmScheduler.cancelMissedTimer()
    }

    companion object {
        const val ACTION_RING = "io.github.gumwoo.aiinterview.action.ALARM_RING"
        const val ACTION_SNOOZE = "io.github.gumwoo.aiinterview.action.ALARM_SNOOZE"
        const val ACTION_DECLINE = "io.github.gumwoo.aiinterview.action.ALARM_DECLINE"
        const val ACTION_MISSED = "io.github.gumwoo.aiinterview.action.ALARM_MISSED"
    }
}
