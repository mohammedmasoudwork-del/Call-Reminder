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
import com.example.data.Person

object ReminderScheduler {

    const val CHANNEL_ID = "multi_person_reminder_channel"
    const val CHECK_CALL_LOG_OFFSET = 1000000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "تذكير صلة الرحم والتواصل"
            val descriptionText = "إشعارات للتذكير بالاطمئنان على الأهل والأصدقاء"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Schedule next regular alarm for a specific person
    fun scheduleNextAlarm(context: Context, person: Person): Long {
        if (!person.isReminderEnabled || person.phoneNumber.isEmpty()) {
            cancelAlarm(context, person)
            return 0L
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_SHOW_REMINDER"
            putExtra("PERSON_ID", person.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            person.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val frequencyMillis = person.intervalDays.toLong() * 24 * 60 * 60 * 1000
        var triggerAtMillis = person.lastContactTimestamp + frequencyMillis

        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            // Setup first trigger with a short delay if never called, otherwise keep standard cycle
            triggerAtMillis = if (person.lastContactTimestamp == 0L) {
                now + 20 * 1000 // Trigger in 20 seconds as a smooth initial setup / testing
            } else {
                var next = triggerAtMillis
                while (next <= now) {
                    next += frequencyMillis
                }
                next
            }
        }

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return triggerAtMillis
    }

    // Schedule snooze alarm for a specific person
    fun scheduleSnoozeAlarm(context: Context, person: Person): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_SHOW_REMINDER"
            putExtra("PERSON_ID", person.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            person.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + person.snoozeMinutes.toLong() * 60 * 1000
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return triggerAtMillis
    }

    // Schedule delayed call log check for a specific person (e.g. 90 seconds after "Call Now")
    fun scheduleCallLogCheck(context: Context, person: Person) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_CHECK_CALL_LOG"
            putExtra("PERSON_ID", person.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            person.id + CHECK_CALL_LOG_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + 90 * 1000 // 90 seconds check window
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Cancel dynamic alarm for a person
    fun cancelAlarm(context: Context, person: Person) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_SHOW_REMINDER"
            putExtra("PERSON_ID", person.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            person.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        // Cancel call log checker as well
        val logIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_CHECK_CALL_LOG"
            putExtra("PERSON_ID", person.id)
        }
        val logPendingIntent = PendingIntent.getBroadcast(
            context,
            person.id + CHECK_CALL_LOG_OFFSET,
            logIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (logPendingIntent != null) {
            alarmManager.cancel(logPendingIntent)
            logPendingIntent.cancel()
        }
    }

    // Create and trigger the specific notification
    fun showReminderNotification(context: Context, person: Person) {
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Setup common Actions
        // Snooze Action
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_SNOOZE"
            putExtra("PERSON_ID", person.id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            person.id * 10 + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Manual Done Confirm action
        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.ACTION_CONFIRM_CALLED"
            putExtra("PERSON_ID", person.id)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            person.id * 10 + 2,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Content activity back intent
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra("PERSON_ID", person.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            person.id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentPendingIntent)

        // Switch headers & customized buttons based on contact preference
        val relationName = person.name
        val contactType = person.preferredCommType

        if (contactType == "WHATSAPP") {
            builder.setContentTitle("حان وقت التواصل مع $relationName")
            builder.setContentText("أرسل رسالة واتساب للاطمئنان عليها اليوم ❤️")

            val whatsappIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "com.example.ACTION_SEND_WHATSAPP"
                putExtra("PERSON_ID", person.id)
            }
            val whatsappPendingIntent = PendingIntent.getBroadcast(
                context,
                person.id * 10 + 3,
                whatsappIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.ic_menu_send, "إرسال واتساب", whatsappPendingIntent)
            builder.addAction(android.R.drawable.checkbox_on_background, "أنا تواصلت", donePendingIntent)
            builder.addAction(android.R.drawable.ic_menu_recent_history, "ذكرني لاحقًا", snoozePendingIntent)

        } else if (contactType == "CALL") {
            builder.setContentTitle("اطمن على $relationName")
            builder.setContentText("عدى وقت كفاية، اتصل بها للاطمئنان عليها ❤️")

            val callIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "com.example.ACTION_CALL_NOW"
                putExtra("PERSON_ID", person.id)
            }
            val callPendingIntent = PendingIntent.getBroadcast(
                context,
                person.id * 10 + 4,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.ic_menu_call, "اتصل الآن", callPendingIntent)
            builder.addAction(android.R.drawable.checkbox_on_background, "أنا تواصلت", donePendingIntent)
            builder.addAction(android.R.drawable.ic_menu_recent_history, "ذكرني لاحقًا", snoozePendingIntent)

        } else { // BOTH
            builder.setContentTitle("تواصل واطمن على $relationName")
            builder.setContentText("اتصل أو أرسل رسالة واتساب لصلة رحمك اليوم ❤️")

            val callIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "com.example.ACTION_CALL_NOW"
                putExtra("PERSON_ID", person.id)
            }
            val callPendingIntent = PendingIntent.getBroadcast(
                context,
                person.id * 10 + 4,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val whatsappIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "com.example.ACTION_SEND_WHATSAPP"
                putExtra("PERSON_ID", person.id)
            }
            val whatsappPendingIntent = PendingIntent.getBroadcast(
                context,
                person.id * 10 + 3,
                whatsappIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.ic_menu_call, "اتصل الآن", callPendingIntent)
            builder.addAction(android.R.drawable.ic_menu_send, "إرسال واتساب", whatsappPendingIntent)
            builder.addAction(android.R.drawable.checkbox_on_background, "أنا تواصلت", donePendingIntent)
        }

        notificationManager.notify(person.id, builder.build())
    }

    // Notification helper on success verification
    fun showCallConfirmedNotification(context: Context, personName: String, personId: Int) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.checkbox_on_background)
            .setContentTitle("تمام ❤️")
            .setContentText("تم التحقق بنجاح من اتصالك بـ $personName اليوم")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(personId + 500000, builder.build())
    }

    // Notification helper on failed automatic checks
    fun showCallFailedNotification(context: Context, personName: String, personId: Int) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("لم نسجل مكالمة ناجحة مع $personName")
            .setContentText("سنقوم بتذكيرك مرة أخرى لاحقًا")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(personId + 600000, builder.build())
    }
}
