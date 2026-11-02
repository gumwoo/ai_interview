package io.github.gumwoo.aiinterview.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.gumwoo.aiinterview.MainActivity
import io.github.gumwoo.aiinterview.data.LocalScheduleRepository
import java.time.LocalDate
import java.time.ZonedDateTime

/** 면접 전화 알람을 예약한다. 다음 일정, 미룬 전화, 부재중 처리 시각을 각각 하나씩만 둔다. */
class AlarmScheduler(context: Context, private val scheduleStore: LocalScheduleRepository) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    /** 저장된 일정으로 다음 전화를 다시 예약한다. 같은 요청 코드라 이전 예약을 덮어쓴다. */
    fun reschedule() {
        val now = ZonedDateTime.now()
        val next = scheduleStore.load().nextOccurrence(now)
        if (next == null) {
            alarmManager.cancel(ringIntent(REQUEST_NEXT, null))
            return
        }
        setRingAt(next.toInstant().toEpochMilli(), ringIntent(REQUEST_NEXT, next.toLocalDate()))
    }

    fun snooze(scheduledDate: LocalDate?) {
        val triggerAt = System.currentTimeMillis() + SNOOZE_MILLIS
        setRingAt(triggerAt, ringIntent(REQUEST_SNOOZE, scheduledDate))
    }

    /** 시험 전화. 하루 일정으로 치지 않도록 날짜 없이 울린다. */
    fun testCall() {
        val triggerAt = System.currentTimeMillis() + TEST_CALL_MILLIS
        setRingAt(triggerAt, ringIntent(REQUEST_SNOOZE, null))
    }

    fun startMissedTimer() {
        val triggerAt = System.currentTimeMillis() + RING_TIMEOUT_MILLIS
        // 일반 알람은 시스템이 크게 늦출 수 있어, 벨소리가 멈춘 뒤 한참 지나 부재중이 뜨지 않게 정확 알람을 쓴다.
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, missedIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, missedIntent())
        }
    }

    fun cancelMissedTimer() {
        alarmManager.cancel(missedIntent())
    }

    /** 시스템이 정확 알람을 막아 두었으면 절전 중에도 울리는 일반 알람으로 예약한다. 조금 늦을 수 있다. */
    private fun setRingAt(triggerAtMillis: Long, operation: PendingIntent) {
        if (alarmManager.canScheduleExactAlarms()) {
            val show = PendingIntent.getActivity(
                appContext,
                REQUEST_SHOW,
                Intent(appContext, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun ringIntent(requestCode: Int, scheduledDate: LocalDate?): PendingIntent {
        val intent = Intent(appContext, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_RING)
            .putExtra(EXTRA_SCHEDULED_DATE, scheduledDate?.toString())
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun missedIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REQUEST_MISSED,
        Intent(appContext, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_MISSED),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val EXTRA_SCHEDULED_DATE = "scheduled_date"
        const val RING_TIMEOUT_MILLIS = 60_000L
        private const val SNOOZE_MILLIS = 5 * 60_000L
        private const val TEST_CALL_MILLIS = 60_000L
        private const val REQUEST_NEXT = 1
        private const val REQUEST_SNOOZE = 2
        private const val REQUEST_MISSED = 3
        private const val REQUEST_SHOW = 4
    }
}
