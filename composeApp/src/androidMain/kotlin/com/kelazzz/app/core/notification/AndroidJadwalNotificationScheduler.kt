package com.kelazzz.app.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kelazzz.app.domain.model.Jadwal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AndroidJadwalNotificationScheduler(
    private val context: Context
) : JadwalNotificationScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override suspend fun schedule(jadwal: Jadwal) {
        val offsetMinutes = jadwal.reminderOffsetMinutes ?: run {
            cancel(jadwal.id)
            return
        }

        val scheduleAtMillis = parseScheduleMillis(jadwal) ?: run {
            cancel(jadwal.id)
            return
        }
        val triggerAtMillis = scheduleAtMillis - offsetMinutes * 60_000L

        if (triggerAtMillis <= System.currentTimeMillis()) {
            cancel(jadwal.id)
            return
        }

        val pendingIntent = createPendingIntent(jadwal, PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    override suspend fun cancel(jadwalId: Long) {
        val intent = Intent(context, JadwalNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            jadwalId.toNotificationRequestCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun createPendingIntent(jadwal: Jadwal, flags: Int): PendingIntent {
        val intent = Intent(context, JadwalNotificationReceiver::class.java).apply {
            putExtra(JadwalNotificationReceiver.EXTRA_JADWAL_ID, jadwal.id)
            putExtra(JadwalNotificationReceiver.EXTRA_TITLE, jadwal.judul)
            putExtra(JadwalNotificationReceiver.EXTRA_DESCRIPTION, jadwal.deskripsi)
            putExtra(JadwalNotificationReceiver.EXTRA_DATE, jadwal.tanggal)
            putExtra(JadwalNotificationReceiver.EXTRA_TIME, jadwal.waktu)
            putExtra(JadwalNotificationReceiver.EXTRA_KIND, jadwal.jenis.displayName)
            putExtra(JadwalNotificationReceiver.EXTRA_OFFSET_MINUTES, jadwal.reminderOffsetMinutes ?: 0L)
        }

        return PendingIntent.getBroadcast(
            context,
            jadwal.id.toNotificationRequestCode(),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun parseScheduleMillis(jadwal: Jadwal): Long? {
        val date = runCatching { LocalDate.parse(jadwal.tanggal.trim()) }.getOrNull()
            ?: return null
        val startTimeText = jadwal.waktu.substringBefore("-").trim()
        val time = runCatching { LocalTime.parse(startTimeText) }.getOrNull()
            ?: return null
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}

private fun Long.toNotificationRequestCode(): Int {
    return (this and 0x7fffffff).toInt()
}
