package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.CallRecord
import com.example.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Dismiss the reminder notification ASAP
        notificationManager.cancel(ReminderScheduler.NOTIFICATION_ID)

        val db = AppDatabase.getDatabase(context)
        val repository = ReminderRepository(db.reminderDao())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = repository.getConfig() ?: return@launch

                when (action) {
                    "com.example.ACTION_CALL_NOW" -> {
                        val phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: config.phoneNumber
                        if (phoneNumber.isNotEmpty()) {
                            // Open dialer safely (ACTION_DIAL does not call automatically, requires user approval)
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(dialIntent)
                            
                            // Schedule a verification check in 90 seconds
                            ReminderScheduler.scheduleCallLogCheck(context)
                        } else {
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(context, "لم يتم العثور على رقم هاتف متاح!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    "com.example.ACTION_SNOOZE" -> {
                        val snoozeMinutes = config.snoozeMinutes
                        val nextTrigger = ReminderScheduler.scheduleSnoozeAlarm(context, snoozeMinutes)
                        repository.saveConfig(config.copy(nextReminderTimestamp = nextTrigger))
                        
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "تم تأجيل التذكير لمدّة $snoozeMinutes دقيقة", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "com.example.ACTION_CONFIRM_CALLED" -> {
                        val now = System.currentTimeMillis()
                        // 1. Log manual call success
                        repository.addCallRecord(
                            CallRecord(
                                timestamp = now,
                                durationSeconds = 0, // Manual entries have 0 duration
                                isManual = true,
                                callerName = config.contactName
                            )
                        )
                        
                        // 2. Reschedule standard reminder
                        val updatedConfig = config.copy(
                            lastCallTimestamp = now
                        )
                        repository.saveConfig(updatedConfig)
                        val nextTrigger = ReminderScheduler.scheduleNextAlarm(context, updatedConfig)
                        repository.saveConfig(updatedConfig.copy(nextReminderTimestamp = nextTrigger))

                        // 3. Inform user using Toast
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "تمام، تم تسجيل الاتصال ❤️", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
