package io.github.gumwoo.aiinterview.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.gumwoo.aiinterview.InterviewApplication

/** 재부팅, 앱 업데이트, 시간 변경으로 사라지거나 어긋난 예약을 다시 잡는다. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> (context.applicationContext as InterviewApplication).alarmScheduler.reschedule()
        }
    }
}
