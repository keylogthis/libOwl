package org.thebottle.libOwl.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.thebottle.libOwl.BookApp
import org.thebottle.libOwl.MainActivity
import org.thebottle.libOwl.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Schedules the daily "books due" check and shows the notification. */
object LoanReminders {
    private const val WORK = "loan_reminders"
    private const val CHANNEL = "loans"
    private const val NOTIFICATION_ID = 1001

    /** Turns the daily check (about 9 am) on or off. Safe to call repeatedly. */
    fun sync(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.with(LocalTime.of(9, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = ChronoUnit.MILLIS.between(now, next)
        val request = PeriodicWorkRequestBuilder<LoanReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Runs the check once, right now (used by the "Check now" setting). */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<LoanReminderWorker>().build())
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    internal fun notify(context: Context, lines: List<String>) {
        if (lines.isEmpty() || !canNotify(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Loan reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_LOANS, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (lines.size == 1) "Loaned book needs attention" else "${lines.size} loaned books need attention"
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach { s.addLine(it) } }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
        }
    }
}

class LoanReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BookApp
        val prefs = app.settings.settings.first().loans
        if (!prefs.remindersEnabled) return Result.success()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val overdue = mutableListOf<Pair<Long, String>>()
        val soon = mutableListOf<Pair<Long, String>>()
        for (loan in app.database.loanDao().activeList()) {
            val due = loan.dueDate ?: continue
            val title = app.database.bookDao().get(loan.bookId)?.title ?: continue
            val days = ChronoUnit.DAYS.between(today, java.time.Instant.ofEpochMilli(due).atZone(zone).toLocalDate())
            when {
                days < 0 -> overdue += due to "Overdue: \"$title\" – ${loan.borrower} (${-days} day${if (-days == 1L) "" else "s"})"
                days <= prefs.remindDaysBefore -> soon += due to
                    "Due ${if (days == 0L) "today" else if (days == 1L) "tomorrow" else "in $days days"}: \"$title\" – ${loan.borrower}"
            }
        }
        val lines = overdue.sortedBy { it.first }.map { it.second } + soon.sortedBy { it.first }.map { it.second }
        LoanReminders.notify(applicationContext, lines)
        return Result.success()
    }
}
