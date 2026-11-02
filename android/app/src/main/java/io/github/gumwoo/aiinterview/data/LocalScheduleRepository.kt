package io.github.gumwoo.aiinterview.data

import android.content.Context
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/** 면접 전화 일정. PC가 꺼져 있어도 울려야 하므로 원본은 앱에 둔다(계획서 4.1). */
data class InterviewSchedule(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 30,
    val days: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ),
) {
    /** [now] 이후 처음 울릴 시각. 꺼져 있거나 요일이 없으면 null이다. */
    fun nextOccurrence(now: ZonedDateTime): ZonedDateTime? {
        if (!enabled || days.isEmpty()) return null
        val time = LocalTime.of(hour, minute)
        return (0L..DAYS_TO_SEARCH)
            .asSequence()
            .map { now.toLocalDate().plusDays(it) }
            .filter { it.dayOfWeek in days }
            .map { it.atTime(time).atZone(now.zone) }
            .firstOrNull { it.isAfter(now) }
    }

    private companion object {
        /** 같은 요일의 오늘 시각이 지났으면 7일 뒤가 다음 차례다. */
        const val DAYS_TO_SEARCH = 7L
    }
}

class LocalScheduleRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): InterviewSchedule {
        val defaults = InterviewSchedule()
        return InterviewSchedule(
            enabled = preferences.getBoolean(KEY_ENABLED, defaults.enabled),
            hour = preferences.getInt(KEY_HOUR, defaults.hour),
            minute = preferences.getInt(KEY_MINUTE, defaults.minute),
            days = preferences.getStringSet(KEY_DAYS, null)?.map(DayOfWeek::valueOf)?.toSet() ?: defaults.days,
        )
    }

    fun save(schedule: InterviewSchedule) {
        preferences.edit {
            putBoolean(KEY_ENABLED, schedule.enabled)
            putInt(KEY_HOUR, schedule.hour)
            putInt(KEY_MINUTE, schedule.minute)
            putStringSet(KEY_DAYS, schedule.days.map { it.name }.toSet())
        }
    }

    private companion object {
        const val PREFERENCES = "interview_schedule"
        const val KEY_ENABLED = "enabled"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val KEY_DAYS = "days"
    }
}
