package com.example.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.ReminderConfig
import java.util.Calendar

object ReminderScheduler {

    const val NOTIFICATION_ID = 1002
    const val CHANNEL_ID = "call_reminder_channel"
    const val ALARM_REQ_CODE = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Call Reminders"
            val descriptionText = "Notifications for call check-ins"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Schedule next regular alarm based on interval days and last call timestamp
    fun scheduleNextAlarm(context: Context, config: ReminderConfig): Long {
        if (!config.isReminderEnabled || config.phoneNumber.isEmpty()) {
            cancelAlarm(context)
            return 0L
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_SHOW_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val frequencyMillis = config.intervalDays.toLong() * 24 * 60 * 60 * 1000
        var triggerAtMillis = config.lastCallTimestamp + frequencyMillis

        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            // If never called or last call was long ago, schedule starting from today/now
            triggerAtMillis = if (config.lastCallTimestamp == 0L) {
                now + 10 * 1000 // Trigger 10 seconds from now as test/first run or tomorrow. Let's do a short buffer (15 seconds)
            } else {
                var next = triggerAtMillis
                while (next <= now) {
                    next += frequencyMillis
                }
                next
            }
        }

        // Use setAndAllowWhileIdle to be battery-saving but reliable
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        return triggerAtMillis
    }

    // Schedule snooze alarm (e.g. after 30 minutes, 1 hour)
    fun scheduleSnoozeAlarm(context: Context, snoozeMinutes: Int): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_SHOW_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + snoozeMinutes.toLong() * 60 * 1000
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        return triggerAtMillis
    }

    // Schedule delayed check of call log (e.g. 90 seconds after clicking "Call Now")
    fun scheduleCallLogCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_CHECK_CALL_LOG"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1003,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + 90 * 1000 // 90 seconds delay
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    // Cancel any scheduled reminder alarm
    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_SHOW_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    // Build and show the primary reminder notification
    fun showReminderNotification(context: Context, config: ReminderConfig) {
        createNotificationChannel(context)

        // Action 1: Call Now (Opens NotificationActionReceiver)
        val callIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_CALL_NOW"
            putExtra("PHONE_NUMBER", config.phoneNumber)
        }
        val callPendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Snooze (Remember later)
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_SNOOZE"
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: I Called
        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_CONFIRM_CALLED"
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            203,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "اطمن على أختك"
        val body = "عدى وقت كفاية، اتصل بها واطمن عليها ❤️"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call) // Default material phone icon as backup
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    context.packageManager.getLaunchIntentForPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(android.R.drawable.ic_menu_call, "اتصل الآن", callPendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "ذكرني لاحقًا", snoozePendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "أنا اتصلت", donePendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    // Build and show the call success confirmation notification
    fun showCallConfirmedNotification(context: Context, personName: String) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.checkbox_on_background)
            .setContentTitle("تمام ❤️")
            .setContentText("أنت اتصلت بـ $personName النهارده")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1004, builder.build())
    }

    // Build and show the call failure reminder notification (when verification check fails)
    fun showCallFailedNotification(context: Context, personName: String) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("لسه محتاج تطمن عليها")
            .setContentText("هفكرك تاني كمان شوية")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1005, builder.build())
    }
}
