package io.github.gumwoo.aiinterview.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import io.github.gumwoo.aiinterview.CallActivity
import io.github.gumwoo.aiinterview.MainActivity
import io.github.gumwoo.aiinterview.R

/**
 * 수신 알림을 띄운다. 벨소리는 알림 채널 소리로 낸다. 앱이 백그라운드여도 시스템이 재생하므로
 * Android 17의 백그라운드 오디오 제한을 받지 않고, 알람 용도라 방해금지에서도 알람 허용을 따른다.
 */
class IncomingCallNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    fun ring(scheduledDate: String?) {
        createChannels()
        val notification = Notification.Builder(appContext, CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(appContext.getString(R.string.ring_title))
            .setContentText(appContext.getString(R.string.ring_text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setTimeoutAfter(AlarmScheduler.RING_TIMEOUT_MILLIS)
            .setContentIntent(activityIntent(REQUEST_SHOW, MainActivity.ACTION_RING, scheduledDate))
            .setFullScreenIntent(activityIntent(REQUEST_FULL_SCREEN, MainActivity.ACTION_RING, scheduledDate), true)
            .addAction(action(R.string.incoming_answer, activityIntent(REQUEST_ANSWER, MainActivity.ACTION_ANSWER, scheduledDate)))
            .addAction(action(R.string.ring_snooze_short, receiverIntent(REQUEST_SNOOZE, AlarmReceiver.ACTION_SNOOZE, scheduledDate)))
            .addAction(action(R.string.incoming_decline, receiverIntent(REQUEST_DECLINE, AlarmReceiver.ACTION_DECLINE, scheduledDate)))
            .build()
        // 받거나 끝날 때까지 벨소리를 되풀이한다.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(ID_RING, notification)
    }

    fun stopRinging() {
        manager.cancel(ID_RING)
    }

    fun showMissed() {
        createChannels()
        val open = PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(appContext, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(appContext.getString(R.string.missed_title))
            .setContentText(appContext.getString(R.string.missed_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(ID_MISSED, notification)
    }

    private fun createChannels() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val ring = NotificationChannel(
            CHANNEL_RING,
            appContext.getString(R.string.ring_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), attributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val missed = NotificationChannel(
            CHANNEL_MISSED,
            appContext.getString(R.string.missed_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { setSound(null, null) }
        manager.createNotificationChannels(listOf(ring, missed))
    }

    private fun action(label: Int, intent: PendingIntent) = Notification.Action.Builder(
        Icon.createWithResource(appContext, R.drawable.ic_call),
        appContext.getString(label),
        intent,
    ).build()

    private fun activityIntent(requestCode: Int, action: String, scheduledDate: String?) = PendingIntent.getActivity(
        appContext,
        requestCode,
        Intent(appContext, CallActivity::class.java)
            .setAction(action)
            .putExtra(AlarmScheduler.EXTRA_SCHEDULED_DATE, scheduledDate),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun receiverIntent(requestCode: Int, action: String, scheduledDate: String?) = PendingIntent.getBroadcast(
        appContext,
        requestCode,
        Intent(appContext, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmScheduler.EXTRA_SCHEDULED_DATE, scheduledDate),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL_RING = "incoming_call"
        const val CHANNEL_MISSED = "missed_call"
        const val ID_RING = 10
        const val ID_MISSED = 11
        const val REQUEST_SHOW = 10
        const val REQUEST_FULL_SCREEN = 11
        const val REQUEST_ANSWER = 12
        const val REQUEST_SNOOZE = 13
        const val REQUEST_DECLINE = 14
        const val REQUEST_OPEN = 15
    }
}
