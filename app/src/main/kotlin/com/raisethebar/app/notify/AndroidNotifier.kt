package com.raisethebar.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raisethebar.app.MainActivity
import com.raisethebar.app.R
import com.raisethebar.app.data.Notifier
import com.raisethebar.engine.Checkin
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Check-in notifications and the rest-timer buzz, with AlarmManager. Phase 1 schedules nothing else. */
class AndroidNotifier(
    private val context: Context,
) : Notifier {
    private val alarms: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun scheduleCheckins(checkins: List<Checkin>, previous: List<LocalDate>) {
        (previous + checkins.map { it.date }).distinct().forEach { alarms.cancel(checkinIntent(it)) }
        checkins.forEach { c ->
            val at = c.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, checkinIntent(c.date))
        }
    }

    override fun cancelCheckin(date: LocalDate) {
        alarms.cancel(checkinIntent(date))
        NotificationManagerCompat.from(context).cancel(notificationId(date))
    }

    override fun scheduleRestEnd(at: OffsetDateTime) {
        val millis = at.toInstant().toEpochMilli()
        val intent = restIntent(millis)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    override fun cancelRestEnd() = alarms.cancel(restIntent(0))

    private fun checkinIntent(date: LocalDate): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            notificationId(date),
            Intent(context, CheckinReceiver::class.java).putExtra(EXTRA_DATE, date.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun restIntent(endsAt: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REST_REQUEST,
            Intent(context, RestReceiver::class.java).putExtra(EXTRA_ENDS_AT, endsAt),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val EXTRA_DATE = "date"
        const val EXTRA_ENDS_AT = "ends_at"
        const val CHANNEL_CHECKINS = "checkins"
        private const val REST_REQUEST = 1

        fun notificationId(date: LocalDate): Int = date.toEpochDay().toInt()

        /** Shows "Training today?" for [date]; tapping it opens the check-in sheet. */
        fun showCheckin(context: Context, date: String) {
            val granted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val manager = NotificationManagerCompat.from(context)
            if (!granted || !manager.areNotificationsEnabled()) return
            val channel = NotificationChannel(CHANNEL_CHECKINS, "Check-ins", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Asks what's going on when a planned workout hasn't started"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val open =
                PendingIntent.getActivity(
                    context,
                    notificationId(LocalDate.parse(date)),
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_CHECKIN_DATE, date)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_CHECKINS)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Training today?")
                    .setContentText("No workout started yet. Tap to tell the app what's going on.")
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            manager.notify(notificationId(LocalDate.parse(date)), notification)
        }
    }
}

class CheckinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra(AndroidNotifier.EXTRA_DATE) ?: return
        AndroidNotifier.showCheckin(context, date)
    }
}

class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Haptics.restOver(context, intent.getLongExtra(AndroidNotifier.EXTRA_ENDS_AT, 0))
    }
}

object Haptics {
    @Volatile private var lastRestEnd = -1L

    /** Buzzes once per rest period, whether the screen or the alarm gets there first. */
    fun restOver(context: Context, endsAt: Long) {
        synchronized(this) {
            if (endsAt != 0L && endsAt == lastRestEnd) return
            lastRestEnd = endsAt
        }
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
        if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1))
    }
}
